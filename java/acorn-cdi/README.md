# acorn-cdi

CDI integration for Acorn. Works with Quarkus, WildFly, Payara, Open Liberty, and any Jakarta CDI container.

## Dependencies

```gradle
implementation("com.ardley.acorn:acorn-core:1.0.0")
implementation("com.ardley.acorn:acorn-cdi:1.0.0")
```

## Setup

### 1. Provide CDI beans

```java
@ApplicationScoped
public class AcornProducer {

    @Produces @ApplicationScoped
    public EvaluationPolicy policy() {
        return EvaluationPolicy.withIsolation("tenant_id");
    }

    @Produces @ApplicationScoped
    public ActionRegistry actionRegistry() {
        ActionRegistry registry = new ActionRegistry();
        registry.registerAll(DocumentActions.class);
        registry.registerAll(UserActions.class);
        return registry;
    }

    @Produces @ApplicationScoped
    public PermissionStore permissionStore(RoleRepository roleRepo) {
        PermissionLoader loader = key -> {
            String tenantId = key.get(0);
            String roleId = key.get(1);
            return roleRepo.findConfig(tenantId, roleId)
                .map(PermissionSet::fromJson);
        };
        return new CachingPermissionStore(loader, Duration.ofMinutes(5), 10_000);
    }

    @Produces @ApplicationScoped
    public CdiExtractorResolver extractorResolver(BeanManager bm) {
        return new CdiExtractorResolver(bm);
    }
}
```

### 2. Implement PrincipalExtractor

```java
@ApplicationScoped
public class JwtPrincipalExtractor implements PrincipalExtractor {

    @Inject JwtDecoder decoder;

    @Override
    public Optional<Principal> extract(RequestContext context) {
        return context.header("Authorization")
            .filter(h -> h.startsWith("Bearer "))
            .map(h -> decoder.decode(h.substring(7)))
            .map(JwtPrincipal::new);
    }
}
```

### 3. Implement resource extractors as CDI beans

```java
@ApplicationScoped
public class DocumentExtractor implements ResourceExtractor<Document> {

    @Inject DocumentRepository repo;

    @Override public String resourceType() { return "document"; }

    @Override public Optional<String> extractId(RequestContext context) {
        return context.pathParam("docId");
    }

    @Override public Document load(String id, AttributeSource principal) {
        String tenantId = principal.attribute("tenant_id").orElseThrow();
        return repo.findById(tenantId, id);
    }

    @Override public Attributes attributes(Document doc) {
        return Attributes.builder()
            .with("tenant_id", doc.getTenantId())
            .with("department", doc.getDepartment())
            .with("owner", doc.getCreatedBy())
            .build();
    }
}
```

### 4. Enable the interceptor in beans.xml

```xml
<beans xmlns="https://jakarta.ee/xml/ns/jakartaee"
       version="4.0" bean-discovery-mode="all">
    <interceptors>
        <class>com.ardley.acorn.cdi.AuthorizationInterceptor</class>
    </interceptors>
</beans>
```

## Using in resources

Apply `@Secured` alongside your authorization annotations:

```java
@Path("/documents")
@ApplicationScoped
public class DocumentResource {

    @GET
    @Secured
    @RequiresActions("ListDocuments")
    public List<Document> list() {
        return documentService.listAll();
    }

    @PUT
    @Path("/{docId}")
    @Secured
    public Document update(
        @PathParam("docId")
        @Authorized(extractor = DocumentExtractor.class, actions = "EditDocument")
        String docId,
        UpdateRequest body
    ) {
        Document doc = (Document) httpRequest.getAttribute("acorn.resource.document");
        return documentService.update(doc, body);
    }
}
```

## Path parameter setup

CDI doesn't have a built-in path variable abstraction like Spring's `URI_TEMPLATE_VARIABLES_ATTRIBUTE`. The `ServletRequestContext` reads path params from request attributes with the prefix `acorn.pathparam.`.

If you're using JAX-RS alongside CDI (common in Quarkus/WildFly), add a filter that populates these attributes from `@PathParam` values:

```java
@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class PathParamPopulator implements ContainerRequestFilter {
    @Context ResourceInfo resourceInfo;
    @Context UriInfo uriInfo;

    @Override
    public void filter(ContainerRequestContext ctx) {
        uriInfo.getPathParameters().forEach((name, values) -> {
            if (!values.isEmpty()) {
                ((HttpServletRequest) ctx.getProperty("jakarta.servlet.http.HttpServletRequest"))
                    .setAttribute("acorn.pathparam." + name, values.get(0));
            }
        });
    }
}
```

Or call `ServletRequestContext.setPathParam(request, name, value)` from your routing layer.

## Exception handling

Acorn CDI throws:

- `AcornCdiAuthenticationException` → map to 401
- `AcornCdiAccessDeniedException` → map to 403
- `AcornCdiResourceNotFoundException` → map to 404

Handle with JAX-RS exception mappers:

```java
@Provider
public class AccessDeniedMapper implements ExceptionMapper<AcornCdiAccessDeniedException> {
    @Override
    public Response toResponse(AcornCdiAccessDeniedException e) {
        return Response.status(403)
            .entity(Map.of(
                "error", "forbidden",
                "action", e.actionName(),
                "resource", e.resourceType(),
                "detail", e.getMessage()
            ))
            .build();
    }
}
```

## How it works

- `@Secured` is a CDI `@InterceptorBinding` that activates `AuthorizationInterceptor`
- The interceptor is an `@AroundInvoke` method — it runs before the target method
- `HttpServletRequest` is injected by the container (standard in servlet-based CDI environments)
- Resource extractors are resolved from the `BeanManager` by class
- Authorized resources are stored in `httpRequest.setAttribute("acorn.resource.<type>", resource)`
- If authorization fails, the exception propagates and the target method never executes
