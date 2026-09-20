# ADR-024: Explicit Docker Compose Volume Naming Across All Microservices

**Status:** Accepted  
**Date:** 2026-09-20  
**Deciders:** Project owner  

## Context

Docker Compose (and Podman Compose) defaults to naming volumes by prefixing the parent directory name, e.g. `<directory>_<volume_name>`. Because microservices and containerized projects frequently place their `docker-compose.yml` files in a generic subfolder named `docker/` or `infrastructure/docker/`, Compose automatically names volumes `docker_postgres_data`, `docker_mongo_data`, `docker_redis_data`, etc.

When multiple distinct development projects exist on the same host (e.g. `echtgut` and `lmdb.dev`) and store their compose configurations inside `docker/`, Compose mounts the exact same volume across projects. As a result:
- Database data, credentials, and schemas from another application are silently attached.
- Microservices crash-loop with `password authentication failed` or missing schemas.
- Data corruption occurs when two separate systems mutate the same underlying data store.

## Decision

Every named volume defined in any `docker-compose*.yml` file across this repository MUST explicitly set a project-scoped `name:` attribute using the `lmdb_` prefix (e.g., `name: lmdb_postgres_data`, `name: lmdb_mongo_data`, `name: lmdb_redis_data`).

No volume may rely on implicit folder-based naming.

## Options Considered

### Option A: Rely on default folder-based naming
- **Risk:** High collision risk on developer machines running multiple projects with standard folder structures (`docker/`).
- **Verdict:** Rejected.

### Option B: Require setting `-p / --project-name` CLI flag on every compose invocation
- **Risk:** Error-prone. Developers or automated scripts omitting `-p` default back to directory-based names.
- **Verdict:** Rejected.

### Option C: Explicit `name: lmdb_*` attribute on all volume declarations (Chosen)
- **Benefit:** Deterministic, self-contained, and enforced directly inside the compose files without relying on CLI parameters.
- **Verdict:** Accepted.

## Consequences

- Prevents cross-project volume collisions and unexpected data reuse across developer workstations.
- Enforced by automated test script `infrastructure/scripts/test-compose-volume-names.sh`.
- All compose files (`docker-compose.yml`, `docker-compose.elk.yml`, etc.) follow this explicit naming standard.
