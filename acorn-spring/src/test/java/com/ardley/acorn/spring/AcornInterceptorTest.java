package com.ardley.acorn.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.ardley.acorn.action.Action;
import com.ardley.acorn.action.ActionRegistry;
import com.ardley.acorn.annotation.Authorized;
import com.ardley.acorn.annotation.RequiresActions;
import com.ardley.acorn.attribute.AttributeSource;
import com.ardley.acorn.attribute.Attributes;
import com.ardley.acorn.attribute.Principal;
import com.ardley.acorn.attribute.PrincipalExtractor;
import com.ardley.acorn.context.RequestContext;
import com.ardley.acorn.permission.PermissionSet;
import com.ardley.acorn.policy.EvaluationPolicy;
import com.ardley.acorn.resource.ResourceExtractor;
import com.ardley.acorn.store.PermissionStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Tests the Spring MVC AcornInterceptor end-to-end: principal extraction,
 * gate checks, resource loading with scope evaluation, exception propagation,
 * and request attribute population.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class AcornInterceptorTest {

    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock ApplicationContext applicationContext;

    private ActionRegistry actionRegistry;
    private ConfigurablePrincipalExtractor principalExtractor;
    private ConfigurablePermissionStore permissionStore;
    private AcornInterceptor interceptor;
    private Map<String, String> pathVariables;
    private Map<String, Object> attributes;

    @BeforeEach
    void setup() {
        actionRegistry = new ActionRegistry();
        actionRegistry.register(Actions.ListUsers);
        actionRegistry.register(Actions.UpdateUser);
        actionRegistry.register(Actions.DeleteUser);

        principalExtractor = new ConfigurablePrincipalExtractor();
        permissionStore = new ConfigurablePermissionStore();

        InMemoryUserExtractor userExtractor = new InMemoryUserExtractor();
        when(applicationContext.getBean(InMemoryUserExtractor.class)).thenReturn(userExtractor);

        SpringExtractorResolver resolver = new SpringExtractorResolver(applicationContext);

        interceptor = new AcornInterceptor(
                principalExtractor, permissionStore,
                EvaluationPolicy.withIsolation("tenant_id"),
                resolver, actionRegistry);

        pathVariables = new HashMap<>();
        attributes = new HashMap<>();

        lenient().when(request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE))
                .thenReturn(pathVariables);
        lenient().when(request.getAttribute(eq("acorn.principal")))
                .thenAnswer(inv -> attributes.get("acorn.principal"));
        lenient().doAnswer(inv -> {
            attributes.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(request).setAttribute(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        lenient().when(request.getRequestURI()).thenReturn("/users/u-1");
        lenient().when(request.getMethod()).thenReturn("PUT");
    }

    @Nested
    @DisplayName("Authentication")
    class Authentication {

        @Test
        @DisplayName("No principal throws AcornAuthenticationException")
        void noPrincipalThrows() throws Exception {
            principalExtractor.returns(null);

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerFor(gateMethod())))
                    .isInstanceOf(AcornAuthenticationException.class);
        }

        @Test
        @DisplayName("Authenticated principal stored as request attribute")
        void principalStoredAsAttribute() throws Exception {
            Principal principal = principal("t-1", "admin", "Eng");
            principalExtractor.returns(principal);
            permissionStore.returns(PermissionSet.allowAll());

            interceptor.preHandle(request, response, handlerFor(gateMethod()));

            assertThat(attributes.get(AcornInterceptor.PRINCIPAL_ATTRIBUTE)).isSameAs(principal);
        }
    }

    @Nested
    @DisplayName("Gate checks (@RequiresActions)")
    class GateChecks {

        @Test
        @DisplayName("Permitted action passes — returns true")
        void permittedPasses() throws Exception {
            principalExtractor.returns(principal("t-1", "admin", "Eng"));
            permissionStore.returns(PermissionSet.fromJson("""
                {"allow": {"ListUsers": "all"}}
                """));

            boolean result = interceptor.preHandle(request, response, handlerFor(gateMethod()));

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Denied action throws with action name")
        void deniedThrowsWithMetadata() throws Exception {
            principalExtractor.returns(principal("t-1", "viewer", "Eng"));
            permissionStore.returns(PermissionSet.empty());

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerFor(gateMethod())))
                    .isInstanceOf(AcornAccessDeniedException.class)
                    .satisfies(e -> {
                        var denied = (AcornAccessDeniedException) e;
                        assertThat(denied.actionName()).isEqualTo("ListUsers");
                        assertThat(denied.resourceType()).isNull();
                    });
        }

        @Test
        @DisplayName("No permission set throws AcornAccessDeniedException")
        void noPermissionsThrows() throws Exception {
            principalExtractor.returns(principal("t-1", "ghost", "Eng"));
            permissionStore.returns(null);

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerFor(gateMethod())))
                    .isInstanceOf(AcornAccessDeniedException.class)
                    .hasMessageContaining("No permissions configured");
        }

        @Test
        @DisplayName("Multi-action AND: second denied fails entire check")
        void multiActionSecondDenied() throws Exception {
            principalExtractor.returns(principal("t-1", "limited", "Eng"));
            permissionStore.returns(PermissionSet.fromJson("""
                {"allow": {"ListUsers": "all"}}
                """));

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerFor(multiGateMethod())))
                    .isInstanceOf(AcornAccessDeniedException.class)
                    .satisfies(e -> assertThat(((AcornAccessDeniedException) e).actionName()).isEqualTo("DeleteUser"));
        }
    }

    @Nested
    @DisplayName("Resource checks (@Authorized)")
    class ResourceChecks {

        @Test
        @DisplayName("Scoped access passes and stores resource")
        void scopedAccessStoresResource() throws Exception {
            principalExtractor.returns(principal("t-1", "manager", "Engineering"));
            permissionStore.returns(PermissionSet.fromJson("""
                {"allow": {"UpdateUser": {"department": {"match": "principal"}}}}
                """));
            pathVariables.put("id", "u-eng-1");

            boolean result = interceptor.preHandle(request, response, handlerFor(authorizedMethod()));

            assertThat(result).isTrue();
            Object stored = attributes.get(AcornInterceptor.RESOURCE_ATTRIBUTE_PREFIX + "user");
            assertThat(stored).isNotNull();
            assertThat(((InMemoryUserExtractor.User) stored).id()).isEqualTo("u-eng-1");
        }

        @Test
        @DisplayName("Scope mismatch throws with full metadata")
        void scopeMismatchThrows() throws Exception {
            principalExtractor.returns(principal("t-1", "manager", "Engineering"));
            permissionStore.returns(PermissionSet.fromJson("""
                {"allow": {"UpdateUser": {"department": {"match": "principal"}}}}
                """));
            pathVariables.put("id", "u-sales-1");

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerFor(authorizedMethod())))
                    .isInstanceOf(AcornAccessDeniedException.class)
                    .satisfies(e -> {
                        var denied = (AcornAccessDeniedException) e;
                        assertThat(denied.actionName()).isEqualTo("UpdateUser");
                        assertThat(denied.resourceType()).isEqualTo("user");
                        assertThat(denied.resourceId()).isEqualTo("u-sales-1");
                    });
        }

        @Test
        @DisplayName("Cross-tenant isolation denies even with allowAll")
        void isolationDenies() throws Exception {
            principalExtractor.returns(principal("t-EVIL", "admin", "Eng"));
            permissionStore.returns(PermissionSet.allowAll());
            pathVariables.put("id", "u-eng-1");

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerFor(authorizedMethod())))
                    .isInstanceOf(AcornAccessDeniedException.class)
                    .hasMessageContaining("Isolation violation");
        }

        @Test
        @DisplayName("Missing path variable throws AcornResourceNotFoundException")
        void missingPathVarThrows() throws Exception {
            principalExtractor.returns(principal("t-1", "admin", "Eng"));
            permissionStore.returns(PermissionSet.allowAll());
            // pathVariables empty

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerFor(authorizedMethod())))
                    .isInstanceOf(AcornResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Resource not found throws AcornResourceNotFoundException with ID")
        void resourceNotFoundThrows() throws Exception {
            principalExtractor.returns(principal("t-1", "admin", "Eng"));
            permissionStore.returns(PermissionSet.allowAll());
            pathVariables.put("id", "nonexistent");

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerFor(authorizedMethod())))
                    .isInstanceOf(AcornResourceNotFoundException.class)
                    .satisfies(e -> {
                        var nf = (AcornResourceNotFoundException) e;
                        assertThat(nf.resourceType()).isEqualTo("user");
                        assertThat(nf.resourceId()).isEqualTo("nonexistent");
                    });
        }

        @Test
        @DisplayName("Unconditional allow grants access across departments")
        void unconditionalAllowGrants() throws Exception {
            principalExtractor.returns(principal("t-1", "admin", "Executive"));
            permissionStore.returns(PermissionSet.fromJson("""
                {"allow": {"UpdateUser": "all"}}
                """));
            pathVariables.put("id", "u-sales-1");

            boolean result = interceptor.preHandle(request, response, handlerFor(authorizedMethod()));

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("Non-handler requests")
    class NonHandler {

        @Test
        @DisplayName("Non-HandlerMethod objects pass through without checks")
        void nonHandlerMethodPassesThrough() throws Exception {
            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    enum Actions implements Action {
        ListUsers("List users"),
        UpdateUser("Update a user"),
        DeleteUser("Delete a user");

        private final String desc;
        Actions(String desc) { this.desc = desc; }
        @Override public String description() { return desc; }
    }

    static class InMemoryUserExtractor implements ResourceExtractor<InMemoryUserExtractor.User> {
        record User(String id, String tenantId, String department) {}

        @Override public String resourceType() { return "user"; }
        @Override public Optional<String> extractId(RequestContext context) { return context.pathParam("id"); }
        @Override public User load(String resourceId, AttributeSource principal) {
            if ("nonexistent".equals(resourceId)) return null;
            String dept = resourceId.contains("sales") ? "Sales" : "Engineering";
            return new User(resourceId, "t-1", dept);
        }
        @Override public Attributes attributes(User user) {
            return Attributes.builder()
                    .with("tenant_id", user.tenantId)
                    .with("department", user.department)
                    .build();
        }
    }

    static class ConfigurablePrincipalExtractor implements PrincipalExtractor {
        private Principal principal;
        void returns(Principal p) { this.principal = p; }
        @Override public Optional<Principal> extract(RequestContext context) { return Optional.ofNullable(principal); }
    }

    static class ConfigurablePermissionStore implements PermissionStore {
        private PermissionSet permissionSet;
        void returns(PermissionSet ps) { this.permissionSet = ps; }
        @Override public Optional<PermissionSet> getPermissionSet(List<String> key) { return Optional.ofNullable(permissionSet); }
        @Override public void invalidate(List<String> key) {}
    }

    private static Principal principal(String tenantId, String role, String department) {
        return new Principal() {
            @Override public Optional<String> attribute(String name) {
                return switch (name) {
                    case "tenant_id" -> Optional.of(tenantId);
                    case "department" -> Optional.of(department);
                    default -> Optional.empty();
                };
            }
            @Override public List<String> permissionKey() { return List.of(tenantId, role); }
        };
    }

    @RequiresActions("ListUsers")
    public void listEndpoint() {}

    @RequiresActions({"ListUsers", "DeleteUser"})
    public void multiGateEndpoint() {}

    public void updateEndpoint(
            @Authorized(extractor = InMemoryUserExtractor.class, actions = "UpdateUser")
            String userId) {}

    private Method gateMethod() throws Exception { return getClass().getMethod("listEndpoint"); }
    private Method multiGateMethod() throws Exception { return getClass().getMethod("multiGateEndpoint"); }
    private Method authorizedMethod() throws Exception { return getClass().getMethod("updateEndpoint", String.class); }

    private HandlerMethod handlerFor(Method method) {
        return new HandlerMethod(this, method);
    }
}
