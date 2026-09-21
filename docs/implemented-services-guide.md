# Implemented Services Guide

This guide describes what is implemented in the repository today. It focuses on
the responsibilities of each runnable application and explains every file in the
User Service `service` package.

## Runnable applications

### Discovery Server

Module: `discovery-server`  
Application name: `DISCOVERY-SERVER`  
Port: `8761`

The Discovery Server is the Eureka registry. User Service and API Gateway register
themselves here. A service can then be located by its logical application name
instead of a hard-coded host and port.

It does not contain user data, validate JWTs, or route business requests.

### API Gateway

Module: `api-gateway`  
Application name: `API-GATEWAY`  
Port: `8080`

Implemented responsibilities:

- Registers with Eureka and discovers `USER-SERVICE`.
- Routes `/api/v1/auth/**` and `/api/v1/users/**` to `lb://USER-SERVICE`.
- Allows registration and login without authentication.
- Requires a valid RSA-signed JWT for logout and profile operations.
- Validates JWTs using only the User Service public RSA key.
- Denies `/internal/**`; internal service APIs are not public routes.
- Applies centralized CORS rules.
- accepts or generates `X-Trace-Id` and forwards it downstream.
- Returns consistent JSON for Gateway authentication and authorization failures.

The Gateway does not issue JWTs and does not access the User Service database.

### User Service

Module: `user-service`  
Application name: `USER-SERVICE`  
Port: `8081`

Implemented responsibilities:

- Persists users in the manually created Oracle `APP_USER` table.
- Registers users and prevents duplicate normalized email addresses.
- Hashes passwords with Argon2id before persistence.
- Authenticates active users and creates RS256 access tokens.
- Validates JWTs again on protected User Service endpoints.
- Reads and updates the authenticated user's profile.
- Changes passwords after verifying the current password.
- Performs stateless logout.
- Deactivates accounts by changing `ACTIVE` to `INACTIVE`.
- Provides internal status and mobile-resolution APIs.
- Requests wallet creation after a new user transaction commits.
- Registers with Eureka and exposes Actuator health information.

## Endpoint-to-service mapping

| Endpoint | Controller | Main service class |
| --- | --- | --- |
| `POST /api/v1/auth/register` | `AuthController` | `UserRegistrationService` |
| `POST /api/v1/auth/login` | `AuthController` | `AuthenticationService` |
| `POST /api/v1/auth/logout` | `AuthController` | No service; stateless `204` response |
| `GET /api/v1/users/me` | `UserController` | `UserProfileService` |
| `PATCH /api/v1/users/me` | `UserController` | `UserProfileService` |
| `PUT /api/v1/users/me/password` | `UserController` | `UserPasswordService` |
| `DELETE /api/v1/users/me` | `UserController` | `UserDeactivationService` |
| `GET /internal/v1/users/{userId}/status` | `InternalUserController` | `InternalUserService` |
| `POST /internal/v1/users/resolve` | `InternalUserController` | `InternalUserService` |

Controllers translate HTTP requests and responses. The classes below contain the
business rules and transaction boundaries.

## Files in the User Service service package

Location:

```text
user-service/src/main/java/com/contacttx/userservice/service
```

### `UserRegistrationService.java`

Purpose: creates a new User Service account.

What `register(...)` does:

1. Trims the email and converts it to lowercase.
2. Checks for an existing normalized email.
3. Hashes the validated password using `PasswordEncoder` (Argon2id).
4. Constructs an `ACTIVE` `AppUser`.
5. Saves and flushes it to Oracle.
6. Translates an Oracle unique-constraint race into `DuplicateEmailException`.
7. Publishes `UserRegisteredEvent` with the generated numeric user ID.
8. Maps the entity to a safe `RegistrationResponse`.

Important annotation:

```java
@Transactional
```

All database work succeeds or rolls back as one transaction. The event is
published while this transaction is active, but the wallet listener does not run
until the transaction commits.

Dependencies:

- `AppUserRepository` for persistence and uniqueness checks.
- `PasswordEncoder` for Argon2id hashing.
- `UserMapper` for the safe response DTO.
- `ApplicationEventPublisher` for the post-commit wallet workflow.

### `AuthenticationService.java`

Purpose: verifies login credentials and issues an access token.

What `login(...)` does:

1. Normalizes the submitted email.
2. Loads the user by email.
3. Requires `ACTIVE` status.
4. Compares the submitted password with the stored Argon2 hash using
   `PasswordEncoder.matches(...)`.
5. Generates an RS256 JWT whose `sub` claim is the numeric user ID.
6. Returns the token type and configured expiry.

Unknown email and incorrect password both result in the generic invalid-credentials
error. This prevents account enumeration.

The method uses `@Transactional(readOnly = true)` because it only reads Oracle.

### `UserProfileService.java`

Purpose: reads and updates the authenticated user's safe profile.

`getProfile(...)` loads the user, requires `ACTIVE` status, and returns a DTO that
does not contain the password hash.

`updateProfile(...)` changes only `name` and `mobileNo`. The user ID comes from the
JWT `sub` claim in `UserController`; it is not accepted from the request body.

The update includes the row's previously read `updatedAt` value in the database
condition. If another request changed the row first, zero rows are updated and the
service throws `ConcurrentUpdateException` rather than silently overwriting the
newer data.

### `UserPasswordService.java`

Purpose: changes the authenticated user's password.

What `changePassword(...)` does:

1. Loads the user by the JWT-derived user ID.
2. Requires `ACTIVE` status.
3. Verifies the current password against the existing Argon2 hash.
4. Hashes the already-validated new password with a new salt.
5. Updates the hash using the same `updatedAt` concurrency check.

The method never returns, logs, or stores a plaintext password.

### `UserDeactivationService.java`

Purpose: deactivates an account without deleting its Oracle row.

`deactivate(...)` requires an existing `ACTIVE` user and conditionally changes the
status to `INACTIVE`. It also uses the `updatedAt` concurrency check. An inactive
user cannot log in and cannot use protected profile operations.

This version does not publish a Kafka deactivation event. Other services must
check the internal status API before sensitive operations.

### `InternalUserService.java`

Purpose: supplies the minimum identity information needed by Contact and
Money/Transaction Services.

Implemented operations:

- `getStatus(userId)` returns `{ "userId": ..., "status": ... }`.
- `resolveByMobile(request)` locates a user by the numeric mobile value and returns
  the same minimal response.

Both methods are read-only transactions. Their controller endpoints require
`X-Internal-Service-Token`; they do not expose email, date of birth, or password
information.

Mobile numbers are currently indexed but not unique. If duplicates exist,
resolution returns the matching row with the smallest user ID.

### `UserRegisteredEvent.java`

Purpose: carries the newly committed account's `userId` from registration to the
wallet-provisioning listener.

This is an in-process Spring application event. It is not a Kafka event and is not
stored in an outbox table. Its only field is the numeric user ID.

### `WalletProvisioningListener.java`

Purpose: starts wallet creation only after the Oracle user transaction commits.

Important annotation:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
```

Consequences:

- A rolled-back registration never calls Transaction Service.
- Transaction Service can call the User Service status endpoint and find the
  committed user.
- The remote call does not hold the Oracle registration transaction open.

The listener catches remote runtime failures. Therefore a Transaction Service
outage cannot roll back a committed user or change the registration response into
a misleading failure. It logs only the safe user ID.

Because Kafka and an outbox are excluded, a failed request is not durably queued.
The wallet must later be retried operationally or created when the user first uses
a wallet operation.

### `TransactionWalletClient.java`

Purpose: performs the outbound HTTP request to Money/Transaction Service.

It sends:

```http
POST /internal/v1/wallets
X-Internal-Service-Token: <shared-token>
Content-Type: application/json
```

```json
{
  "userId": 1
}
```

The client uses the configured base URL:

```properties
integration.transaction-service.base-url=${TRANSACTION_SERVICE_URL:http://TRANSACTIONMICROSERVICE}
```

Its `RestClient.Builder` is marked `@LoadBalanced`, so the default logical host is
resolved through Eureka. `TRANSACTIONMICROSERVICE` must exactly match the
Transaction Service's `spring.application.name`.

The same `INTERNAL_SERVICE_TOKEN` must be configured in both services. The token
authenticates the calling service; the body identifies the user whose wallet must
be created.

### `CreateWalletRequest.java`

This DTO is physically located under `dto/request`, not `service`, but it belongs
to the wallet workflow. It intentionally contains only `userId`. User Service does
not send user profile data, passwords, JWTs, balances, or account information to
Transaction Service.

## Registration and wallet sequence

```text
Client
  -> AuthController
  -> UserRegistrationService
  -> Oracle APP_USER insert
  -> UserRegisteredEvent published
  -> Oracle commit
  -> WalletProvisioningListener
  -> TransactionWalletClient
  -> POST http://TRANSACTIONMICROSERVICE/internal/v1/wallets
  -> Transaction Service validates internal token
  -> Transaction Service calls USER-SERVICE status endpoint
  -> Transaction Service creates or returns the user's wallet
```

The Transaction Service wallet operation must be idempotent and enforce one
wallet per `userId`. Retrying the same user ID must return the existing wallet,
not create a duplicate.

## Transaction annotations in plain language

| Annotation | Meaning in this implementation |
| --- | --- |
| `@Transactional` | Database changes form one commit-or-rollback unit. |
| `@Transactional(readOnly = true)` | The operation reads data and does not intend to modify it. |
| `@TransactionalEventListener(AFTER_COMMIT)` | The method runs only after the surrounding transaction commits successfully. |

## Important boundaries

- User Service owns users, password hashes, authentication, status, and JWT
  creation.
- Transaction Service owns wallets, accounts, balances, and money transactions.
- User Service sends only the external `userId`; it never writes to the
  Transaction Service database.
- Transaction Service stores `userId` as an external identifier, not a
  cross-schema foreign key.
- Public clients never provide their own owner or payer user ID. Services derive
  it from the validated JWT `sub` claim.
- Internal endpoints and internal tokens are never routed through API Gateway.

## Related documentation

- [Public API contracts](api-contracts.md)
- [Integration contracts](integration-contracts.md)
- [JWT integration](jwt-integration.md)
- [IntelliJ and environment setup](setup-intellij.md)
- [Live test plan](insomnia-live-test-plan.md)
