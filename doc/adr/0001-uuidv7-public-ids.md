# ADR-0001: UUIDv7 Public Identifiers for External Resources

## Status
Approved

## Context
Exposing database-generated sequential auto-incrementing identifiers (bigint) via public APIs and JWT claims leaks database size, patterns of insertion, and opens the system to ID harvesting attacks.
To solve this, we migrated all external resource identities to the Dual-Key Pattern:
- **Internal Key**: Sequential `bigint` (identity PK) is retained for fast foreign key indexing, clustering performance, and internal joins.
- **External Key**: Time-ordered UUID (UUIDv7) is introduced as the `publicId`, exposed in DTO responses, controllers paths, and JWT tokens.

## Decisions

### D1: Time-Ordered Generator (RFC 9562)
Hibernate 7.1 (active in Spring Boot 4.1.0) does not support UUIDv7 natively (it is introduced in 7.2 via `UuidVersion7Strategy`).
We implemented a custom generator `UuidV7Generator` that wraps the `com.github.f4b6a3:uuid-creator` library. This abstracts the generation logic behind a custom Hibernate strategy, facilitating a seamless future upgrade to native Hibernate 7.2.

### D2: Mapping Boundaries
We avoided using complex mapping frameworks. Mappings between database entities and DTO response layers are handled explicitly via static factory methods (e.g., `EvidenciaResponse.from(evidencia)`) to maintain compile-time type-safety and architectural clarity.

### D3: JWT Security Subject (`sub`)
The subject of both Access and Refresh JWT tokens is mapped to the user's `publicId` (UUID) in string format. To keep the filter pipeline stateless, the security filter sets the `UserPrincipal` with the UUID representation. Any transaction requiring a user foreign key mapping translates this public UUID to the internal `bigint` id at the service boundary.

### D4: Liquibase Migration and Seeds
For existing tables, migrations add the nullable `public_id` column, backfill existing records using the native database `gen_random_uuid()` function, and then alter the column to be `NOT NULL` and `UNIQUE`. Unique indexes are created explicitly. Rollbacks are provided for all changesets.

### D5: Test Isolation
Integration tests targeting database layers (`@DataJpaTest`) use Testcontainers Postgres to ensure unique constraints and UUIDv7 behaviors are validated against a real engine (since H2 does not support `gen_random_uuid()`). The generic smoke test `ElectoralApiApplicationTests` is `@Disabled` to prevent failures in environments lacking a global Postgres instance.

## Consequences
- **Security**: Complete prevention of resource enumeration attacks.
- **Performance**: High insertion performance is maintained since UUIDv7 is chronologically sortable and does not cause index fragmentation.
- **Maintainability**: Low-friction upgrades to future versions of Spring Boot and Hibernate.
