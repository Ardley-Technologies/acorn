# acorn-guice

Guice integration for Acorn. Provides dependency injection wiring and automatic extractor resolution.

## Dependencies

```gradle
implementation("com.ardley.acorn:acorn-core:1.0.0")
implementation("com.ardley.acorn:acorn-jaxrs:1.0.0")
implementation("com.ardley.acorn:acorn-guice:1.0.0")
```

## Setup

Extend `AcornModule` and provide your bindings:

```java
public class AuthorizationModule extends AcornModule {

    @Override
    protected void configureAcorn() {
        bindPolicy(EvaluationPolicy.withIsolation("tenant_id"));
        bindPrincipalExtractor(JwtPrincipalExtractor.class);
        bindExtractor(DocumentExtractor.class);
        bindExtractor(UserExtractor.class);
        bindExtractor(ProjectExtractor.class);
    }

    @Provides
    @Singleton
    PermissionStore providePermissionStore(DynamoDbClient dynamoDb) {
        PermissionLoader loader = key -> {
            String tenantId = key.get(0);
            String roleId = key.get(1);
            return dynamoDb.getItem(tenantId, "ROLE#" + roleId)
                .map(item -> PermissionSet.fromJson(item.getString("configuration")));
        };
        return new CachingPermissionStore(loader, Duration.ofMinutes(5), 10_000);
    }

    @Provides
    @Singleton
    ActionRegistry provideActionRegistry() {
        ActionRegistry registry = new ActionRegistry();
        registry.registerAll(DocumentActions.class);
        registry.registerAll(UserActions.class);
        registry.registerAll(ProjectActions.class);
        return registry;
    }
}
```

Install the module:

```java
Injector injector = Guice.createInjector(
    new AuthorizationModule(),
    new ServiceModule(),
    new PersistenceModule()
);
```

## How it works

- `AuthorizationFilter` is bound as a singleton and receives all dependencies via `@Inject`
- `ExtractorResolver` is implemented by `GuiceExtractorResolver` which looks up extractor beans via the Guice `Injector`
- Resource extractors are bound as singletons and resolved by their class at request time
- The `@Authorized(extractor = DocumentExtractor.class, ...)` annotation tells Guice which class to resolve

## Registering with Jersey

After creating the injector, register the filter:

```java
ResourceConfig config = new ResourceConfig();
config.register(injector.getInstance(AuthorizationFilter.class));
config.register(injector.getInstance(AuthzExceptionMapper.class));
// ... your resources
```

## Missing bindings fail fast

If you forget to bind a required component (`PermissionStore`, `PrincipalExtractor`, `EvaluationPolicy`, `ActionRegistry`), the injector throws `CreationException` at startup — not at the first request. This means misconfigurations surface during deployment, not in production traffic.

## Multiple extractors

Each `bindExtractor()` call makes that class available for `@Authorized` resolution. You can bind as many as you need. They're resolved by exact class match — no ambiguity:

```java
// In module:
bindExtractor(DocumentExtractor.class);
bindExtractor(UserExtractor.class);
bindExtractor(InvoiceExtractor.class);

// In endpoints:
@Authorized(extractor = DocumentExtractor.class, actions = "EditDocument")
@Authorized(extractor = InvoiceExtractor.class, actions = "ApproveInvoice")
```
