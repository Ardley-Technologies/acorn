package com.ardley.acorn.roles;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.*;

class RoleInitializationServiceTest {

    static final String MANIFEST_JSON = """
        {
            "version": 1,
            "roles": [
                {
                    "roleId": "admin",
                    "roleName": "Admin",
                    "description": "Full access",
                    "systemRole": true,
                    "assignableRoles": ["editor", "viewer"],
                    "configuration": {"allow": "all"}
                },
                {
                    "roleId": "editor",
                    "roleName": "Editor",
                    "description": "Edit access",
                    "systemRole": true,
                    "assignableRoles": ["viewer"],
                    "configuration": {"allow": {"ListUsers": "all"}, "deny": {"DeleteUser": "all"}}
                },
                {
                    "roleId": "viewer",
                    "roleName": "Viewer",
                    "description": "Read-only",
                    "systemRole": true,
                    "assignableRoles": [],
                    "configuration": {"allow": {"ListUsers": "all"}}
                }
            ]
        }
        """;

    @Test
    void seedsAllRolesForNewTenant() {
        var manifest = RoleManifest.fromJson(MANIFEST_JSON);
        var service = new RoleInitializationService(manifest);
        var repo = new InMemoryRepo();

        var result = service.initializeIfNeeded("t-1", 0, repo);

        assertThat(result.initialized()).isTrue();
        assertThat(result.rolesCreated()).isEqualTo(3);
        assertThat(repo.records).hasSize(3);
        assertThat(repo.records.stream().map(RoleRecord::roleId).toList())
                .containsExactlyInAnyOrder("admin", "editor", "viewer");
    }

    @Test
    void skipsIfAlreadyAtCurrentVersion() {
        var manifest = RoleManifest.fromJson(MANIFEST_JSON);
        var service = new RoleInitializationService(manifest);
        var repo = new InMemoryRepo();

        var result = service.initializeIfNeeded("t-1", 1, repo);

        assertThat(result.initialized()).isFalse();
        assertThat(result.rolesCreated()).isEqualTo(0);
        assertThat(repo.records).isEmpty();
    }

    @Test
    void doesNotOverwriteExistingRoles() {
        var manifest = RoleManifest.fromJson(MANIFEST_JSON);
        var service = new RoleInitializationService(manifest);
        var repo = new InMemoryRepo();
        repo.records.add(RoleRecord.builder()
                .tenantId("t-1").roleId("admin").roleName("Admin")
                .configuration("{}").version(0).build());

        var result = service.initializeIfNeeded("t-1", 0, repo);

        assertThat(result.initialized()).isTrue();
        assertThat(result.rolesCreated()).isEqualTo(2);
        assertThat(repo.records).hasSize(3);
    }

    @Test
    void currentVersionReturnsManifestVersion() {
        var manifest = RoleManifest.fromJson(MANIFEST_JSON);
        var service = new RoleInitializationService(manifest);
        assertThat(service.currentVersion()).isEqualTo(1);
    }

    @Test
    void manifestParsesRoleDefinitions() {
        var manifest = RoleManifest.fromJson(MANIFEST_JSON);
        assertThat(manifest.roles()).hasSize(3);
        assertThat(manifest.roles().get(0).roleId()).isEqualTo("admin");
        assertThat(manifest.roles().get(0).configurationJson()).contains("\"all\"");
    }

    @Test
    void repositoryPermissionLoaderLoadsByKey() {
        var repo = new InMemoryRepo();
        repo.records.add(RoleRecord.builder()
                .tenantId("t-1").roleId("editor").roleName("Editor")
                .configuration("{\"allow\": {\"ListUsers\": \"all\"}}")
                .version(1).build());

        var loader = new RepositoryPermissionLoader(repo);
        var perms = loader.load(List.of("t-1", "editor"));

        assertThat(perms).isPresent();
        assertThat(perms.get().hasAllowFor("ListUsers")).isTrue();
    }

    @Test
    void repositoryPermissionLoaderReturnsEmptyForMissing() {
        var repo = new InMemoryRepo();
        var loader = new RepositoryPermissionLoader(repo);

        assertThat(loader.load(List.of("t-1", "nonexistent"))).isEmpty();
        assertThat(loader.load(List.of())).isEmpty();
        assertThat(loader.load(List.of("t-1"))).isEmpty();
    }

    // ---- In-memory test implementation ----

    static class InMemoryRepo implements RoleConfigurationRepository {
        final List<RoleRecord> records = new ArrayList<>();

        @Override
        public void save(RoleRecord record) {
            records.add(record);
        }

        @Override
        public Optional<RoleRecord> findById(String tenantId, String roleId) {
            return records.stream()
                    .filter(r -> r.tenantId().equals(tenantId) && r.roleId().equals(roleId))
                    .findFirst();
        }

        @Override
        public List<RoleRecord> listByTenant(String tenantId) {
            return records.stream()
                    .filter(r -> r.tenantId().equals(tenantId))
                    .toList();
        }

        @Override
        public void delete(String tenantId, String roleId) {
            records.removeIf(r -> r.tenantId().equals(tenantId) && r.roleId().equals(roleId));
        }

        @Override
        public boolean exists(String tenantId, String roleId) {
            return records.stream()
                    .anyMatch(r -> r.tenantId().equals(tenantId) && r.roleId().equals(roleId));
        }
    }
}
