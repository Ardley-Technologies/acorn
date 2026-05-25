<div align="center">
<img src="assets/acorn-logo.png" alt="Acorn" width="180">

# Acorn

[![Build & Test](https://github.com/Ardley-Technologies/acorn/actions/workflows/build.yml/badge.svg)](https://github.com/Ardley-Technologies/acorn/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

*Declarative, schema-free RBAC for Java APIs.*

[Why Acorn?](#why-another-rbac-library) · [How it works](#how-it-works) · [Permissions](#schema-free-permissions) · [Scope filters](#scope-filters) · [Frameworks](#framework-support) · [Get started](#getting-started)
</div>

---

Most authorization libraries make you scatter permission checks throughout your handlers. You end up with `if (user.hasRole("admin"))` buried inside business logic, or worse, you forget the check entirely and ship an open endpoint.

Acorn takes a different approach. Authorization is declared at the API surface — on your endpoint annotations — and evaluated before your handler code ever executes. If a request doesn't have permission, it never reaches your business logic. Period.

## Table of Contents

- [Why another RBAC library?](#why-another-rbac-library)
- [How it works](#how-it-works)
- [Schema-free permissions](#schema-free-permissions)
- [Scope filters](#scope-filters)
- [Deny wins](#deny-wins)
- [Isolation policy](#isolation-policy)
- [Framework support](#framework-support)
- [You own the principal](#you-own-the-principal)
- [You own the resource](#you-own-the-resource)
- [You own the storage](#you-own-the-storage)
- [Getting started](#getting-started)
- [License](#license)

## Why another RBAC library?

I've used Spring Security, Apache Shiro, Keycloak adapters, policy-language engines, and custom-built filter chains. They all share the same friction points:

1. **Hardcoded role names.** You end up with `@RolesAllowed("ADMIN")` strings that drift out of sync with your actual role definitions. Rename a role in your admin panel and your annotations silently stop working.

2. **Binary access.** You either have the role or you don't. No way to say "managers can edit users, but only in their own department." Real authorization is scoped — it depends on *which* resource you're touching, not just *what* you're doing.

3. **Framework lock-in.** Your authorization logic is welded to Spring Security's filter chain or JAX-RS's `SecurityContext`. Switching frameworks means rewriting everything.

4. **Schema rigidity.** Most systems assume you have "users" and "roles" with specific fields. If your domain calls them "agents" and "permission profiles," you're fighting the library instead of using it.

5. **Custom policy languages.** Some newer systems introduce a dedicated policy syntax with its own grammar, compiler, and tooling chain. Policies live in separate files, get deployed independently, and reference entity types and attributes with no compile-time link to your application code. When your data model changes, nothing tells you your policies are stale until a user gets an unexpected 403 in production. You're maintaining two codebases in two languages that have to agree on the shape of the world — and they diverge silently.

6. **External service dependency.** Policy engines that require a sidecar or network call for every authorization decision add latency, introduce a new failure mode, and mean your app can't start without the policy service being healthy. Your authorization path becomes as fragile as your least reliable infrastructure component.

Acorn takes a different position. Permissions are plain JSON — no custom syntax, no separate tooling, no deployment pipeline for policy files. Your existing JSON tooling works. Permissions live in your database alongside your application data, managed through your existing APIs. Evaluation happens in-process in microseconds with no network calls. And your IDE can trace from annotation to evaluator to scope filter in a single jump — no `.policy` file to cross-reference.

## How it works

You define **actions** — discrete operations your API supports. You define **resource extractors** — components that know how to load a resource and expose its properties. Then you annotate your endpoints with the actions they require.

At request time, Acorn:

1. Extracts the principal from the request (JWT, session, API key — you decide how)
2. Loads the principal's permission set from your store (DynamoDB, Postgres, Redis — you decide where)
3. Evaluates whether the principal can perform the declared actions, optionally against the specific resource being accessed
4. Either lets the request through or throws — before your handler ever sees it

```java
@GET
@RequiresActions("ListUsers")
public Response listUsers() {
    // Only reaches here if the principal has ListUsers permission
}

@PUT
@Path("/{id}")
public Response updateUser(
    @PathParam("id")
    @Authorized(extractor = UserExtractor.class, actions = "UpdateUser")
    String userId
) {
    // Only reaches here if the principal can UpdateUser on THIS specific user
    // The user is already loaded and authorized — grab it from the request context
}
```

No role strings in your code. No manual permission checks in your handlers. The API surface *is* your authorization policy.

## Schema-free permissions

Permission sets are plain JSON. No predefined fields, no mandatory structure beyond `allow` and `deny`:

```json
{
  "allow": {
    "ListUsers": "all",
    "UpdateUser": { "department": { "match": "principal" } },
    "ViewReports": { "region": { "in": ["US", "EU"] } }
  },
  "deny": {
    "DeleteUser": "all"
  }
}
```

That's a complete role definition. It says:

- Can list all users
- Can update users, but only those in the same department as the principal
- Can view reports, but only for US and EU regions
- Cannot delete any user, ever

The attribute names (`department`, `region`) are yours. Acorn doesn't know what they mean. It just compares values according to the rules you write. If your domain uses `team` instead of `department`, use `team`. If you scope by `facility_id` and `shift`, use those. No schema to extend, no migrations to run.

## Scope filters

The real power is in scoped permissions. Instead of "can do X" or "can't do X," you express "can do X when these conditions hold":

| Filter | JSON | Meaning |
|--------|------|---------|
| Same attribute | `{"match": "principal"}` | Resource attribute matches principal's same-named attribute |
| Cross attribute | `{"matchPrincipalAttribute": "userId"}` | Resource attribute matches a different principal attribute |
| Fallback chain | `{"matchPrincipalAttributes": ["userId", "email"]}` | Try multiple principal attributes in order |
| Exact value | `{"equals": "active"}` | Resource attribute equals a literal |
| Value list | `{"in": ["US", "EU", "APAC"]}` | Resource attribute is in a set |

All filters in a scope are AND'd. If you specify both `department` and `status`, both must match. Empty scope means no filtering — full access.

## Deny wins

If a permission set has both an allow and a deny for the same action, deny wins. Always. This isn't configurable because it shouldn't be — explicit denials must be respected regardless of what else is granted. A scoped deny only blocks when the scope matches:

```json
{
  "allow": { "UpdateUser": "all" },
  "deny": { "UpdateUser": { "department": { "equals": "Executive" } } }
}
```

This means: can update any user, *except* users in the Executive department. Deny is surgical.

## Isolation policy

Multi-tenant apps need tenant isolation. Single-tenant apps don't. Rather than hardcoding a cross-tenant check, you configure it:

```java
EvaluationPolicy policy = EvaluationPolicy.withIsolation("tenant_id");
```

Now if a resource has a `tenant_id` attribute that differs from the principal's `tenant_id`, access is denied — regardless of what the permission set says. Remove the isolation attribute and the check disappears. Your call.

## Framework support

Acorn is modular. The core evaluation engine has zero framework dependencies. Integration modules adapt it to your stack:

| Module | What it does |
|--------|-------------|
| `acorn-core` | Evaluation engine, permission model, scope filters, caching store |
| `acorn-jaxrs` | JAX-RS `ContainerRequestFilter` integration (Jersey, RESTEasy) |
| `acorn-guice` | Guice DI module with extractor resolution |
| `acorn-spring` | Spring MVC `HandlerInterceptor` with `@EnableAcorn` auto-config |
| `acorn-cdi` | CDI interceptor for Quarkus, WildFly, Payara |

Pick the modules that match your stack. Use the core directly if your framework isn't listed.

## You own the principal

Acorn doesn't authenticate. It doesn't know what a JWT is, doesn't care about OAuth flows, has no opinion on session management. You implement `PrincipalExtractor` — a single method that takes a request and returns a principal. How you get there is your business.

```java
public class JwtPrincipalExtractor implements PrincipalExtractor {
    public Optional<Principal> extract(RequestContext context) {
        return context.header("Authorization")
            .filter(h -> h.startsWith("Bearer "))
            .map(h -> decodeAndValidate(h.substring(7)));
    }
}
```

The principal is an attribute bag. Expose whatever your authorization rules reference — `tenant_id`, `department`, `user_id`, `clearance_level`. The framework never interprets these. It just matches them against scope filters.

## You own the resource

Same story for resources. Implement `ResourceExtractor` — tell Acorn how to find the resource ID in the request, how to load it from storage, and which attributes matter for authorization:

```java
public class DocumentExtractor implements ResourceExtractor<Document> {
    public String resourceType() { return "document"; }

    public Optional<String> extractId(RequestContext context) {
        return context.pathParam("docId");
    }

    public Document load(String id, AttributeSource principal) {
        String tenantId = principal.attribute("tenant_id").orElseThrow();
        return documentRepo.findById(tenantId, id);
    }

    public Attributes attributes(Document doc) {
        return Attributes.builder()
            .with("tenant_id", doc.getTenantId())
            .with("classification", doc.getClassification())
            .with("owner", doc.getCreatedBy())
            .build();
    }
}
```

Register it. Now any endpoint can protect documents with scoped permissions — without the handler knowing authorization exists.

## You own the storage

Permission sets live wherever you want. Implement `PermissionLoader` and wrap it with the built-in `CachingPermissionStore`:

```java
PermissionLoader loader = key -> dynamoDb.getItem(key.get(0), key.get(1))
    .map(item -> PermissionSet.fromJson(item.getString("config")));

PermissionStore store = new CachingPermissionStore(loader, Duration.ofMinutes(5), 10_000);
```

The cache key is derived from your principal's `permissionKey()` — you decide the segments. Multi-tenant apps might use `[tenantId, roleName]`. Single-tenant apps might use `[roleName]`. Your schema, your rules.

## Getting started

```gradle
dependencies {
    implementation("com.ardley.acorn:acorn-core:1.0.0")
    implementation("com.ardley.acorn:acorn-jaxrs:1.0.0")   // or acorn-spring, acorn-cdi
    implementation("com.ardley.acorn:acorn-guice:1.0.0")   // or your DI framework
}
```

1. Define your actions
2. Implement `PrincipalExtractor`
3. Implement `ResourceExtractor` for each protected resource type
4. Implement `PermissionLoader` backed by your database
5. Configure `EvaluationPolicy` with your isolation rules
6. Register the filter/interceptor with your framework
7. Annotate your endpoints

Your handlers stay clean. Your authorization is declarative. Your permission model is yours.

## License

MIT
