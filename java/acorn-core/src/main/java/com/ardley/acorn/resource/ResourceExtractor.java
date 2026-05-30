package com.ardley.acorn.resource;

import com.ardley.acorn.attribute.AttributeSource;
import com.ardley.acorn.attribute.Attributes;
import com.ardley.acorn.context.RequestContext;

import java.util.Optional;

/**
 * Loads a resource from storage and extracts its authorization-relevant attributes.
 *
 * <p>Implementations are provided by the application for each resource type that
 * requires authorization. The framework uses extractors to:
 * <ol>
 *   <li>Resolve a resource identifier from the incoming {@link RequestContext}</li>
 *   <li>Load the resource instance from persistent storage</li>
 *   <li>Extract an attribute map for scope filter evaluation</li>
 * </ol>
 *
 * <p>The principal is supplied during loading so implementations can scope their
 * storage queries as needed (e.g., by tenant, by organization, or not at all).
 * The framework imposes no particular scoping strategy.
 *
 * <p>Example:
 * <pre>{@code
 * public class UserExtractor implements ResourceExtractor<User> {
 *
 *     public String resourceType() { return "user"; }
 *
 *     public Optional<String> extractId(RequestContext context) {
 *         return context.pathParam("id");
 *     }
 *
 *     public User load(String resourceId, AttributeSource principal) {
 *         String tenantId = principal.attribute("tenant_id").orElseThrow();
 *         return userRepo.findById(tenantId, resourceId);
 *     }
 *
 *     public Attributes attributes(User user) {
 *         return Attributes.builder()
 *             .with("tenant_id", user.getTenantId())
 *             .with("department", user.getDepartment())
 *             .with("assigned_to", user.getAssignedTo())
 *             .build();
 *     }
 * }
 * }</pre>
 *
 * @param <R> the domain resource type this extractor handles
 */
public interface ResourceExtractor<R> {

    /**
     * A human-readable name for the resource type. Used in error messages, logging,
     * and as the property key when storing authorized resources in the request context.
     */
    String resourceType();

    /**
     * Extracts the resource identifier from the incoming request.
     *
     * <p>Implementations decide where the ID lives — path parameters, query parameters,
     * headers, or any combination. Returns empty if the identifier cannot be found.
     *
     * @param context the framework-agnostic request context
     * @return the extracted resource identifier, or empty
     */
    Optional<String> extractId(RequestContext context);

    /**
     * Loads the resource from persistent storage.
     *
     * <p>The principal is provided as an attribute source so implementations can
     * use it for query scoping. Returns {@code null} if the resource does not exist.
     *
     * @param resourceId the identifier resolved from the request
     * @param principal the authenticated principal's attributes
     * @return the loaded resource, or null if not found
     */
    R load(String resourceId, AttributeSource principal);

    /**
     * Extracts authorization-relevant attributes from a loaded resource instance.
     *
     * <p>The returned attributes are compared against scope filters during evaluation.
     * Only include attributes that are referenced by your permission configurations.
     *
     * @param resource the loaded resource
     * @return the attribute set for scope evaluation
     */
    Attributes attributes(R resource);
}
