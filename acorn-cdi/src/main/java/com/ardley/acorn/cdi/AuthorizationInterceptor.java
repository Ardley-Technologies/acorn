package com.ardley.acorn.cdi;

import com.ardley.acorn.action.Action;
import com.ardley.acorn.action.ActionRegistry;
import com.ardley.acorn.annotation.Authorized;
import com.ardley.acorn.annotation.RequiresActions;
import com.ardley.acorn.attribute.Attributes;
import com.ardley.acorn.attribute.Principal;
import com.ardley.acorn.attribute.PrincipalExtractor;
import com.ardley.acorn.context.RequestContext;
import com.ardley.acorn.evaluator.AuthorizationResult;
import com.ardley.acorn.evaluator.Evaluator;
import com.ardley.acorn.permission.PermissionSet;
import com.ardley.acorn.policy.EvaluationPolicy;
import com.ardley.acorn.resource.ResourceExtractor;
import com.ardley.acorn.store.PermissionStore;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * CDI interceptor that enforces Acorn authorization.
 *
 * <p>Triggered by the {@link Secured} interceptor binding. Reads
 * {@link RequiresActions} and {@link Authorized} annotations on the
 * intercepted method and performs the same evaluation pipeline as the
 * JAX-RS filter and Spring interceptor.
 *
 * <p>Requires an {@link HttpServletRequest} to be injectable (standard in
 * servlet-based CDI environments like Quarkus, WildFly, and Payara).
 */
@Interceptor
@Secured
public class AuthorizationInterceptor {

    private static final Logger log = LogManager.getLogger(AuthorizationInterceptor.class);

    @Inject private PrincipalExtractor principalExtractor;
    @Inject private PermissionStore permissionStore;
    @Inject private EvaluationPolicy policy;
    @Inject private CdiExtractorResolver extractorResolver;
    @Inject private ActionRegistry actionRegistry;
    @Inject private HttpServletRequest httpRequest;

    @AroundInvoke
    public Object authorize(InvocationContext ctx) throws Exception {
        Method method = ctx.getMethod();
        RequestContext requestContext = new ServletRequestContext(httpRequest);

        Principal principal = principalExtractor.extract(requestContext)
                .orElseThrow(() -> new AcornCdiAuthenticationException("No authenticated principal"));

        httpRequest.setAttribute("acorn.principal", principal);

        Optional<PermissionSet> permsOpt = permissionStore.getPermissionSet(principal.permissionKey());
        if (permsOpt.isEmpty()) {
            log.warn("No permissions found for key: {}", principal.permissionKey());
            throw new AcornCdiAccessDeniedException("No permissions configured for this role",
                    null, null, null);
        }

        PermissionSet permissions = permsOpt.get();

        // Gate check
        RequiresActions gate = method.getAnnotation(RequiresActions.class);
        if (gate != null) {
            for (String actionName : gate.value()) {
                Action action = actionRegistry.resolve(actionName);
                AuthorizationResult result = Evaluator.canPerformAction(permissions, action);
                if (result.isDenied()) {
                    log.info("Action \"{}\" denied: {}", actionName, result.reason());
                    throw new AcornCdiAccessDeniedException(result.reason(), actionName, null, null);
                }
            }
        }

        // Resource checks
        Parameter[] parameters = method.getParameters();
        for (Parameter param : parameters) {
            Authorized authorized = param.getAnnotation(Authorized.class);
            if (authorized == null) continue;

            ResourceExtractor<?> extractor = extractorResolver.resolve(authorized.extractor());

            String resourceId = extractor.extractId(requestContext)
                    .orElseThrow(() -> new AcornCdiResourceNotFoundException(
                            extractor.resourceType(), "(missing)"));

            Object resource = extractor.load(resourceId, principal);
            if (resource == null) {
                throw new AcornCdiResourceNotFoundException(extractor.resourceType(), resourceId);
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
                    throw new AcornCdiAccessDeniedException(
                            result.reason(), actionName, extractor.resourceType(), resourceId);
                }
            }

            httpRequest.setAttribute("acorn.resource." + extractor.resourceType(), resource);
        }

        return ctx.proceed();
    }
}
