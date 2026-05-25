# acorn-core

The evaluation engine and permission model. Zero framework dependencies.

This module contains everything needed to make authorization decisions programmatically — without any HTTP framework, DI container, or servlet API.

## Dependencies

```gradle
implementation("com.ardley.acorn:acorn-core:1.0.0")
```

Only requires Guava (for `CachingPermissionStore`) and Jackson (for JSON parsing).

## What's inside

| Package | Purpose |
|---------|---------|
| `annotation` | `@RequiresActions`, `@Authorized` — shared by all framework integrations |
| `action` | `Action` interface and `ActionRegistry` for defining and registering permissions |
| `attribute` | `AttributeSource`, `Attributes`, `Principal`, `PrincipalExtractor` |
| `context` | `RequestContext` — framework-agnostic request abstraction |
| `evaluator` | `Evaluator` — stateless decision engine |
| `permission` | `PermissionSet`, `PermissionLevel`, `ScopeFilter`, `AttributeFilter` |
| `policy` | `EvaluationPolicy` — configurable isolation rules |
| `resource` | `ResourceExtractor` interface |
| `role` | `RoleConfiguration`, `RoleConfigurationValidator` |
| `store` | `PermissionStore`, `PermissionLoader`, `CachingPermissionStore` |

## Usage without a framework

```java
import com.ardley.acorn.action.Action;
import com.ardley.acorn.action.ActionRegistry;
import com.ardley.acorn.attribute.Attributes;
import com.ardley.acorn.evaluator.AuthorizationResult;
import com.ardley.acorn.evaluator.Evaluator;
import com.ardley.acorn.permission.PermissionSet;
import com.ardley.acorn.policy.EvaluationPolicy;

// 1. Define actions
enum MyActions implements Action {
    ViewDocument("View a document"),
    EditDocument("Edit a document");

    private final String desc;
    MyActions(String desc) { this.desc = desc; }
    @Override public String description() { return desc; }
}

// 2. Parse a permission set
PermissionSet permissions = PermissionSet.fromJson("""
    {
        "allow": {
            "ViewDocument": "all",
            "EditDocument": {"department": {"match": "principal"}}
        }
    }
    """);

// 3. Build principal and resource attributes
Attributes principal = Attributes.builder()
    .with("tenant_id", "acme")
    .with("department", "Engineering")
    .build();

Attributes resource = Attributes.builder()
    .with("tenant_id", "acme")
    .with("department", "Engineering")
    .build();

// 4. Configure policy
EvaluationPolicy policy = EvaluationPolicy.withIsolation("tenant_id");

// 5. Evaluate
AuthorizationResult result = Evaluator.evaluate(
    permissions, principal, resource, policy, MyActions.EditDocument);

assert result.permitted(); // true — same department, same tenant
```

## Defining actions

Actions are typically enums that implement the `Action` interface. Since `Enum.name()` is final and already satisfies `Action.name()`, you only need to implement `description()`:

```java
public enum DocumentActions implements Action {
    ListDocuments("List all documents"),
    CreateDocument("Create a new document"),
    ViewDocument("View a document's contents"),
    EditDocument("Edit a document"),
    DeleteDocument("Permanently remove a document");

    private final String description;
    DocumentActions(String description) { this.description = description; }
    @Override public String description() { return description; }
}
```

Register them in a registry for use by the framework integrations:

```java
ActionRegistry registry = new ActionRegistry();
registry.registerAll(DocumentActions.class);
registry.registerAll(UserActions.class);

// Introspection — list all available actions for admin UIs
registry.all().forEach(action ->
    System.out.println(action.name() + ": " + action.description()));
```

## Implementing a ResourceExtractor

```java
public class DocumentExtractor implements ResourceExtractor<Document> {

    private final DocumentRepository repo;

    public DocumentExtractor(DocumentRepository repo) {
        this.repo = repo;
    }

    @Override
    public String resourceType() {
        return "document";
    }

    @Override
    public Optional<String> extractId(RequestContext context) {
        return context.pathParam("docId");
    }

    @Override
    public Document load(String resourceId, AttributeSource principal) {
        String tenantId = principal.attribute("tenant_id").orElseThrow();
        return repo.findById(tenantId, resourceId);
    }

    @Override
    public Attributes attributes(Document doc) {
        return Attributes.builder()
            .with("tenant_id", doc.getTenantId())
            .with("department", doc.getDepartment())
            .with("classification", doc.getClassification())
            .with("owner", doc.getCreatedBy())
            .build();
    }
}
```

## Implementing a PrincipalExtractor

```java
public class JwtPrincipalExtractor implements PrincipalExtractor {

    private final JwtDecoder decoder;

    @Override
    public Optional<Principal> extract(RequestContext context) {
        return context.header("Authorization")
            .filter(h -> h.startsWith("Bearer "))
            .map(h -> h.substring(7))
            .map(decoder::decode)
            .map(claims -> new JwtPrincipal(claims));
    }
}

class JwtPrincipal implements Principal {
    private final Map<String, String> claims;

    @Override
    public Optional<String> attribute(String name) {
        return Optional.ofNullable(claims.get(name));
    }

    @Override
    public List<String> permissionKey() {
        return List.of(claims.get("tenant_id"), claims.get("role"));
    }
}
```

## Permission store with caching

```java
// Your loader fetches from the database
PermissionLoader loader = key -> {
    String tenantId = key.get(0);
    String roleId = key.get(1);
    return dynamoDb.getItem(tenantId, "ROLE#" + roleId)
        .map(item -> PermissionSet.fromJson(item.getString("configuration")));
};

// Wrap with 5-minute TTL cache
PermissionStore store = new CachingPermissionStore(loader, Duration.ofMinutes(5), 10_000);

// Invalidate after role updates
store.invalidate(List.of("tenant-abc", "manager"));
```

## Role configuration validation

```java
RoleConfigurationValidator validator = RoleConfigurationValidator.builder()
    .withHierarchy("EditDocument", "ViewDocument")   // edit implies view
    .withHierarchy("DeleteDocument", "ViewDocument")  // delete implies view
    .build();

var result = validator.validate(submittedJson);
if (!result.isValid()) {
    // Return errors to the admin
    result.errors().forEach(System.err::println);
}
```
