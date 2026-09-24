# Contact and Money Service Integration Contracts

These contracts are authoritative for services integrating with User Service.
Other services must not read the User Service database.

For public bearer-token validation and authenticated-user extraction, follow the
[JWT integration contract](jwt-integration.md).

## Service discovery

The logical Eureka service name is:

```text
USER-SERVICE
```

A discovery-aware service calls URLs such as:

```text
http://USER-SERVICE/internal/v1/users/1/status
http://USER-SERVICE/internal/v1/users/resolve
http://USER-SERVICE/internal/v1/users/display-names
```

The caller must use a load-balanced Spring HTTP client so `USER-SERVICE` is
resolved through Eureka.

## Internal authentication

Every internal request must contain:

```http
X-Internal-Service-Token: <shared-INTERNAL_SERVICE_TOKEN-value>
```

Use the same secret value in User Service, Contact Service, and Money Service run
configurations. This token is separate from a user JWT and must never be sent to a
browser.

The API Gateway has no `/internal/**` route and explicitly denies such requests.

## Read status by user ID

```http
GET /internal/v1/users/{userId}/status
X-Internal-Service-Token: <service-token>
```

Found response:

```json
{
  "userId": 1,
  "status": "ACTIVE"
}
```

The Transaction Service response model should match this contract:

```java
public record UserStatusResponse(Long userId, String status) {
}
```

The current User Service schema uses an Oracle identity `NUMBER`, so `userId` is
a Java `Long`. Older diagrams that label this value as a UUID are no longer the
implementation contract.

The response may also contain `INACTIVE` or `DELETED`. A missing user returns
`404 Not Found`. No email, date of birth, password data, or other profile fields
are returned.

## Resolve a user by mobile number

```http
POST /internal/v1/users/resolve
X-Internal-Service-Token: <service-token>
Content-Type: application/json
```

```json
{
  "mobileNo": "9876543210"
}
```

Found response:

```json
{
  "userId": 1,
  "status": "ACTIVE"
}
```

The current database does not enforce unique mobile numbers. If duplicates exist,
the implementation returns the matching row with the smallest user ID. Add a
database unique constraint later if mobile resolution must be unambiguous.

## Contact Service rule

Contact Service may use both internal endpoints. It stores User Service IDs as
external identifiers without a cross-schema database foreign key. It must validate
the relevant status before operations that require an active user.

## Resolve registered display names in bulk

Transaction Service uses one bulk request per transaction-history page so a user
who has not saved the other participant as a contact can still see that person's
registered profile name.

```http
POST /internal/v1/users/display-names
X-Internal-Service-Token: <service-token>
Content-Type: application/json
```

```json
{
  "userIds": [7, 12, 25]
}
```

The request must contain 1–200 positive user IDs. Duplicate IDs are accepted and
resolved once, preserving their first-requested order.

```json
{
  "users": [
    { "userId": 7, "displayName": "Alice Sharma" },
    { "userId": 12, "displayName": "Bob Tester" }
  ],
  "unresolvedUserIds": [25]
}
```

Deleted or missing users are listed in `unresolvedUserIds`; their names are not
returned. This endpoint is internal-only and must not be exposed through the API
Gateway or called directly by the browser.

## Money Service rule

Money Service uses the status endpoint before wallet setup and sensitive financial
operations. It uses the bulk display-name endpoint while building transaction
history and owns all wallet, account, and transaction records. If the display-name
lookup is unavailable, history still succeeds with nullable name fields.

The agreed no-Kafka registration behavior is:

```text
Commit the new User Service row, then request wallet creation from the
Money/Transaction Service using the committed user ID.
```

The wallet request is deliberately triggered after the Oracle commit. This lets
Money/Transaction Service call the User Service status endpoint and find the new
user. A failed wallet request does not roll back the committed user registration.

### User, wallet, and account identity

These IDs identify different things:

| Identifier | Owner | Meaning | Cardinality |
| --- | --- | --- | --- |
| `userId` (`Long`) | User Service | The person who owns financial records | One per user |
| `walletId` (UUID) | Money/Transaction Service | The user's application wallet | One wallet per user |
| `accountId` (UUID) | Money/Transaction Service | One funding or payment account | Many accounts per user |

Money Service stores `userId` on its `WALLET` and `ACCOUNT` rows as an external
User Service identifier. It is not a cross-schema foreign key. Enforce
`UNIQUE(user_id)` on `WALLET`, but do not make `ACCOUNT.user_id` unique because a
user can have multiple accounts.

An account must have its own `accountId` and a safe display label such as
`displayName`, `accountType`, and a masked account number or last four digits. The
client selects an `accountId`; the service establishes ownership with both values:

```text
findByAccountIdAndUserId(accountId, authenticatedUserId)
```

For public operations, `authenticatedUserId` must come from the validated JWT
`sub` claim. A client must never be allowed to choose `ownerUserId` or
`payerUserId` in a request body.

### Internal wallet creation

If Money/Transaction Service exposes the team-proposed endpoint, the complete
service-to-service request must identify both the calling service and the user:

```http
POST /internal/v1/wallets
X-Internal-Service-Token: <shared-service-token>
Content-Type: application/json
```

```json
{
  "userId": 1
}
```

The header authenticates the calling service; it does not identify the user whose
wallet is being created. The `userId` in the body provides that identity.

Before creating the wallet, Money/Transaction Service must:

1. Validate `X-Internal-Service-Token`.
2. Call `GET /internal/v1/users/{userId}/status` with the same service token.
3. Require `ACTIVE` status.
4. Find or create the wallet using the unique `userId` constraint.
5. Return the existing wallet when one already exists, so retries cannot create
   duplicate wallets.

User Service calls this endpoint after a successful registration commit. The
Money/Transaction endpoint must remain idempotent because a later retry of the
same `userId` must not create a second wallet. This no-Kafka version does not have
a durable automatic retry queue; if the remote call fails, wallet creation must be
retried operationally or by Money/Transaction Service when the user first accesses
a wallet operation.

When using a URL such as `http://TRANSACTIONMICROSERVICE/internal/v1/wallets`, the
host must exactly match that service's `spring.application.name`, and the caller
must use a Eureka-aware, load-balanced HTTP client. It is not a normal public DNS
address and is not routed through the public API Gateway.

## Deactivation limitation

User Service changes an account to `INACTIVE`, but this version publishes no
deactivation event. Contact and Money Services are not automatically notified and
must recheck status before sensitive operations. Asynchronous Kafka propagation can
be introduced in a later version.
