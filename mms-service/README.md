# MMS Service

Spring Boot API for MMS authentication, members, RBAC, governance, entitlements, AI model catalog, invitations, localization, email verification, and TOTP 2FA.

This module is built from the repository root because it inherits from the standalone parent POM:

```bash
mvn -pl mms-service spring-boot:run
mvn -pl mms-service test
mvn -pl mms-service verify
```

Runtime configuration is supplied through environment variables. At minimum, configure:

- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `JWT_SECRET`
- `ADMIN_PASSWORD`

PostgreSQL schema changes are owned by Flyway under `src/main/resources/db/migration`. Hibernate runs with `ddl-auto=validate`.

The integration suite uses Testcontainers PostgreSQL, so Docker must be running for the full test suite.

For the complete standalone stack, use `docker compose up --build` from the repository root.
