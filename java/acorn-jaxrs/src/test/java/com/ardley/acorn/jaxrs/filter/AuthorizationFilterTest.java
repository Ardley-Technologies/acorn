package com.ardley.acorn.jaxrs.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import com.ardley.acorn.action.Action;
import com.ardley.acorn.action.ActionRegistry;
import com.ardley.acorn.annotation.Authorized;
import com.ardley.acorn.annotation.RequiresActions;
import com.ardley.acorn.attribute.AttributeSource;
import com.ardley.acorn.attribute.Attributes;
import com.ardley.acorn.attribute.Principal;
import com.ardley.acorn.attribute.PrincipalExtractor;
import com.ardley.acorn.context.RequestContext;
import com.ardley.acorn.jaxrs.exception.AuthenticationRequiredException;
import com.ardley.acorn.jaxrs.exception.AuthorizationDeniedException;
import com.ardley.acorn.jaxrs.exception.ResourceNotFoundException;
import com.ardley.acorn.permission.PermissionSet;
import com.ardley.acorn.policy.EvaluationPolicy;
import com.ardley.acorn.resource.ResourceExtractor;
import com.ardley.acorn.store.PermissionStore;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests the AuthorizationFilter end-to-end: principal extraction, gate checks,
 * resource loading with scope evaluation, exception propagation with structured metadata,
 * and context population for downstream handlers.
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationFilterTest {

    @Mock ContainerRequestContext ctx;
    @Mock UriInfo uriInfo;
    @Mock ResourceInfo resourceInfo;

    private ActionRegistry actionRegistry;
    private ConfigurablePrincipalExtractor principalExtractor;
    private ConfigurablePermissionStore permissionStore;
    private AuthorizationFilter filter;
    private MultivaluedHashMap<String, String> pathParams;

    @BeforeEach
    void setup() {
        actionRegistry = new ActionRegistry();
        actionRegistry.register(Actions.ListUsers);
        actionRegistry.register(Actions.UpdateUser);
        actionRegistry.register(Actions.DeleteUser);

        principalExtractor = new ConfigurablePrincipalExtractor();
        permissionStore = new ConfigurablePermissionStore();

        ExtractorResolver resolver = clazz -> new InMemoryUserExtractor();

        filter = new AuthorizationFilter(
                principalExtractor,
                permissionStore,
                EvaluationPolicy.withIsolation("tenant_id"),
                resolver,
                actionRegistry);

        injectResourceInfo();

        pathParams = new MultivaluedHashMap<>();
        lenient().when(ctx.getUriInfo()).thenReturn(uriInfo);
        lenient().when(uriInfo.getPath()).thenReturn("/users/u-1");
        lenient().when(uriInfo.getPathParameters()).thenReturn(pathParams);
        lenient().when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>());
        lenient().when(ctx.getMethod()).thenReturn("PUT");
    }

    @Nested
    @DisplayName("Authentication")
    class Authentication {

        @Test
        @DisplayName("Unauthenticated request throws AuthenticationRequiredException")
        void unauthenticatedThrows() throws Exception {
            principalExtractor.returns(null);
            setMethod(gateMethod());

            assertThatThrownBy(() -> filter.filter(ctx))
                    .isInstanceOf(AuthenticationRequiredException.class);
        }

        @Test
        @DisplayName("Authenticated principal stored in request context for downstream access")
        void principalStoredInContext() throws Exception {
            Principal principal = principal("t-1", "admin", "Engineering");
            principalExtractor.returns(principal);
            permissionStore.returns(PermissionSet.allowAll());
            setMethod(gateMethod());

            filter.filter(ctx);

            ArgumentCaptor<Principal> captor = ArgumentCaptor.forClass(Principal.class);
            verify(ctx).setProperty(eq(AuthorizationFilter.PRINCIPAL_PROPERTY), captor.capture());
            assertThat(captor.getValue()).isSameAs(principal);
        }
    }

    @Nested
    @DisplayName("Gate checks (@RequiresActions)")
    class GateChecks {

        @Test
        @DisplayName("Permitted action passes silently")
        void permittedActionPasses() throws Exception {
            principalExtractor.returns(principal("t-1", "admin", "Eng"));
            permissionStore.returns(PermissionSet.fromJson("""
                {"allow": {"ListUsers": "all"}}
                """));
            setMethod(gateMethod());

            filter.filter(ctx); // no exception
        }

        @Test
        @DisplayName("Missing action denied with correct metadata")
        void missingActionDeniedWithMetadata() throws Exception {
            principalExtractor.returns(principal("t-1", "viewer", "Eng"));
            permissionStore.returns(PermissionSet.empty());
            setMethod(gateMethod());

            assertThatThrownBy(() -> filter.filter(ctx))
                    .isInstanceOf(AuthorizationDeniedException.class)
                    .satisfies(e -> {
                        var denied = (AuthorizationDeniedException) e;
                        assertThat(denied.kind()).isEqualTo(AuthorizationDeniedException.DenialKind.GATE);
                        assertThat(denied.actionName()).isEqualTo("ListUsers");
                        assertThat(denied.resourceType()).isNull();
                        assertThat(denied.resourceId()).isNull();
                    });
        }

        @Test
        @DisplayName("No permission set for role throws with NO_PERMISSIONS kind")
        void noPermissionSetThrows() throws Exception {
            principalExtractor.returns(principal("t-1", "ghost", "Eng"));
            permissionStore.returns(null);
            setMethod(gateMethod());

            assertThatThrownBy(() -> filter.filter(ctx))
                    .isInstanceOf(AuthorizationDeniedException.class)
                    .satisfies(e -> {
                        var denied = (AuthorizationDeniedException) e;
                        assertThat(denied.kind()).isEqualTo(AuthorizationDeniedException.DenialKind.NO_PERMISSIONS);
                    });
        }

        @Test
        @DisplayName("Multi-action: first permitted, second denied — request fails")
        void multiActionAndSemanticsFirstPassSecondFails() throws Exception {
            principalExtractor.returns(principal("t-1", "limited", "Eng"));
            permissionStore.returns(PermissionSet.fromJson("""
                {"allow": {"ListUsers": "all"}}
                """));
            setMethod(multiGateMethod());

            assertThatThrownBy(() -> filter.filter(ctx))
                    .isInstanceOf(AuthorizationDeniedException.class)
                    .satisfies(e -> {
                        var denied = (AuthorizationDeniedException) e;
                        assertThat(denied.actionName()).isEqualTo("DeleteUser");
                    });
        }
    }

    @Nested
    @DisplayName("Resource checks (@Authorized)")
    class ResourceChecks {

        @Test
        @DisplayName("Permitted scoped access loads resource and stores in context")
        void scopedAccessStoresResource() throws Exception {
            principalExtractor.returns(principal("t-1", "manager", "Engineering"));
            permissionStore.returns(PermissionSet.fromJson("""
                {"allow": {"UpdateUser": {"department": {"match": "principal"}}}}
                """));
            pathParams.add("id", "u-eng-1");
            setMethod(authorizedMethod());

            filter.filter(ctx);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(ctx).setProperty(eq(AuthorizationFilter.RESOURCE_PROPERTY_PREFIX + "user"), captor.capture());
            assertThat(captor.getValue()).isInstanceOf(InMemoryUserExtractor.User.class);
            var user = (InMemoryUserExtractor.User) captor.getValue();
            assertThat(user.id()).isEqualTo("u-eng-1");
        }

        @Test
        @DisplayName("Scope mismatch denies with RESOURCE kind and correct metadata")
        void scopeMismatchDeniesWithMetadata() throws Exception {
            principalExtractor.returns(principal("t-1", "manager", "Engineering"));
            permissionStore.returns(PermissionSet.fromJson("""
                {"allow": {"UpdateUser": {"department": {"match": "principal"}}}}
                """));
            pathParams.add("id", "u-sales-1");
            setMethod(authorizedMethod());

            assertThatThrownBy(() -> filter.filter(ctx))
                    .isInstanceOf(AuthorizationDeniedException.class)
                    .satisfies(e -> {
                        var denied = (AuthorizationDeniedException) e;
                        assertThat(denied.kind()).isEqualTo(AuthorizationDeniedException.DenialKind.RESOURCE);
                        assertThat(denied.actionName()).isEqualTo("UpdateUser");
                        assertThat(denied.resourceType()).isEqualTo("user");
                        assertThat(denied.resourceId()).isEqualTo("u-sales-1");
                    });
        }

        @Test
        @DisplayName("Cross-tenant isolation denies even with allowAll permissions")
        void isolationDenies() throws Exception {
            principalExtractor.returns(principal("t-EVIL", "admin", "Eng"));
            permissionStore.returns(PermissionSet.allowAll());
            pathParams.add("id", "u-eng-1");
            setMethod(authorizedMethod());

            assertThatThrownBy(() -> filter.filter(ctx))
                    .isInstanceOf(AuthorizationDeniedException.class)
                    .satisfies(e -> {
                        var denied = (AuthorizationDeniedException) e;
                        assertThat(denied.getMessage()).contains("Isolation violation");
                    });
        }

        @Test
        @DisplayName("Missing path param throws ResourceNotFoundException")
        void missingPathParamThrows() throws Exception {
            principalExtractor.returns(principal("t-1", "admin", "Eng"));
            permissionStore.returns(PermissionSet.allowAll());
            // pathParams empty — no "id"
            setMethod(authorizedMethod());

            assertThatThrownBy(() -> filter.filter(ctx))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Resource not found throws ResourceNotFoundException with ID")
        void resourceNotFoundThrows() throws Exception {
            principalExtractor.returns(principal("t-1", "admin", "Eng"));
            permissionStore.returns(PermissionSet.allowAll());
            pathParams.add("id", "nonexistent");
            setMethod(authorizedMethod());

            assertThatThrownBy(() -> filter.filter(ctx))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .satisfies(e -> {
                        var notFound = (ResourceNotFoundException) e;
                        assertThat(notFound.resourceType()).isEqualTo("user");
                        assertThat(notFound.resourceId()).isEqualTo("nonexistent");
                    });
        }

        @Test
        @DisplayName("Unconditional allow grants access to any resource in same tenant")
        void unconditionalAllowGrants() throws Exception {
            principalExtractor.returns(principal("t-1", "admin", "Executive"));
            permissionStore.returns(PermissionSet.fromJson("""
                {"allow": {"UpdateUser": "all"}}
                """));
            pathParams.add("id", "u-sales-1");
            setMethod(authorizedMethod());

            filter.filter(ctx); // no exception

            verify(ctx).setProperty(eq(AuthorizationFilter.RESOURCE_PROPERTY_PREFIX + "user"),
                    org.mockito.ArgumentMatchers.any());
        }
    }

    // -----------------------------------------------------------------------
    // Test actions
    // -----------------------------------------------------------------------

    enum Actions implements Action {
        ListUsers("List users"),
        UpdateUser("Update a user"),
        DeleteUser("Delete a user");

        private final String desc;
        Actions(String desc) { this.desc = desc; }
        @Override public String description() { return desc; }
    }

    // -----------------------------------------------------------------------
    // Test resource extractor
    // -----------------------------------------------------------------------

    static class InMemoryUserExtractor implements ResourceExtractor<InMemoryUserExtractor.User> {
        record User(String id, String tenantId, String department) {}

        @Override public String resourceType() { return "user"; }

        @Override public Optional<String> extractId(RequestContext context) {
            return context.pathParam("id");
        }

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

    // -----------------------------------------------------------------------
    // Configurable test doubles
    // -----------------------------------------------------------------------

    static class ConfigurablePrincipalExtractor implements PrincipalExtractor {
        private Principal principal;

        void returns(Principal p) { this.principal = p; }

        @Override
        public Optional<Principal> extract(RequestContext context) {
            return Optional.ofNullable(principal);
        }
    }

    static class ConfigurablePermissionStore implements PermissionStore {
        private PermissionSet permissionSet;

        void returns(PermissionSet ps) { this.permissionSet = ps; }

        @Override
        public Optional<PermissionSet> getPermissionSet(List<String> key) {
            return Optional.ofNullable(permissionSet);
        }

        @Override
        public void invalidate(List<String> key) {}
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Principal principal(String tenantId, String role, String department) {
        return new Principal() {
            @Override
            public Optional<String> attribute(String name) {
                return switch (name) {
                    case "tenant_id" -> Optional.of(tenantId);
                    case "department" -> Optional.of(department);
                    default -> Optional.empty();
                };
            }

            @Override
            public List<String> permissionKey() {
                return List.of(tenantId, role);
            }
        };
    }

    private void injectResourceInfo() {
        try {
            var field = AuthorizationFilter.class.getDeclaredField("resourceInfo");
            field.setAccessible(true);
            field.set(filter, resourceInfo);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setMethod(Method method) {
        lenient().when(resourceInfo.getResourceMethod()).thenReturn(method);
    }

    // Simulated annotated endpoints
    @RequiresActions("ListUsers")
    public void listEndpoint() {}

    @RequiresActions({"ListUsers", "DeleteUser"})
    public void multiGateEndpoint() {}

    public void updateEndpoint(
            @PathParam("id")
            @Authorized(extractor = InMemoryUserExtractor.class, actions = "UpdateUser")
            String userId) {}

    private Method gateMethod() throws Exception {
        return getClass().getMethod("listEndpoint");
    }

    private Method multiGateMethod() throws Exception {
        return getClass().getMethod("multiGateEndpoint");
    }

    private Method authorizedMethod() throws Exception {
        return getClass().getMethod("updateEndpoint", String.class);
    }
}
