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

## Money Service rule

Money Service uses the status endpoint before wallet setup and sensitive financial
operations. It owns all wallet, account, and transaction records.

The agreed no-Kafka wallet behavior is:

```text
Money Service lazily creates a wallet when an authenticated user first accesses a
wallet operation, after validating that user through the internal User API.
```

User registration therefore does not call Money Service and succeeds even when
Money Service is unavailable.

## Deactivation limitation

User Service changes an account to `INACTIVE`, but this version publishes no
deactivation event. Contact and Money Services are not automatically notified and
must recheck status before sensitive operations. Asynchronous Kafka propagation can
be introduced in a later version.
