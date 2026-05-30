# acorn-roles

Role management for multi-tenant applications. Provides a repository interface you implement for your persistence layer, a seeding service for default roles, and a bridge that connects your role store to Acorn's permission loading.

The pattern: define your default roles in a JSON manifest, implement a repository for your database (DynamoDB, Postgres, Mongo, JDBC — your call), and this module handles initialization logic and wires it into the authorization engine.

## Getting started

```gradle
dependencies {
    implementation("com.ardley.acorn:acorn-roles:0.1.0")
}
```

## Define your default roles

Create a manifest — a versioned list of roles to seed for new tenants. Put it on your classpath (e.g., `src/main/resources/default-roles.json`):

```json
{
  "version": 1,
  "roles": [
    {
      "roleId": "admin",
      "roleName": "Admin",
      "description": "Full access to all resources",
      "systemRole": true,
      "assignableRoles": ["editor", "viewer"],
      "configuration": { "allow": "all" }
    },
    {
      "roleId": "editor",
      "roleName": "Editor",
      "description": "Create and modify, but can't delete or manage users",
      "systemRole": true,
      "assignableRoles": ["viewer"],
      "configuration": {
        "allow": { "ListUsers": "all", "UpdateUser": "all", "CreateUser": "all" },
        "deny": { "DeleteUser": "all", "ManageRoles": "all" }
      }
    },
    {
      "roleId": "viewer",
      "roleName": "Viewer",
      "description": "Read-only access",
      "systemRole": true,
      "assignableRoles": [],
      "configuration": {
        "allow": { "ListUsers": "all", "ViewUser": "all" }
      }
    }
  ]
}
```

The `configuration` field is a standard Acorn permission set. Same format everywhere — same JSON your evaluator reads at request time.

## Implement the repository

This is the only thing you write. Five methods, all straightforward:

```java
public class JdbcRoleRepository implements RoleConfigurationRepository {

    @Override
    public void save(RoleRecord record) {
        // INSERT or UPSERT into your roles table
    }

    @Override
    public Optional<RoleRecord> findById(String tenantId, String roleId) {
        // SELECT WHERE tenant_id = ? AND role_id = ?
    }

    @Override
    public List<RoleRecord> listByTenant(String tenantId) {
        // SELECT WHERE tenant_id = ?
    }

    @Override
    public void delete(String tenantId, String roleId) {
        // DELETE WHERE tenant_id = ? AND role_id = ?
    }

    @Override
    public boolean exists(String tenantId, String roleId) {
        // SELECT 1 WHERE tenant_id = ? AND role_id = ?
    }
}
```

## Seed roles for new tenants

```java
RoleManifest manifest = RoleManifest.fromResource("default-roles.json");
RoleInitializationService seeder = new RoleInitializationService(manifest);

// Call when creating a tenant, or on first access
InitializationResult result = seeder.initializeIfNeeded("tenant-abc", 0, repository);
if (result.initialized()) {
    log.info("Seeded {} roles for tenant", result.rolesCreated());
}
```

The service is safe to call repeatedly:
- If the tenant's version is already at or above the manifest version, it does nothing
- It never overwrites existing roles — if `admin` already exists for this tenant, it's left alone
- Only missing roles are created

This lets you bump the manifest version, add new roles, and re-run. Existing customized roles stay intact, new system roles get added.

## Loading the manifest

Three options depending on your setup:

```java
// From classpath resource (most common — embedded in your JAR)
RoleManifest manifest = RoleManifest.fromResource("default-roles.json");

// From a file path (external config)
RoleManifest manifest = RoleManifest.fromFile(Path.of("/etc/myapp/roles.json"));

// From a string (fetched from S3, config service, etc.)
RoleManifest manifest = RoleManifest.fromJson(jsonString);
```

## Connect to authorization

The `RepositoryPermissionLoader` bridges your repository to Acorn's `PermissionLoader` interface. Wrap it with `CachingPermissionStore` and you're done:

```java
PermissionLoader loader = new RepositoryPermissionLoader(repository);
PermissionStore store = new CachingPermissionStore(loader, Duration.ofMinutes(5), 10_000);
```

Now pass `store` to your framework filter/interceptor. When a request comes in, Acorn calls `store.getPermissionSet(List.of("tenant-abc", "editor"))` — the loader fetches the role configuration from your database, parses the permission JSON, and the cache keeps it hot for 5 minutes.

Your `Principal` implementation just needs to return the right key:

```java
@Override
public List<String> permissionKey() {
    return List.of(tenantId, roleName);
}
```

## The RoleRecord

A simple data class representing what lives in your database:

| Field | Type | Purpose |
|-------|------|---------|
| `tenantId` | String | Partition key |
| `roleId` | String | Unique within tenant |
| `roleName` | String | Human-readable display name |
| `description` | String | What this role is for |
| `systemRole` | boolean | Can't be deleted by customers |
| `assignableRoles` | List&lt;String&gt; | Which roles this role can grant to others |
| `configuration` | String | Raw permission JSON |
| `version` | int | Manifest version this was seeded from |

Built with a standard builder:

```java
RoleRecord record = RoleRecord.builder()
    .tenantId("tenant-abc")
    .roleId("custom-analyst")
    .roleName("Analyst")
    .description("Can view reports and export data")
    .systemRole(false)
    .assignableRoles(List.of())
    .configuration("{\"allow\": {\"ViewReports\": \"all\", \"ExportData\": \"all\"}}")
    .version(1)
    .build();
```

## License

MIT
