# acorn-spring

Spring MVC integration for Acorn. Provides a `HandlerInterceptor` with `@EnableAcorn` auto-configuration.

## Dependencies

```gradle
implementation("com.ardley.acorn:acorn-core:1.0.0")
implementation("com.ardley.acorn:acorn-spring:1.0.0")
```

## Setup

Enable Acorn and provide your beans:

```java
@Configuration
@EnableAcorn
public class SecurityConfig {

    @Bean
    public EvaluationPolicy policy() {
        return EvaluationPolicy.withIsolation("tenant_id");
    }

    @Bean
    public PrincipalExtractor principalExtractor(JwtDecoder decoder) {
        return new JwtPrincipalExtractor(decoder);
    }

    @Bean
    public PermissionStore permissionStore(RoleRepository roleRepo) {
        PermissionLoader loader = key -> {
            String tenantId = key.get(0);
            String roleId = key.get(1);
            return roleRepo.findConfig(tenantId, roleId)
                .map(PermissionSet::fromJson);
        };
        return new CachingPermissionStore(loader, Duration.ofMinutes(5), 10_000);
    }

    @Bean
    public ActionRegistry actionRegistry() {
        ActionRegistry registry = new ActionRegistry();
        registry.registerAll(DocumentActions.class);
        registry.registerAll(UserActions.class);
        return registry;
    }
}
```

Register your resource extractors as Spring beans:

```java
@Component
public class DocumentExtractor implements ResourceExtractor<Document> {
    private final DocumentRepository repo;

    public DocumentExtractor(DocumentRepository repo) {
        this.repo = repo;
    }

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

## Using in controllers

```java
@RestController
@RequestMapping("/documents")
public class DocumentController {

    @GetMapping
    @RequiresActions("ListDocuments")
    public List<DocumentDto> list() {
        return documentService.listAll();
    }

    @PutMapping("/{docId}")
    public DocumentDto update(
        @PathVariable("docId")
        @Authorized(extractor = DocumentExtractor.class, actions = "EditDocument")
        String docId,
        @RequestBody UpdateDocumentRequest body
    ) {
        Document doc = (Document) request.getAttribute("acorn.resource.document");
        return documentService.update(doc, body);
    }

    @DeleteMapping("/{docId}")
    @RequiresActions("DeleteDocument")
    public void delete(
        @PathVariable("docId")
        @Authorized(extractor = DocumentExtractor.class, actions = "DeleteDocument")
        String docId
    ) {
        documentService.delete(docId);
    }
}
```

## Exception handling

Handle Acorn exceptions with `@ControllerAdvice`:

```java
@ControllerAdvice
public class AcornExceptionHandler {

    @ExceptionHandler(AcornAccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleDenied(AcornAccessDeniedException e) {
        ProblemDetail problem = ProblemDetail.forStatus(403);
        problem.setTitle("Access Denied");
        problem.setDetail(e.getMessage());
        problem.setProperty("action", e.actionName());
        problem.setProperty("resourceType", e.resourceType());
        problem.setProperty("resourceId", e.resourceId());
        return ResponseEntity.status(403).body(problem);
    }

    @ExceptionHandler(AcornAuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuth(AcornAuthenticationException e) {
        ProblemDetail problem = ProblemDetail.forStatus(401);
        problem.setTitle("Authentication Required");
        return ResponseEntity.status(401).body(problem);
    }

    @ExceptionHandler(AcornResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(AcornResourceNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatus(404);
        problem.setTitle("Not Found");
        problem.setDetail(e.getMessage());
        return ResponseEntity.status(404).body(problem);
    }
}
```

## How it works

- `@EnableAcorn` imports `AcornSpringConfiguration` which registers the `AcornInterceptor` with Spring MVC
- The interceptor runs before every handler method
- Path variables are resolved from Spring's `URI_TEMPLATE_VARIABLES_ATTRIBUTE`
- Resource extractors are resolved from the `ApplicationContext` by class
- The authorized resource is stored in `request.getAttribute("acorn.resource.<type>")`
- Non-`HandlerMethod` requests (static resources, etc.) pass through without checks
