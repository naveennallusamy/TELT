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
