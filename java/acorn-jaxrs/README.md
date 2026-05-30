# acorn-jaxrs

JAX-RS integration for Acorn. Works with Jersey, RESTEasy, and any Jakarta REST implementation.

## Dependencies

```gradle
implementation("com.ardley.acorn:acorn-core:1.0.0")
implementation("com.ardley.acorn:acorn-jaxrs:1.0.0")
```

## How it works

The `AuthorizationFilter` is a `ContainerRequestFilter` at `@Priority(AUTHORIZATION)`. It runs after your authentication filter and before your resource methods. It reads `@RequiresActions` and `@Authorized` annotations and enforces permissions declaratively.

## Setup

Register the filter with your JAX-RS application. You need to provide:

- A `PrincipalExtractor` (resolves the authenticated user from each request)
- A `PermissionStore` (loads permission sets for roles)
- An `EvaluationPolicy` (configures isolation rules)
- An `ExtractorResolver` (locates resource extractors — use `acorn-guice` or implement your own)
- An `ActionRegistry` (registered actions)

If using Guice, see `acorn-guice`. Otherwise, construct the filter directly:

```java
ActionRegistry registry = new ActionRegistry();
registry.registerAll(DocumentActions.class);
registry.registerAll(UserActions.class);

AuthorizationFilter filter = new AuthorizationFilter(
    new JwtPrincipalExtractor(jwtDecoder),
    new CachingPermissionStore(loader, Duration.ofMinutes(5), 10_000),
    EvaluationPolicy.withIsolation("tenant_id"),
    clazz -> extractorInstances.get(clazz),  // your ExtractorResolver
    registry
);

// Register with Jersey
resourceConfig.register(filter);
```

## Annotating endpoints

### Gate check — no resource needed

```java
@GET
@Path("/documents")
@RequiresActions("ListDocuments")
public Response listDocuments() {
    // Reaches here only if the principal has ListDocuments permission
    return Response.ok(documentService.listAll()).build();
}
```

### Resource-level check — scoped permissions

```java
@PUT
@Path("/documents/{docId}")
public Response updateDocument(
    @PathParam("docId")
    @Authorized(extractor = DocumentExtractor.class, actions = "EditDocument")
    String docId
) {
    // Reaches here only if the principal can EditDocument on THIS document
    Document doc = (Document) request.getProperty("acorn.resource.document");
    // doc is already loaded and authorized
    return Response.ok(documentService.update(doc)).build();
}
```

### Multiple actions (AND semantics)

```java
@DELETE
@Path("/documents/{docId}")
@RequiresActions({"ViewDocument", "DeleteDocument"})
public Response deleteDocument(
    @PathParam("docId")
    @Authorized(extractor = DocumentExtractor.class, actions = "DeleteDocument")
    String docId
) { ... }
```

## Exception handling

The filter throws exceptions — it never constructs responses directly. Register exception mappers to control your response format:

```java
@Provider
public class AuthzExceptionMapper implements ExceptionMapper<AuthorizationDeniedException> {
    @Override
    public Response toResponse(AuthorizationDeniedException e) {
        return Response.status(403)
            .entity(new ProblemDetail(
                "forbidden",
                String.format("Action \"%s\" on %s \"%s\" is not permitted",
                    e.actionName(), e.resourceType(), e.resourceId()),
                e.getMessage()
            ))
            .build();
    }
}

@Provider
public class AuthnExceptionMapper implements ExceptionMapper<AuthenticationRequiredException> {
    @Override
    public Response toResponse(AuthenticationRequiredException e) {
        return Response.status(401)
            .entity(new ProblemDetail("unauthorized", "Authentication required", null))
            .build();
    }
}

@Provider
public class NotFoundExceptionMapper implements ExceptionMapper<ResourceNotFoundException> {
    @Override
    public Response toResponse(ResourceNotFoundException e) {
        return Response.status(404)
            .entity(new ProblemDetail("not_found", e.getMessage(), null))
            .build();
    }
}
```

## Accessing the authorized resource

After authorization passes, the loaded resource is available in request properties:

```java
// Key format: "acorn.resource." + extractor.resourceType()
Document doc = (Document) containerRequest.getProperty("acorn.resource.document");
User user = (User) containerRequest.getProperty("acorn.resource.user");
```

The principal is also stored:

```java
Principal principal = (Principal) containerRequest.getProperty("acorn.principal");
```
