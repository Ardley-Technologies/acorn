package com.ardley.acorn.spring;

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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Parameter;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Spring MVC interceptor that enforces Acorn authorization.
 *
 * <p>Reads {@link RequiresActions} on handler methods for gate checks and
 * {@link Authorized} on method parameters for resource-level authorization.
 *
 * <p>Register via {@link org.springframework.web.servlet.config.annotation.InterceptorRegistry}:
 * <pre>{@code
 * @Configuration
 * public class WebConfig implements WebMvcConfigurer {
 *     @Autowired AcornInterceptor acornInterceptor;
 *
 *     public void addInterceptors(InterceptorRegistry registry) {
 *         registry.addInterceptor(acornInterceptor);
 *     }
 * }
 * }</pre>
 *
 * <p>Throws the same exception types as the JAX-RS filter for consistent
 * handling via {@code @ControllerAdvice}.
 */
public class AcornInterceptor implements HandlerInterceptor {

    private static final Logger log = LogManager.getLogger(AcornInterceptor.class);

    public static final String PRINCIPAL_ATTRIBUTE = "acorn.principal";
    public static final String RESOURCE_ATTRIBUTE_PREFIX = "acorn.resource.";

    private final PrincipalExtractor principalExtractor;
    private final PermissionStore permissionStore;
    private final EvaluationPolicy policy;
    private final SpringExtractorResolver extractorResolver;
    private final ActionRegistry actionRegistry;

    public AcornInterceptor(
            PrincipalExtractor principalExtractor,
            PermissionStore permissionStore,
            EvaluationPolicy policy,
            SpringExtractorResolver extractorResolver,
            ActionRegistry actionRegistry) {
        this.principalExtractor = principalExtractor;
        this.permissionStore = permissionStore;
        this.policy = policy;
        this.extractorResolver = extractorResolver;
        this.actionRegistry = actionRegistry;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequestContext requestContext = new SpringRequestContext(request);

        Principal principal = principalExtractor.extract(requestContext)
                .orElseThrow(() -> new AcornAuthenticationException("No authenticated principal"));

        request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);

        Optional<PermissionSet> permsOpt = permissionStore.getPermissionSet(principal.permissionKey());
        if (permsOpt.isEmpty()) {
            log.warn("No permissions found for key: {}", principal.permissionKey());
            throw new AcornAccessDeniedException("No permissions configured for this role",
                    null, null, null);
        }

        PermissionSet permissions = permsOpt.get();

        // Gate check
        RequiresActions gate = handlerMethod.getMethodAnnotation(RequiresActions.class);
        if (gate != null) {
            for (String actionName : gate.value()) {
                Action action = actionRegistry.resolve(actionName);
                AuthorizationResult result = Evaluator.canPerformAction(permissions, action);
                if (result.isDenied()) {
                    log.info("Action \"{}\" denied: {}", actionName, result.reason());
                    throw new AcornAccessDeniedException(result.reason(), actionName, null, null);
                }
            }
        }

        // Resource-level checks
        Parameter[] parameters = handlerMethod.getMethod().getParameters();
        for (Parameter param : parameters) {
            Authorized authorized = param.getAnnotation(Authorized.class);
            if (authorized == null) continue;

            ResourceExtractor<?> extractor = extractorResolver.resolve(authorized.extractor());

            String resourceId = extractor.extractId(requestContext)
                    .orElseThrow(() -> new AcornResourceNotFoundException(
                            extractor.resourceType(), "(missing)"));

            Object resource = extractor.load(resourceId, principal);
            if (resource == null) {
                throw new AcornResourceNotFoundException(extractor.resourceType(), resourceId);
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
                    throw new AcornAccessDeniedException(
                            result.reason(), actionName, extractor.resourceType(), resourceId);
                }
            }

            request.setAttribute(RESOURCE_ATTRIBUTE_PREFIX + extractor.resourceType(), resource);
        }

        return true;
    }
}
