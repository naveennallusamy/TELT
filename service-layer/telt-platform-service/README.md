# TELT Platform Service

Spring Boot 4.1 / Java 25 service for the multi-tenant examination platform (identity, tenants, users, organisations).
Base URL: `http://localhost:5050/telt/api` · Swagger UI: `/telt/api/swagger-ui.html`

## Run locally

Requirements: JDK 25, Maven 3.9+, PostgreSQL 16+.

```bash
export DB_URL=jdbc:postgresql://localhost:5432/TELT DB_USERNAME=telt_app DB_PASSWORD=...
# First start only: creates the bootstrap super admin if none exists
export TELT_ADMIN_EMAIL=admin@example.com TELT_ADMIN_MOBILE=9000000000 TELT_ADMIN_PASSWORD='Change!Me123'
./mvnw spring-boot:run
```

The schema is managed by Flyway (`src/main/resources/db/migration`); Hibernate only validates it.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/TELT`, `telt_app`, empty | Database |
| `JWT_PRIVATE_KEY_LOCATION`, `JWT_PUBLIC_KEY_LOCATION` | empty | RS256 key pair as PEM (`file:/path/key.pem`). Empty = ephemeral key pair, dev only (tokens invalid after restart / across replicas) |
| `JWT_ISSUER`, `JWT_ACCESS_TOKEN_TTL`, `JWT_REFRESH_TOKEN_TTL` | `telt-platform`, `15m`, `7d` | Token settings |
| `TELT_ADMIN_USERNAME`, `TELT_ADMIN_EMAIL`, `TELT_ADMIN_MOBILE`, `TELT_ADMIN_PASSWORD` | `teltadmin`, empty | Bootstrap super admin (created only when none exists) |
| `PASSWORD_RESET_URL`, `PASSWORD_RESET_TTL` | `http://localhost:3000/reset-password`, `1h` | Link e-mailed for reset (`?token=` is appended) |
| `MAX_FAILED_LOGINS`, `LOCKOUT_DURATION` | `5`, `15m` | Login lockout |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `MAIL_FROM` | unset | SMTP; when unset, reset e-mails are skipped with a warning |
| `CORS_ALLOWED_ORIGINS` | empty | Comma-separated allowed origins |

Generate a key pair:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem
```

## Account endpoints

| Endpoint | Who | Notes |
|---|---|---|
| `POST /auth/login` | public | E-mail, mobile or username. Locks after `MAX_FAILED_LOGINS` failures |
| `POST /auth/refresh` | public | Rotates the refresh token; reusing an old one revokes the session |
| `POST /auth/logout` / `POST /auth/logout-all` | public (refresh token) / authenticated | Revoke one / all sessions |
| `POST /auth/forgot-password` | public | Always 200 (no account enumeration) |
| `POST /auth/reset-password` | public | Single-use, expiring token; signs out all sessions |
| `POST /auth/change-password` | authenticated | Requires current password; returns fresh tokens, other sessions signed out |
| `GET/PUT /me` | authenticated | Own profile (personal info, addresses) |
| `POST /user/register` | super admin, tenant admin | Users must change password on first login; omit `password` to e-mail a setup link |
| `POST /user/{id}/activate`, `/deactivate`, `/unlock`, `/password-reset` | super admin, tenant admin | Tenant admins only reach users of their own tenant |
| `/tenant/**` | super admin | Tenant CRUD |
| `GET /roles` | admins | Seeded roles: `SUPER_ADMIN`, `TENANT_ADMIN`, `USER` |

Errors use one JSON shape: `{"code","message","correlationId","timestamp","details"}`; send `X-Correlation-Id` to correlate.

## Tests

```bash
./mvnw test   # needs Docker (Testcontainers starts PostgreSQL)
```

## Tenancy and access (Phase 1)

- Users are global; `tenant_memberships` link them to tenants as `TENANT_ADMIN`, `SUB_ADMIN` or `STUDENT`. A student belongs to exactly one tenant (DB trigger); staff can belong to several and use `POST /auth/switch-tenant`.
- Sub-admins are `TENANT_WIDE` or `DEPARTMENT_SCOPED` (default from `/tenant-settings`, per member via `PATCH /members/{userId}`).
- Seeded roles (`GET /roles`) bundle permissions (`GET /permissions`); assign with `POST /members/{userId}/role-assignments` at `TENANT` or `DEPARTMENT` scope (department scope includes sub-departments). Grantors can't grant permissions they don't hold.
- `GET /me/authorization` returns the caller's effective permissions; `/departments`, `/audit-events` manage the department tree and audit log.

## Database roles and row-level security (Phase 1b)

Tenant-owned tables (`departments`, `tenant_memberships`, `role_assignments`, `organization_dtls`, `audit_events`) have PostgreSQL row-level security. Every transaction sets `telt.tenant_id`, `telt.user_id` and `telt.bypass_rls` with `set_config(..., true)` (`RlsJpaTransactionManager`), taken from the access token. Super admins, login/refresh/password-reset flows and internal authorization lookups run with bypass. With no tenant context, these tables return no rows.

RLS does not apply to superusers, `BYPASSRLS` roles or table owners, so use two roles:

```sql
CREATE ROLE telt_owner LOGIN PASSWORD '...';   -- owns the schema, runs Flyway
CREATE ROLE telt_app   LOGIN PASSWORD '...';   -- used by the application at runtime
```

| Variable | Purpose |
| --- | --- |
| `DB_MIGRATION_USERNAME` / `DB_MIGRATION_PASSWORD` | Flyway (schema owner). Defaults to `DB_USERNAME`/`DB_PASSWORD`. |
| `DB_USERNAME` / `DB_PASSWORD` | Application role; `V4` grants it DML (no `UPDATE`/`DELETE` on `audit_events`). |
| `RLS_REQUIRE_ENFORCED` | `true` fails startup if the application role bypasses RLS (otherwise a warning is logged). |
| `AUTHZ_INVALIDATION_ENABLED` | `LISTEN telt_authz` for cross-instance permission-cache eviction (default `true`). |

Triggers on memberships, role assignments, departments and tenant settings call `pg_notify('telt_authz', ...)`. Each instance listens on a dedicated connection and evicts the affected Caffeine entries. The 5-minute TTL still caps how stale a cached entry can get if a notification is missed.

## Question banks (Phase 2a)

- `GET/POST /question-banks`, `PUT /question-banks/{bankId}`: a bank belongs to the tenant and optionally to a department. Department-scoped staff can only reach banks in their department tree; banks with no department need tenant-wide access.
- `GET/POST /question-categories`: a subject/topic tree for the tenant.
- `POST /question-banks/{bankId}/questions`: create a question (`SINGLE_CHOICE`, `MULTIPLE_SELECT`, `NUMERICAL`, `DESCRIPTIVE`). It starts as version 1 in `DRAFT`. Content and answer key are checked against the question type.
- Versions: `PUT /questions/{id}/versions/{no}` edits a draft. `POST /questions/{id}/versions` starts a new draft. `.../submit` moves it to `IN_REVIEW`. `.../review` with `APPROVE` or `REJECT` needs `QUESTION_REVIEW`; when the tenant's `separationOfDuties` is on, authors can't review their own versions. `POST /questions/{id}/retire` needs `QUESTION_PUBLISH`.
- A trigger blocks edits to any version that has left `DRAFT` and only lets status move forward. The app role can't delete versions or reviews.
- `GET /questions?bankId&categoryId&type&status&difficulty&tag&q&page&size`: paged search over each question's latest version; `q` is PostgreSQL full-text search.
- Answer keys and explanations are only returned to callers with `ANSWER_KEY_VIEW` or `QUESTION_WRITE` on the bank.
