# Contact Service API Contracts

This document defines the intended public HTTP contract for Contact Service.

> Current status: the DTOs, mapping, persistence, business service, errors,
> trace-ID infrastructure, JWT validation, and public controller are implemented.
> The endpoints require a valid User Service-issued RS256 access token.

## API conventions

Base path:

```text
/api/v1/contacts
```

Content type:

```text
application/json
```

Trace header:

```text
X-Trace-Id
```

A caller may provide a safe `X-Trace-Id`, or Contact Service generates one.
The service returns the trace ID in the response header. Error responses also
contain it in the JSON body.

## Ownership and authentication

Every Contact API operation is owner-scoped. Contact Service validates the
access token and uses its authenticated numeric JWT `sub` claim as
`owner_user_id`.

Public callers must never supply any of these values:

- `ownerUserId`
- `linkedUserId`
- `contactId` in a request body
- `createdAt`
- `updatedAt`
- Internal service tokens

For single-contact operations, Contact Service will query by both `contactId`
and authenticated `owner_user_id`. A contact owned by another user returns
`404 Not Found`, exactly like a missing contact, so ownership information is not
disclosed.

## Internal payment eligibility

```http
POST /internal/v1/contacts/payment-eligibility
X-Internal-Service-Token: <INTERNAL_SERVICE_TOKEN>
Content-Type: application/json
```

```json
{
  "senderUserId": 42,
  "receiverUserId": 99
}
```

This is an internal service-to-service endpoint. It is not a public Contact API,
must not be routed through API Gateway, and does not accept a user JWT.

It returns `allowed: true` only when the sender owns a registered contact linked
to the receiver and User Service currently reports that receiver as `ACTIVE`.
Self-payments, external contacts, missing links, unknown users, and inactive
users return `allowed: false`. If User Service cannot be reached safely, the
endpoint returns `503 USER_SERVICE_UNAVAILABLE`.

```json
{
  "allowed": true
}
```

## Contact response

The public contact representation is:

```json
{
  "contactId": 42,
  "contactName": "Sam Taylor",
  "contactPhone": "9876543210",
  "linkedToRegisteredUser": true,
  "createdAt": "2026-09-17T10:15:30.123456",
  "updatedAt": "2026-09-17T10:15:30.123456"
}
```

`owner_user_id` and `linked_user_id` are intentionally not exposed.

Oracle columns use `TIMESTAMP` without a time-zone offset. The Java mapping is
`LocalDateTime`, so these timestamp strings do not contain `Z` or an offset.

## Create a contact

```http
POST /api/v1/contacts
```

Request:

```json
{
  "contactName": "Sam Taylor",
  "contactPhone": "9876543210",
  "linkToRegisteredUser": true
}
```

Rules:

- `contactName` is required, trimmed, and 2–20 characters long.
- `contactPhone` is required and must be a JSON string of exactly ten digits.
- A JSON number is rejected even when it contains ten digits.
- `linkToRegisteredUser` is required and must be `true` or `false`.
- When `true`, User Service must resolve the phone to an `ACTIVE` user.
- A resolved user cannot equal the authenticated owner.
- When `false`, `linked_user_id` is stored as `NULL` and User Service is not
  called.

Successful response:

```http
HTTP/1.1 201 Created
Content-Type: application/json
X-Trace-Id: 31ac7274-1405-40a2-8a0f-8fb505f67dd5
```

```json
{
  "contactId": 42,
  "contactName": "Sam Taylor",
  "contactPhone": "9876543210",
  "linkedToRegisteredUser": true,
  "createdAt": "2026-09-17T10:15:30.123456",
  "updatedAt": "2026-09-17T10:15:30.123456"
}
```

## List owned contacts

```http
GET /api/v1/contacts
```

Only contacts belonging to the authenticated owner are returned. Results are
ordered by `contactId` ascending.

Successful response:

```http
HTTP/1.1 200 OK
Content-Type: application/json
```

```json
[
  {
    "contactId": 42,
    "contactName": "Sam Taylor",
    "contactPhone": "9876543210",
    "linkedToRegisteredUser": true,
    "createdAt": "2026-09-17T10:15:30.123456",
    "updatedAt": "2026-09-17T10:15:30.123456"
  },
  {
    "contactId": 43,
    "contactName": "Lee Parker",
    "contactPhone": "9123456789",
    "linkedToRegisteredUser": false,
    "createdAt": "2026-09-17T10:20:00.000000",
    "updatedAt": "2026-09-17T10:20:00.000000"
  }
]
```

When no contacts exist, the response is an empty array:

```json
[]
```

## Get one owned contact

```http
GET /api/v1/contacts/{contactId}
```

`contactId` must be a positive numeric value representable by Java `Long`.

Successful response:

```http
HTTP/1.1 200 OK
```

The body is one Contact response.

Missing or unowned response:

```http
HTTP/1.1 404 Not Found
```

The response never reveals whether the ID exists for another owner.

## Update one owned contact

```http
PUT /api/v1/contacts/{contactId}
```

Request:

```json
{
  "contactName": "Samuel Taylor",
  "contactPhone": "9876543210",
  "linkToRegisteredUser": false
}
```

This is a full update of the editable fields. All three fields are required.
The caller cannot update ownership, IDs, or timestamps.

When linking is requested, Contact Service resolves the new phone and validates
the resolved user before modifying the managed entity. A failed validation does
not partially update the contact.

Successful response:

```http
HTTP/1.1 200 OK
```

The body is the updated Contact response. Oracle's trigger supplies the new
`updatedAt` value.

This schema has no version column, so the API does not provide JPA optimistic
locking or claim protection from concurrent lost updates.

## Delete one owned contact

```http
DELETE /api/v1/contacts/{contactId}
```

Successful response:

```http
HTTP/1.1 204 No Content
```

There is no response body.

A missing or unowned contact returns:

```http
HTTP/1.1 404 Not Found
```

## Error response

Safe errors use this shape:

```json
{
  "timestamp": "2026-09-17T04:45:30.123Z",
  "status": 400,
  "errorCode": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/v1/contacts",
  "traceId": "31ac7274-1405-40a2-8a0f-8fb505f67dd5",
  "fieldErrors": {
    "contactPhone": "contactPhone must contain exactly 10 digits"
  }
}
```

The response must never include stack traces, Java class names, SQL, Oracle
messages, JWT values, internal tokens, passwords, or database credentials.

## Error status and code table

| Status | Error code | Meaning |
| --- | --- | --- |
| `400` | `VALIDATION_FAILED` | One or more request fields failed validation |
| `400` | `MALFORMED_REQUEST` | JSON is missing, malformed, or uses the wrong JSON type |
| `400` | `INVALID_REQUEST_VALUE` | A path or request value has an invalid format |
| `400` | `SELF_LINK_NOT_ALLOWED` | The resolved linked user equals the owner |
| `401` | Authentication error to be finalized | Authentication is absent or invalid after security is enabled |
| `403` | Authorization error to be finalized | Authentication is valid but access is forbidden |
| `404` | `CONTACT_NOT_FOUND` | Contact is missing or belongs to another owner |
| `404` | `LINKED_USER_NOT_FOUND` | No registered user resolves from the requested phone |
| `409` | `LINKED_USER_INACTIVE` | The resolved User Service user is not active |
| `409` | `DATA_INTEGRITY_CONFLICT` | The requested change conflicts with a database rule |
| `500` | `INTERNAL_ERROR` | An unexpected internal failure occurred |
| `503` | `USER_SERVICE_UNAVAILABLE` | User Service could not safely validate the requested link |

The exact `401` and `403` response codes and messages will be finalized when
the authentication mechanism is explicitly implemented.

## Phone-number storage limitation

The API accepts `contactPhone` as a string so it can validate exactly ten input
digits before conversion. Oracle stores it as `NUMBER(10)` according to the
authoritative schema.

Numeric storage cannot preserve leading zeroes. For example:

```text
Input JSON:  "0123456789"
Oracle value: 123456789
Output JSON: "123456789"
```

This is a known schema limitation. The service must not silently redesign the
column type; changing it requires an explicitly approved schema migration.
