# Contact Service Integration Contracts

This document defines how Contact Service interacts with other modules in the
Contact Transaction App.

## Service ownership

Contact Service owns:

- Creating contacts for an authenticated owner
- Listing contacts belonging to an authenticated owner
- Reading, updating, and deleting one owned contact
- Resolving a requested linked contact through User Service
- Contact data stored in `SYSTEM.CONTACT` for the current training setup

Contact Service does not own:

- Registration, login, passwords, JWT signing, or user lifecycle
- User Service-owned tables, even when they share the `SYSTEM` schema
- Wallets, accounts, balances, transactions, or transfers
- Money Service-owned tables, even when they share the `SYSTEM` schema

The training database physically shares `SYSTEM`, but the logical service
boundary remains: no other service may query `SYSTEM.CONTACT` directly, and
Contact Service must not query User Service or Money Service tables directly.

## User Service discovery

Contact Service calls User Service through Eureka using this service ID:

```text
USER-SERVICE
```

The logical base URL is:

```text
http://USER-SERVICE
```

The `@LoadBalanced RestClient.Builder` asks Spring Cloud LoadBalancer to resolve
`USER-SERVICE` to an available instance registered in Eureka. No User Service
host or port is hardcoded in Java code.

## Internal authentication token

The client requires:

```text
USER_SERVICE_INTERNAL_TOKEN
```

The current implementation sends it in:

```http
X-Internal-Service-Token: <shared internal token>
```

`X-Internal-Service-Token` matches the User Service internal-token filter.

The token must never be:

- Committed to Git
- Returned in an API response
- Included in trace or error logs
- Added to exception messages
- Placed in request DTOs

## Get user status

Request:

```http
GET /internal/v1/users/{userId}/status
X-Internal-Service-Token: <shared internal token>
```

Successful response:

```json
{
  "userId": 101,
  "status": "ACTIVE"
}
```

This operation is intended for Contact operations that depend on the current
state of an already linked user.

Expected behavior:

- `200`: validate that the response contains a positive numeric `userId` and a
  nonblank `status`.
- `404`: treat the user as not found.
- Timeout, connection failure, invalid body, or other HTTP failure: treat User
  Service as unavailable and return a safe `503` from the public Contact API.

## Resolve a user by mobile number

Request:

```http
POST /internal/v1/users/resolve
Content-Type: application/json
X-Internal-Service-Token: <shared internal token>
```

```json
{
  "mobileNo": "9876543210"
}
```

Successful response:

```json
{
  "userId": 101,
  "status": "ACTIVE"
}
```

Contact Service uses this operation when `linkToRegisteredUser` is `true`.

Rules:

1. Contact Service validates that the public phone input contains exactly ten
   digits before calling User Service.
2. User Service must return a positive numeric `userId` and nonblank `status`.
3. The status must equal `ACTIVE`.
4. The returned `userId` must not equal the authenticated `owner_user_id`.
5. Only after all checks pass may Contact Service store that ID in
   `linked_user_id`.
6. If `linkToRegisteredUser` is `false`, Contact Service does not call User
   Service and stores `linked_user_id` as `NULL`.

## User Service failure mapping

| User Service outcome | Contact Service behavior |
| --- | --- |
| Active user resolved | Continue and store the returned numeric ID |
| No user resolves from phone | `404 LINKED_USER_NOT_FOUND` |
| Resolved user is inactive | `409 LINKED_USER_INACTIVE` |
| Resolved user is the owner | `400 SELF_LINK_NOT_ALLOWED` |
| Connection or response timeout | `503 USER_SERVICE_UNAVAILABLE` |
| Invalid or empty response body | `503 USER_SERVICE_UNAVAILABLE` |
| Other client/server HTTP failure | `503 USER_SERVICE_UNAVAILABLE` |

Contact Service must not silently create a requested linked contact as an
external contact when validation fails. The caller must explicitly send
`linkToRegisteredUser=false` to create an external contact.

## Timeouts

Current configuration:

```properties
contact-service.user-service.connect-timeout=2s
contact-service.user-service.read-timeout=3s
```

The connection timeout limits how long establishing the network connection may
take. The read timeout limits how long Contact Service waits for the response.
Both values prevent Contact requests from waiting indefinitely.

Changing these values requires observing the deployed network and User Service
latency. Do not change dependency versions to diagnose a network timeout.

## No-Kafka behavior

Kafka and outbox messaging are excluded from this project.

Consequences:

- Contact Service does not receive automatic user-deactivation events.
- An existing `linked_user_id` can remain stored after that user is deactivated.
- Operations whose correctness depends on current linked-user status must call
  `GET /internal/v1/users/{userId}/status` synchronously.
- `linked_user_id` is an external reference, not an Oracle foreign key.

This is an explicit consistency tradeoff, not an accidental missing consumer.

## Contact Service Eureka registration

Contact Service registers with Eureka as:

```text
CONTACT-SERVICE
```

Configuration:

```properties
spring.application.name=CONTACT-SERVICE
eureka.client.serviceUrl.defaultZone=${EUREKA_URL:http://localhost:8761/eureka/}
```

The Eureka client starter enables registration and discovery. Modern Spring
Cloud does not require `@EnableEurekaClient`.

For local integration testing, start services in this order:

1. Oracle Database and listener
2. Eureka Server
3. User Service
4. Contact Service
5. API Gateway, after its route and security behavior are implemented

After Contact Service starts, open the Eureka dashboard and verify that
`CONTACT-SERVICE` is `UP`. Live registration has not yet been verified in this
repository because the Eureka Server project is external.

## Future API Gateway integration

The planned public Gateway route is:

```text
/api/v1/contacts/** → lb://CONTACT-SERVICE
```

The Gateway must not expose these User Service contracts through a public
`/internal/**` route.

Planned request flow:

```text
Client
  → API Gateway
  → lb://CONTACT-SERVICE
  → Contact controller
  → Contact service
  → SYSTEM.CONTACT
```

For a requested linked contact:

```text
Contact service
  → Eureka/LoadBalancer resolves USER-SERVICE
  → User Service resolve endpoint
  → validate ACTIVE and not self
  → SYSTEM.CONTACT
```

JWT validation is enabled in Contact Service as defense in depth:

- Gateway validates the JWT before routing.
- Contact Service also validates the JWT.
- Contact Service uses the numeric JWT `sub` claim as `owner_user_id`.
- Neither Gateway nor clients send a freely editable owner-ID header.

The Contact controller now exposes the owner-scoped public API directly on
Contact Service. Gateway routing remains an external prerequisite.

## Money Service boundary

Transaction Service may synchronously check contact eligibility through:

```http
POST /internal/v1/contacts/payment-eligibility
X-Internal-Service-Token: <INTERNAL_SERVICE_TOKEN>
```

The request contains `senderUserId` and `receiverUserId`. Contact Service
returns `allowed: true` only if the sender owns a contact linked to the receiver
and the receiver is currently `ACTIVE` in User Service. This endpoint is
read-only and must not be published by API Gateway.

Contact data must not be used to mutate accounts, wallets, balances, or
transactions. Transaction Service must not access `SYSTEM.CONTACT` directly;
it must use this explicit service API contract and must not introduce a
cross-schema foreign key.

## Trace propagation

External callers and Gateway may send:

```http
X-Trace-Id: gateway-trace-456
```

Contact Service accepts only short, safe trace-ID characters. Unsafe or missing
values are replaced with a UUID. The same ID is returned in response headers,
safe error bodies, and logs.

The current User Service client does not yet forward the trace ID. Trace
forwarding can be added later as a deliberate client-interceptor enhancement;
it must not forward authorization secrets or request bodies.

## Integration readiness checklist

Before testing Contact Service with User Service:

- [ ] Eureka Server is running.
- [ ] User Service registers as `USER-SERVICE`.
- [ ] Contact Service registers as `CONTACT-SERVICE`.
- [x] Both services use `X-Internal-Service-Token`.
- [ ] Both services receive the same internal token through secure environment
      configuration.
- [ ] User Service implements both documented internal endpoints.
- [ ] User Service returns numeric `userId` values.
- [ ] User Service returns the exact status value `ACTIVE` for active users.
- [ ] No service reads another service's Oracle schema directly.
- [ ] Internal endpoints are not publicly routed through Gateway.
- [ ] Logs have been checked for token and credential leakage.

Before testing through Gateway:

- [x] Contact Service JWT validation and authenticated-owner extraction are implemented.
- [ ] Gateway and Contact Service agree on the authentication boundary.
- [ ] The Gateway route uses `lb://CONTACT-SERVICE`.
- [ ] Cross-user Contact API tests return `404`.
- [ ] Direct and Gateway-routed calls return consistent safe errors.
