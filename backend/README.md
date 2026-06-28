# Personal Finance — Backend

REST API built with **Java 21 + Spring Boot 3.3.5** for the Personal Finance app.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.5 |
| Security | Spring Security + JWT (jjwt 0.12) |
| Persistence | Spring Data JPA + PostgreSQL 16 |
| Migrations | Flyway |
| Mapping | MapStruct |
| Code generation | Lombok |
| Formatting | Spotless (Google Java Format 1.22) |
| Tests | JUnit 5 + Spring Boot Test + H2 (in-memory) |

## Project Structure

```
src/main/java/com/personalfinance/
├── config/          # SecurityConfig, JwtFilter, CORS, beans
├── controller/      # REST endpoints
├── service/         # Business logic
├── repository/      # Spring Data JPA interfaces
├── model/
│   ├── entity/      # JPA entities (UUID PKs)
│   └── enums/       # TransactionType, etc.
└── dto/
    ├── request/     # Inbound payloads (@Valid)
    └── response/    # Outbound payloads (MapStruct)

src/main/resources/
├── application.yml          # Common config
├── application-dev.yml      # Dev (PostgreSQL local)
├── application-prod.yml     # Prod (env vars)
└── db/migration/
    ├── V1__initial_schema.sql    # All 8 tables
    ├── V2__seed_categories.sql   # Default categories
    └── V3__seed_merchant_rules.sql  # Merchant knowledge base
```

## Prerequisites

- Java 21+
- Maven 3.9+ (or use the included wrapper `./mvnw`)
- Docker (for the database)

## Running Locally

**1. Start the database:**
```bash
docker compose up -d postgres
```

**2. Run the backend:**
```bash
./mvnw spring-boot:run
```

The API starts at `http://localhost:8080`. Flyway applies migrations automatically on startup.

**3. Verify:**
```bash
curl http://localhost:8080/api/actuator/health
# {"status":"UP"}
```

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `personal_finance` | Database name |
| `DB_USER` | `postgres` | Database user |
| `DB_PASSWORD` | `postgres` | Database password |
| `JWT_SECRET` | *(dev default)* | JWT signing secret — **change in production** |
| `SPRING_PROFILES_ACTIVE` | `dev` | Active Spring profile (`dev` / `prod`) |

Copy `.env.example` to `.env` in the project root before running Docker Compose.

## Spring Profiles

| Profile | Database | Flyway | DDL |
|---|---|---|---|
| `dev` (default) | PostgreSQL localhost:5432 | enabled | validate |
| `prod` | PostgreSQL (env vars) | enabled | validate |
| `test` | H2 in-memory (PostgreSQL mode) | disabled | create-drop |

## Common Commands

```bash
# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=TransactionServiceTest

# Check formatting
./mvnw spotless:check

# Apply formatting
./mvnw spotless:apply

# Build JAR (skip tests)
./mvnw package -DskipTests

# Compile only (fast check)
./mvnw compile
```

## API Overview

| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Login → JWT |
| GET | `/api/categories` | List categories |
| GET | `/api/transactions` | List transactions (paginated) |
| POST | `/api/transactions` | Create manual transaction |
| POST | `/api/import/parse` | Upload & parse PDF (EXTRATO/FATURA) |
| POST | `/api/import/{id}/confirm` | Confirm import session |
| GET | `/api/review/pending` | List pending review queue |
| POST | `/api/review/{id}/resolve` | Resolve review item (triggers learning) |
| GET | `/api/dashboard/monthly` | Monthly dashboard metrics |
| GET/POST/PUT/DELETE | `/api/known-persons` | Manage known PIX persons |

All endpoints except `/api/auth/**` require `Authorization: Bearer <token>`.
