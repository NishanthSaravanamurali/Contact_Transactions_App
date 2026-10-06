# Public User API Contracts

Use the Gateway base URL for client traffic:

```text
http://localhost:8080
```

For direct User Service checks only, replace it with `http://localhost:8081`.
Requests and responses use JSON unless the successful response has no body.

## Endpoint summary

| Method | Path | Authentication | Success |
|---|---|---|---|
| POST | `/api/v1/auth/register` | Public | `201 Created` |
| POST | `/api/v1/auth/login` | Public | `200 OK` |
| POST | `/api/v1/auth/logout` | Bearer JWT | `204 No Content` |
| GET | `/api/v1/users/me` | Bearer JWT | `200 OK` |
| PATCH | `/api/v1/users/me` | Bearer JWT | `200 OK` |
| PUT | `/api/v1/users/me/password` | Bearer JWT | `200 OK` |
| DELETE | `/api/v1/users/me` | Bearer JWT | `204 No Content` |

## Register

```http
POST /api/v1/auth/register
Content-Type: application/json
```

```json
{
  "name": "Alex Johnson",
  "email": "alex@example.com",
  "password": "Strong@Password123",
  "mobileNo": "9876543210",
  "dateOfBirth": "2000-06-15"
}
```

The name is trimmed and must contain 5–60 characters. Email is trimmed,
lowercased, syntax-checked, and must be unique. Password length is 12–128 and it
must contain uppercase, lowercase, numeric, and special characters. Mobile number
must contain ten digits and begin with 6–9. The user must be at least 18 years old.

Successful response:

```json
{
  "userId": 1,
  "email": "alex@example.com",
  "status": "ACTIVE",
  "createdAt": "2026-09-16T10:00:00.123456"
}
```

The password is encoded with Argon2id before persistence. Neither the password nor
the encoded value appears in the response.

Duplicate normalized email addresses return `409 Conflict`.

## Login

```http
POST /api/v1/auth/login
Content-Type: application/json
```

```json
{
  "email": "alex@example.com",
  "password": "Strong@Password123"
}
```

Successful response:

```json
{
  "accessToken": "signed-jwt",
  "tokenType": "Bearer",
  "expiresIn": 1800
}
```

Only `ACTIVE` users can log in. An unknown email or wrong password returns the same
message: `Invalid email or password`. An inactive account returns `403 Forbidden`.

The JWT uses RS256 and contains the numeric user ID as a string in `sub`, plus the
normalized email, `iat`, and `exp`. Clients cannot select a different user ID.

## Read the authenticated profile

```http
GET /api/v1/users/me
Authorization: Bearer <access-token>
```

```json
{
  "userId": 1,
  "name": "Alex Johnson",
  "email": "alex@example.com",
  "mobileNo": "9876543210",
  "dateOfBirth": "2000-06-15",
  "status": "ACTIVE",
  "createdAt": "2026-09-16T10:00:00.123456",
  "updatedAt": "2026-09-16T10:00:00.123456"
}
```

The service derives `userId` only from the JWT `sub` claim.

## Update the authenticated profile

```http
PATCH /api/v1/users/me
Authorization: Bearer <access-token>
Content-Type: application/json
```

```json
{
  "name": "Alex Johnson Updated",
  "mobileNo": "9123456789"
}
```

At least one field is required. Only `name` and `mobileNo` can be changed. A
successful response is the complete safe profile shown above. If `UPDATED_AT`
changed after the profile was read, the service returns `409 Conflict`.

## Change password

```http
PUT /api/v1/users/me/password
Authorization: Bearer <access-token>
Content-Type: application/json
```

```json
{
  "currentPassword": "Strong@Password123",
  "newPassword": "New@Password456"
}
```

The current password must match and the new password must satisfy the registration
password policy. Success returns `200 OK` with no response body.

## Logout

```http
POST /api/v1/auth/logout
Authorization: Bearer <access-token>
```

Success returns `204 No Content`. Logout is stateless: the client deletes its
token, but the issued JWT remains cryptographically valid until `exp`.

## Deactivate account

```http
DELETE /api/v1/users/me
Authorization: Bearer <access-token>
```

Success returns `204 No Content` and changes the database status from `ACTIVE` to
`INACTIVE`. The row remains in Oracle. Future login attempts are rejected.

No Kafka event is produced in this version.

## Error response

Controller and service failures use:

```json
{
  "timestamp": "2026-09-16T10:00:00Z",
  "traceId": "trace-value",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Registration details are invalid",
  "fieldErrors": {
    "email": "Email format is invalid"
  }
}
```

Expected status codes are `400`, `401`, `403`, `404`, `409`, and `500`. Stack
traces, Oracle errors, password hashes, and JWT values are never part of an API
response.
