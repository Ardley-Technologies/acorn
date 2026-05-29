package com.ardley.acorn.roles;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for role configuration CRUD operations.
 *
 * <p>Users implement this for their specific persistence layer (DynamoDB, Postgres,
 * MongoDB, etc.). The framework never calls this directly during authorization —
 * it is used by {@link RoleInitializationService} for seeding and by application
 * code for role management APIs.
 */
public interface RoleConfigurationRepository {

    void save(RoleRecord record);

    Optional<RoleRecord> findById(String tenantId, String roleId);

    List<RoleRecord> listByTenant(String tenantId);

    void delete(String tenantId, String roleId);

    boolean exists(String tenantId, String roleId);
}
