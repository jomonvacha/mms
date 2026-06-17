# Member Management System

Standalone MMS monorepo containing:

- `mms-service`: Spring Boot API for authentication, members, roles, governance, entitlements, models, invitations, localization, and 2FA.
- `mms-ui`: React and TypeScript administration UI.
- `docker-compose.yml`: local PostgreSQL, API, and UI stack.

## Requirements

- Java 25
- Maven 3.9+
- Node.js 20+ and npm
- Docker with Compose v2 for the full local stack

## Quick Start

```bash
cp .env.example .env
# Replace JWT_SECRET and ADMIN_PASSWORD in .env with strong values.
docker compose up --build -d
```

Open:

- UI: http://localhost:3001
- API: http://localhost:8081
- Health: http://localhost:8081/actuator/health
- Swagger UI: http://localhost:8081/swagger-ui.html

## Local Development

The fastest path is the start/stop scripts. They load `.env`, bring up a local
PostgreSQL container when `DATABASE_URL` points at localhost, build and launch
`mms-service` (port `8081`), then start the `mms-ui` Vite dev server (port
`3001`). Each process is health-checked and backgrounded with a PID file under
`logs/`.

```bash
cp .env.example .env
# Set a strong JWT_SECRET (openssl rand -base64 64) and ADMIN_PASSWORD.

make start     # or: ./start.sh
make stop      # or: ./stop.sh
```

- UI: http://localhost:3001
- API: http://localhost:8081
- Logs: `tail -f logs/*.log`

`make stop` leaves the PostgreSQL container running; stop it with
`docker compose down`.

### Manual

To run the pieces yourself instead, start PostgreSQL:

```bash
docker compose up -d postgres
```

Export the database and security settings from `.env`, then run:

```bash
mvn -pl mms-service spring-boot:run
npm --prefix mms-ui ci
npm --prefix mms-ui run dev
```

The Vite dev server runs on port `3001` and proxies `/api` to the API on port `8081`.

## Verification

```bash
make ui-install
make verify
```

The backend uses Flyway-managed PostgreSQL migrations and Testcontainers-based integration tests. Docker must be running for the complete backend test suite.

## Data Migration

The service includes a one-shot MongoDB-to-PostgreSQL migration profile. See the migration code under `mms-service/src/main/java/com/roots/mms/migration` and run the migration targets from `mms-service/Makefile`.
