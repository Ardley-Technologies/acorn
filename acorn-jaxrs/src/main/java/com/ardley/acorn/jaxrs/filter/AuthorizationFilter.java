package com.ardley.acorn.jaxrs.filter;

import com.ardley.acorn.action.Action;
import com.ardley.acorn.action.ActionRegistry;
import com.ardley.acorn.attribute.Attributes;
import com.ardley.acorn.attribute.Principal;
import com.ardley.acorn.attribute.PrincipalExtractor;
import com.ardley.acorn.context.RequestContext;
import com.ardley.acorn.evaluator.AuthorizationResult;
import com.ardley.acorn.evaluator.Evaluator;
import com.ardley.acorn.annotation.Authorized;
import com.ardley.acorn.annotation.RequiresActions;
import com.ardley.acorn.jaxrs.exception.AuthenticationRequiredException;
import com.ardley.acorn.jaxrs.exception.AuthorizationDeniedException;
import com.ardley.acorn.jaxrs.exception.ResourceNotFoundException;
import com.ardley.acorn.permission.PermissionSet;
import com.ardley.acorn.policy.EvaluationPolicy;
import com.ardley.acorn.resource.ResourceExtractor;
import com.ardley.acorn.store.PermissionStore;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Parameter;
import java.util.Optional;

/**
 * JAX-RS request filter that enforces Acorn authorization on annotated resource methods.
 *
 * <p>Processes two annotation types:
 * <ul>
 *   <li>{@link RequiresActions} on methods — gate check (no resource context)</li>
 *   <li>{@link Authorized} on parameters — resource-level check with scope evaluation</li>
 * </ul>
 *
 * <p>This filter delegates to:
 * <ul>
 *   <li>{@link PrincipalExtractor} — resolves the authenticated principal from the request</li>
 *   <li>{@link ResourceExtractor} — resolves the resource ID and loads the resource</li>
 *   <li>{@link Evaluator} — makes the authorization decision</li>
 * </ul>
 *
 * <p>Throws typed exceptions rather than constructing responses. Applications must register
 * exception mappers for {@link AuthenticationRequiredException},
 * {@link AuthorizationDeniedException}, and {@link ResourceNotFoundException}.
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class AuthorizationFilter implements ContainerRequestFilter {

    private static final Logger log = LogManager.getLogger(AuthorizationFilter.class);

    /** Request property key where the resolved principal is stored for downstream access. */
    public static final String PRINCIPAL_PROPERTY = "acorn.principal";

    /** Prefix for request properties storing authorized resource instances. */
    public static final String RESOURCE_PROPERTY_PREFIX = "acorn.resource.";

    private final PrincipalExtractor principalExtractor;
    private final PermissionStore permissionStore;
    private final EvaluationPolicy policy;
    private final ExtractorResolver extractorResolver;
    private final ActionRegistry actionRegistry;

    @Context
    private ResourceInfo resourceInfo;

    @Inject
    public AuthorizationFilter(
            PrincipalExtractor principalExtractor,
            PermissionStore permissionStore,
            EvaluationPolicy policy,
            ExtractorResolver extractorResolver,
            ActionRegistry actionRegistry) {
        this.principalExtractor = principalExtractor;
        this.permissionStore = permissionStore;
        this.policy = policy;
        this.extractorResolver = extractorResolver;
        this.actionRegistry = actionRegistry;
    }

    @Override
    public void filter(ContainerRequestContext ctx) {
        if (resourceInfo == null || resourceInfo.getResourceMethod() == null) {
            return;
        }

        RequestContext requestContext = new JaxRsRequestContext(ctx);

        // Resolve principal via extractor
        Principal principal = principalExtractor.extract(requestContext)
                .orElseThrow(AuthenticationRequiredException::new);

        // Store for downstream handler access
        ctx.setProperty(PRINCIPAL_PROPERTY, principal);

        // Load permissions
        Optional<PermissionSet> permsOpt = permissionStore.getPermissionSet(principal.permissionKey());
        if (permsOpt.isEmpty()) {
            log.warn("No permissions found for key: {}", principal.permissionKey());
            throw AuthorizationDeniedException.noPermissions(
                    "No permissions configured for this role");
        }

        PermissionSet permissions = permsOpt.get();

        // Gate check: @RequiresActions on method
        RequiresActions gate = resourceInfo.getResourceMethod().getAnnotation(RequiresActions.class);
        if (gate != null) {
            for (String actionName : gate.value()) {
                Action action = actionRegistry.resolve(actionName);
                AuthorizationResult result = Evaluator.canPerformAction(permissions, action);
                if (result.isDenied()) {
                    log.info("Action \"{}\" denied: {}", actionName, result.reason());
                    throw AuthorizationDeniedException.gateCheck(actionName, result.reason());
                }
            }
        }

        // Resource-level checks: @Authorized on parameters
        Parameter[] parameters = resourceInfo.getResourceMethod().getParameters();
        for (Parameter param : parameters) {
            Authorized authorized = param.getAnnotation(Authorized.class);
            if (authorized == null) continue;

            ResourceExtractor<?> extractor = extractorResolver.resolve(authorized.extractor());

            String resourceId = extractor.extractId(requestContext)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            extractor.resourceType(), "(missing)"));

            Object resource = extractor.load(resourceId, principal);
            if (resource == null) {
                throw new ResourceNotFoundException(extractor.resourceType(), resourceId);
            }

            @SuppressWarnings("unchecked")
            Attributes resourceAttrs = ((ResourceExtractor<Object>) extractor).attributes(resource);

            for (String actionName : authorized.actions()) {
                Action action = actionRegistry.resolve(actionName);
                AuthorizationResult result = Evaluator.evaluate(
                        permissions, principal, resourceAttrs, policy, action);
                if (result.isDenied()) {
                    log.info("Access to {} \"{}\" with action \"{}\" denied: {}",
                            extractor.resourceType(), resourceId, actionName, result.reason());
                    throw AuthorizationDeniedException.resourceCheck(
                            actionName, extractor.resourceType(), resourceId, result.reason());
                }
            }

            ctx.setProperty(RESOURCE_PROPERTY_PREFIX + extractor.resourceType(), resource);
        }
    }
}
