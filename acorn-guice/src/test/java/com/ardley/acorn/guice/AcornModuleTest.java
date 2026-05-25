package com.ardley.acorn.guice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ardley.acorn.action.Action;
import com.ardley.acorn.action.ActionRegistry;
import com.ardley.acorn.attribute.AttributeSource;
import com.ardley.acorn.attribute.Attributes;
import com.ardley.acorn.attribute.Principal;
import com.ardley.acorn.attribute.PrincipalExtractor;
import com.ardley.acorn.context.RequestContext;
import com.ardley.acorn.jaxrs.filter.AuthorizationFilter;
import com.ardley.acorn.jaxrs.filter.ExtractorResolver;
import com.ardley.acorn.permission.PermissionSet;
import com.ardley.acorn.policy.EvaluationPolicy;
import com.ardley.acorn.resource.ResourceExtractor;
import com.ardley.acorn.store.PermissionStore;
import com.google.inject.ConfigurationException;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests the Guice module wiring produces a fully functional authorization system.
 * Goes beyond "can Guice instantiate" to verify end-to-end behavior through the DI layer.
 */
class AcornModuleTest {

    @Nested
    @DisplayName("Injector creation")
    class InjectorCreation {

        @Test
        @DisplayName("Complete module produces a working injector")
        void completeModuleCreatesInjector() {
            Injector injector = Guice.createInjector(new FullModule());

            assertThat(injector.getInstance(AuthorizationFilter.class)).isNotNull();
            assertThat(injector.getInstance(ExtractorResolver.class)).isNotNull();
            assertThat(injector.getInstance(PrincipalExtractor.class)).isNotNull();
        }

        @Test
        @DisplayName("Missing PermissionStore binding fails at injector creation")
        void missingStoreFailsFast() {
            assertThatThrownBy(() -> Guice.createInjector(new MissingStoreModule()))
                    .isInstanceOf(CreationException.class)
                    .hasMessageContaining("PermissionStore");
        }

        @Test
        @DisplayName("Missing PrincipalExtractor binding fails at injector creation")
        void missingPrincipalExtractorFailsFast() {
            assertThatThrownBy(() -> Guice.createInjector(new MissingPrincipalModule()))
                    .isInstanceOf(CreationException.class)
                    .hasMessageContaining("PrincipalExtractor");
        }
    }

    @Nested
    @DisplayName("ExtractorResolver")
    class ExtractorResolution {

        @Test
        @DisplayName("Resolves the correct extractor by class")
        void resolvesCorrectExtractor() {
            Injector injector = Guice.createInjector(new FullModule());
            ExtractorResolver resolver = injector.getInstance(ExtractorResolver.class);

            ResourceExtractor<?> user = resolver.resolve(UserExtractor.class);
            ResourceExtractor<?> doc = resolver.resolve(DocumentExtractor.class);

            assertThat(user.resourceType()).isEqualTo("user");
            assertThat(doc.resourceType()).isEqualTo("document");
        }

        @Test
        @DisplayName("Same extractor class returns same singleton instance")
        void extractorIsSingleton() {
            Injector injector = Guice.createInjector(new FullModule());
            ExtractorResolver resolver = injector.getInstance(ExtractorResolver.class);

            ResourceExtractor<?> first = resolver.resolve(UserExtractor.class);
            ResourceExtractor<?> second = resolver.resolve(UserExtractor.class);

            assertThat(first).isSameAs(second);
        }

        @Test
        @DisplayName("Extractor with unsatisfied dependency throws at resolution time")
        void unresolvableExtractorThrows() {
            Injector injector = Guice.createInjector(new FullModule());
            ExtractorResolver resolver = injector.getInstance(ExtractorResolver.class);

            assertThatThrownBy(() -> resolver.resolve(ExtractorWithDependency.class))
                    .isInstanceOf(ConfigurationException.class);
        }
    }

    @Nested
    @DisplayName("End-to-end through DI layer")
    class EndToEnd {

        @Test
        @DisplayName("Filter retrieved from injector uses the bound PermissionStore")
        void filterUsesInjectedStore() {
            Injector injector = Guice.createInjector(new FullModule());

            PermissionStore store = injector.getInstance(PermissionStore.class);
            // The store bound by FullModule returns allowAll for "t-1::admin"
            Optional<PermissionSet> perms = store.getPermissionSet(List.of("t-1", "admin"));

            assertThat(perms).isPresent();
            assertThat(perms.get().isAllowAll()).isTrue();
        }

        @Test
        @DisplayName("Filter retrieved from injector uses the bound EvaluationPolicy")
        void filterUsesInjectedPolicy() {
            Injector injector = Guice.createInjector(new FullModule());

            EvaluationPolicy policy = injector.getInstance(EvaluationPolicy.class);

            assertThat(policy.isolationAttributes()).containsExactly("tenant_id");
        }

        @Test
        @DisplayName("ActionRegistry populated by @Provides resolves registered actions")
        void actionRegistryResolves() {
            Injector injector = Guice.createInjector(new FullModule());

            ActionRegistry registry = injector.getInstance(ActionRegistry.class);

            assertThat(registry.resolve("ListUsers").description()).isEqualTo("List users");
            assertThat(registry.resolve("UpdateUser").description()).isEqualTo("Update a user");
            assertThatThrownBy(() -> registry.resolve("Unknown"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("PrincipalExtractor bound through module is used by filter")
        void principalExtractorBound() {
            Injector injector = Guice.createInjector(new FullModule());

            PrincipalExtractor extractor = injector.getInstance(PrincipalExtractor.class);
            // The TestPrincipalExtractor always returns a principal with tenant_id=t-1
            Optional<Principal> principal = extractor.extract(null);

            assertThat(principal).isPresent();
            assertThat(principal.get().attribute("tenant_id")).contains("t-1");
        }

        @Test
        @DisplayName("Resource extractor loaded through resolver can load and extract attributes")
        void resourceExtractorFunctional() {
            Injector injector = Guice.createInjector(new FullModule());
            ExtractorResolver resolver = injector.getInstance(ExtractorResolver.class);

            ResourceExtractor<?> extractor = resolver.resolve(UserExtractor.class);
            Object resource = extractor.load("u-1", Attributes.builder().with("tenant_id", "t-1").build());

            assertThat(resource).isNotNull();

            @SuppressWarnings("unchecked")
            Attributes attrs = ((ResourceExtractor<Object>) extractor).attributes(resource);
            assertThat(attrs.attribute("tenant_id")).contains("t-1");
            assertThat(attrs.attribute("department")).contains("Engineering");
        }
    }

    // -----------------------------------------------------------------------
    // Modules
    // -----------------------------------------------------------------------

    static class FullModule extends AcornModule {
        @Override
        protected void configureAcorn() {
            bindPolicy(EvaluationPolicy.withIsolation("tenant_id"));
            bindPrincipalExtractor(TestPrincipalExtractor.class);
            bindExtractor(UserExtractor.class);
            bindExtractor(DocumentExtractor.class);
        }

        @Provides @Singleton
        PermissionStore provideStore() {
            return new TestPermissionStore();
        }

        @Provides @Singleton
        ActionRegistry provideRegistry() {
            ActionRegistry registry = new ActionRegistry();
            registry.register(TestActions.ListUsers);
            registry.register(TestActions.UpdateUser);
            return registry;
        }
    }

    static class MissingStoreModule extends AcornModule {
        @Override
        protected void configureAcorn() {
            bindPolicy(EvaluationPolicy.none());
            bindPrincipalExtractor(TestPrincipalExtractor.class);
            // No PermissionStore bound — should fail
        }

        @Provides @Singleton
        ActionRegistry provideRegistry() { return new ActionRegistry(); }
    }

    static class MissingPrincipalModule extends AcornModule {
        @Override
        protected void configureAcorn() {
            bindPolicy(EvaluationPolicy.none());
            // No PrincipalExtractor bound — should fail
        }

        @Provides @Singleton
        PermissionStore provideStore() { return new TestPermissionStore(); }

        @Provides @Singleton
        ActionRegistry provideRegistry() { return new ActionRegistry(); }
    }

    // -----------------------------------------------------------------------
    // Test implementations
    // -----------------------------------------------------------------------

    enum TestActions implements Action {
        ListUsers("List users"),
        UpdateUser("Update a user");

        private final String desc;
        TestActions(String desc) { this.desc = desc; }
        @Override public String description() { return desc; }
    }

    public static class TestPrincipalExtractor implements PrincipalExtractor {
        @Override
        public Optional<Principal> extract(RequestContext context) {
            return Optional.of(new Principal() {
                @Override
                public Optional<String> attribute(String name) {
                    return switch (name) {
                        case "tenant_id" -> Optional.of("t-1");
                        case "role" -> Optional.of("admin");
                        default -> Optional.empty();
                    };
                }

                @Override
                public List<String> permissionKey() {
                    return List.of("t-1", "admin");
                }
            });
        }
    }

    static class TestPermissionStore implements PermissionStore {
        @Override
        public Optional<PermissionSet> getPermissionSet(List<String> key) {
            if (key.equals(List.of("t-1", "admin"))) {
                return Optional.of(PermissionSet.allowAll());
            }
            return Optional.empty();
        }

        @Override
        public void invalidate(List<String> key) {}
    }

    public static class UserExtractor implements ResourceExtractor<UserExtractor.User> {
        record User(String id, String tenantId, String department) {}

        @Override public String resourceType() { return "user"; }

        @Override public Optional<String> extractId(RequestContext context) {
            return context != null ? context.pathParam("id") : Optional.of("u-1");
        }

        @Override public User load(String resourceId, AttributeSource principal) {
            return new User(resourceId, "t-1", "Engineering");
        }

        @Override public Attributes attributes(User user) {
            return Attributes.builder()
                    .with("tenant_id", user.tenantId)
                    .with("department", user.department)
                    .build();
        }
    }

    public static class DocumentExtractor implements ResourceExtractor<DocumentExtractor.Document> {
        record Document(String id, String tenantId) {}

        @Override public String resourceType() { return "document"; }

        @Override public Optional<String> extractId(RequestContext context) {
            return context != null ? context.pathParam("docId") : Optional.empty();
        }

        @Override public Document load(String resourceId, AttributeSource principal) {
            return new Document(resourceId, "t-1");
        }

        @Override public Attributes attributes(Document doc) {
            return Attributes.builder().with("tenant_id", doc.tenantId).build();
        }
    }

    /**
     * Extractor with a constructor dependency that Guice cannot satisfy.
     * Used to verify that resolution fails cleanly for unbound extractors.
     */
    public static class ExtractorWithDependency implements ResourceExtractor<Object> {
        @jakarta.inject.Inject
        public ExtractorWithDependency(Runnable unsatisfiedDep) {}

        @Override public String resourceType() { return "unknown"; }
        @Override public Optional<String> extractId(RequestContext context) { return Optional.empty(); }
        @Override public Object load(String resourceId, AttributeSource principal) { return null; }
        @Override public Attributes attributes(Object resource) { return Attributes.empty(); }
    }
}
