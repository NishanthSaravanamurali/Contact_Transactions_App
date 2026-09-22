# Contact Transaction App

This repository currently contains the discovery, gateway, and user components of
the Contact Transaction App learning project.

## Modules

| Module | Application name | Port | Responsibility |
|---|---|---:|---|
| `discovery-server` | `DISCOVERY-SERVER` | 8761 | Eureka registry and dashboard |
| `user-service` | `USER-SERVICE` | 8081 | Registration, authentication, profiles, account status, and internal user lookup |
| `api-gateway` | `API-GATEWAY` | 8080 | Public routing, JWT validation, CORS, and trace-ID forwarding |

The Gateway discovers `USER-SERVICE` through Eureka and routes:

```text
/api/v1/auth/**  -> lb://USER-SERVICE
/api/v1/users/** -> lb://USER-SERVICE
```

`/internal/**` is deliberately not routed by the public Gateway.

## Implemented functionality

- Oracle persistence against the manually maintained `APP_USER` table
- Argon2id password hashing
- Registration with normalized, database-unique email addresses
- Login and RS256 JWT creation
- JWT validation by both API Gateway and User Service
- Authenticated profile read and restricted profile update
- Password change with current-password verification
- Stateless logout
- Soft account deactivation (`ACTIVE` to `INACTIVE`)
- Internal status and mobile-resolution APIs protected by a shared service token
- Transactional `UserRegistered` outbox rows committed with user registration
- Scheduled Kafka publication to `user.lifecycle.v1` with failure retry state
- Eureka registration and load-balanced Gateway routing
- Central Gateway CORS and trace-ID forwarding
- Consistent controller-level API errors

Flyway, OpenAPI, and Swagger are intentionally outside this version. The
`APP_USER` and `OUTBOX_EVENT` tables are maintained manually in Oracle.

## Prerequisites

- JDK 24
- Oracle Database with the manually created `APP_USER` and `OUTBOX_EVENT` tables
- Kafka reachable through `KAFKA_BOOTSTRAP_SERVERS`
- Maven Wrapper supplied by this repository
- An RSA private/public key pair supplied through environment variables

See [IntelliJ and environment setup](docs/setup-intellij.md) before starting the
applications.

## Start order

### Start all five services on Windows

Copy `.env.example` to `.env` in this backend folder and fill in your database,
JWT keys, and shared internal token (or set these variables in your shell).
Variables saved only in IntelliJ run configurations are not available to the
launcher. Java 24 and the existing Oracle database/schema must be available.

Double-click **`start-all.bat`**, or run it from PowerShell:

```powershell
.\start-all.bat
```

The launcher starts Discovery (8761), User (8081), Contact (8082), Transaction
(8083), then Gateway (8080). It waits for health and Eureka registration before
moving to the next service. Transaction has no health endpoint, so its readiness
check uses its listening port and Eureka UP registration. Local ports and the
local Eureka URL are enforced for this launcher.

Keep the launcher window open; use **Ctrl+C** to stop its services in reverse
order. Startup failures/timeouts also stop services launched by this invocation.
Existing occupied ports cause an error before anything starts. Logs are saved in
`logs/<timestamp>/`, with separate output and error files per service.

Use `.\start-all.bat -Check` for environment/path/port checks without starting
services, or `.\start-all.bat -StartupTimeoutSeconds 600` to allow more time for
initial Maven downloads. The default timeout is 300 seconds per service.

### Start individual services

Use three IntelliJ run configurations or three PowerShell windows:

```powershell
.\mvnw.cmd -pl discovery-server spring-boot:run
.\mvnw.cmd -pl user-service spring-boot:run
.\mvnw.cmd -pl api-gateway spring-boot:run
```

Start them in this order:

1. Discovery Server
2. User Service
3. API Gateway

Then check:

- Eureka dashboard: `http://localhost:8761`
- User Service health: `http://localhost:8081/actuator/health`
- Gateway health: `http://localhost:8080/actuator/health`

The Eureka dashboard should show both `USER-SERVICE` and `API-GATEWAY`.

## Documentation

- [Implemented services and service-class guide](docs/implemented-services-guide.md)
- [Public API contracts](docs/api-contracts.md)
- [Contact and Money Service integration contracts](docs/integration-contracts.md)
- [Transaction Service Kafka integration](docs/transaction-service-kafka-integration.md)
- [JWT integration for Contact and Transaction Services](docs/jwt-integration.md)
- [IntelliJ and environment setup](docs/setup-intellij.md)
- [Live Gateway demonstration](docs/live-gateway-demo.md)
- [Complete Insomnia live-test plan](docs/insomnia-live-test-plan.md)
- [Importable Insomnia test collection](insomnia/contact-transaction-app-live-tests.postman_collection.json)

## Build commands

Run commands from the repository root—the directory containing the top-level
`pom.xml` and `mvnw.cmd`.

Compile one module:

```powershell
.\mvnw.cmd -pl user-service compile
.\mvnw.cmd -pl api-gateway compile
```

Run the complete automated verification when the remaining tests and live checks
are ready:

```powershell
.\mvnw.cmd clean verify
```

## Current limitations

- Logout is stateless. Deleting the token on the client does not revoke an
  already-issued token before its expiry.
- There is no Kafka notification when a user is deactivated. Contact and Money
  Services must query the internal status API before sensitive operations.
- Mobile numbers are indexed but not unique. Resolution currently returns the
  matching row with the smallest user ID if duplicate mobile numbers exist.
- The User Service still needs a request trace filter and Spring Security error
  handlers so direct security failures use the same trace header and JSON error
  format as Gateway failures.
- Production HTTPS, secret management, and persistent Gateway rate limiting are
  deployment responsibilities not demonstrated by the local setup.
