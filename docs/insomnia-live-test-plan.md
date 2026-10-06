# Complete Insomnia Live-Test Plan

This runbook verifies the implemented Discovery Server, User Service, API Gateway,
Oracle persistence, JWT security, and internal service APIs without a UI.

An importable collection containing these HTTP tests is available at
[`insomnia/contact-transaction-app-live-tests.postman_collection.json`](../insomnia/contact-transaction-app-live-tests.postman_collection.json).
It includes its own collection variables, so a pre-existing Insomnia environment
is not required. See the [import instructions](../insomnia/README.md).

Run the sections in order. Password change, short-lived-token testing, and account
deactivation intentionally appear near the end because they alter the test user's
state.

## 1. Preconditions

Confirm the following before opening Insomnia:

- Oracle is running and the `SYSTEM.APP_USER` table exists.
- `UserServiceApplication` has `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`,
  `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY`, and `INTERNAL_SERVICE_TOKEN`.
- `ApiGatewayApplication` has the same `JWT_PUBLIC_KEY`.
- Discovery Server is running on port 8761.
- User Service is running on port 8081.
- API Gateway is running on port 8080.
- Eureka shows `USER-SERVICE` and `API-GATEWAY` as `UP`.

Start order:

```text
Discovery Server -> User Service -> API Gateway
```

## 2. Create the Insomnia collection

1. Create a request collection named `Contact Transaction App`.
2. Create these folders in the collection:

```text
00 - Infrastructure
01 - Registration
02 - Login and JWT
03 - Profile
04 - Password and Logout
05 - Internal APIs
06 - Deactivation
07 - Resilience and Database
```

3. Open the collection environment and enter this JSON:

```json
{
  "gatewayUrl": "http://localhost:8080",
  "userServiceUrl": "http://localhost:8081",
  "eurekaUrl": "http://localhost:8761",
  "email": "gateway.insomnia.20260917@example.com",
  "directEmail": "direct.insomnia.20260917@example.com",
  "unknownEmail": "not.registered.20260917@example.com",
  "password": "Strong@Password123",
  "newPassword": "New@Password456",
  "mobileNo": "9876543210",
  "directMobileNo": "9765432109",
  "updatedMobileNo": "9123456789",
  "userId": "",
  "accessToken": "",
  "tamperedToken": "",
  "shortLivedToken": "",
  "internalServiceToken": "PASTE_THE_SAME_INTERNAL_SERVICE_TOKEN_HERE"
}
```

Change the date/suffix in the email variables each time you repeat the complete
run. This prevents an earlier database row from causing an unexpected duplicate.

Reference variables in Insomnia with syntax such as:

```text
{{ gatewayUrl }}
{{ email }}
{{ accessToken }}
```

Do not sync or export an environment containing actual access tokens or the
internal service token. Clear those values before sharing the collection.

## 3. Standard request configuration

For JSON requests select `Body -> JSON` and use:

```http
Content-Type: application/json
```

For protected public requests select `Auth -> Bearer Token` and enter:

```text
{{ accessToken }}
```

Alternatively, add this header manually:

```http
Authorization: Bearer {{ accessToken }}
```

For internal requests do not use a bearer token. Add:

```http
X-Internal-Service-Token: {{ internalServiceToken }}
```

## 4. Expected error structure

Controller and service errors should have this structure:

```json
{
  "timestamp": "2026-09-17T10:00:00Z",
  "traceId": "trace-value",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request details are invalid",
  "fieldErrors": {}
}
```

Gateway security failures already use this shape. Direct User Service security
filter failures may differ until the remaining User Service security error-handler
checkpoint is implemented.

## 5. Infrastructure tests

### INF-01: Discovery Server health

```http
GET {{ eurekaUrl }}/actuator/health
```

Expected: `200 OK`, with `status` equal to `UP`.

### INF-02: User Service health directly

```http
GET {{ userServiceUrl }}/actuator/health
```

Expected: `200 OK`, with `status` equal to `UP`.

### INF-03: API Gateway health

```http
GET {{ gatewayUrl }}/actuator/health
```

Expected: `200 OK`, with `status` equal to `UP`.

### INF-04: Eureka registrations

Open this in a browser or Insomnia:

```http
GET {{ eurekaUrl }}
```

Expected: `200 OK`. The Eureka dashboard must list `USER-SERVICE` and
`API-GATEWAY` as `UP`.

### INF-05: Unknown Gateway route

```http
GET {{ gatewayUrl }}/not-a-real-route
```

Expected: request denied; it must not route to User Service.

### INF-06: CORS preflight

```http
OPTIONS {{ gatewayUrl }}/api/v1/users/me
Origin: http://localhost:3000
Access-Control-Request-Method: GET
Access-Control-Request-Headers: Authorization,Content-Type
```

Expected: successful preflight response containing
`Access-Control-Allow-Origin: http://localhost:3000`.

Insomnia does not enforce browser CORS, so inspect the response headers manually.

## 6. Registration tests

### REG-01: Direct User Service registration

```http
POST {{ userServiceUrl }}/api/v1/auth/register
```

```json
{
  "name": "Direct Demo User",
  "email": "{{ directEmail }}",
  "password": "{{ password }}",
  "mobileNo": "{{ directMobileNo }}",
  "dateOfBirth": "2000-06-15"
}
```

Expected: `201 Created`, numeric `userId`, normalized email, and status `ACTIVE`.
The response must not contain `password` or `passwordHash`.

### REG-02: Registration through Gateway

```http
POST {{ gatewayUrl }}/api/v1/auth/register
```

```json
{
  "name": "Gateway Demo User",
  "email": "{{ email }}",
  "password": "{{ password }}",
  "mobileNo": "{{ mobileNo }}",
  "dateOfBirth": "2000-06-15"
}
```

Expected: `201 Created`, demonstrating Gateway-to-Eureka-to-User-Service routing.

Optional after-response script for this request:

```javascript
insomnia.test('Registration returns 201', () => {
  insomnia.expect(insomnia.response.code).to.eql(201);
  const body = insomnia.response.json();
  insomnia.expect(body.status).to.eql('ACTIVE');
  insomnia.expect(body).not.to.have.property('password');
  insomnia.expect(body).not.to.have.property('passwordHash');
  insomnia.environment.set('userId', String(body.userId));
});
```

### REG-03: Email normalization

Send a new registration using an uppercase email, for example:

```json
{
  "name": "Normalization User",
  "email": "NORMALIZED.20260917@EXAMPLE.COM",
  "password": "Strong@Password123",
  "mobileNo": "9654321098",
  "dateOfBirth": "2000-06-15"
}
```

Expected: `201 Created`; response and Oracle value are lowercase.

### REG-04: Duplicate normalized email

Repeat REG-02 using the same email with different letter casing.

Expected: `409 Conflict`, code `DUPLICATE_EMAIL`.

### REG-05: Missing required fields

```json
{}
```

Expected: `400 Bad Request`, code `VALIDATION_FAILED`, with field errors.

### REG-06: Invalid email

Use `not-an-email`.

Expected: `400 Bad Request`; `fieldErrors.email` explains the invalid format.

### REG-07: Weak password

Use `weakpassword`.

Expected: `400 Bad Request`; password validation fails.

### REG-08: Invalid mobile number

Use `1234567890` or a value with fewer than ten digits.

Expected: `400 Bad Request`; mobile validation fails.

### REG-09: Underage user

Use a date of birth less than 18 years before today's date.

Expected: `400 Bad Request`; age validation fails.

### REG-10: Short name

Use `Alex`.

Expected: `400 Bad Request`; the name must contain at least five characters.

### REG-11: Malformed JSON

Remove a closing quote or brace from the body.

Expected: `400 Bad Request`, with message `Request body is missing or malformed`.

## 7. Login and JWT tests

### AUTH-01: Successful login through Gateway

```http
POST {{ gatewayUrl }}/api/v1/auth/login
```

```json
{
  "email": "{{ email }}",
  "password": "{{ password }}"
}
```

Expected: `200 OK`, `tokenType` equal to `Bearer`, `expiresIn` equal to `1800`,
and a non-empty `accessToken`.

Add this after-response script to save the token:

```javascript
insomnia.test('Login returns a bearer token', () => {
  insomnia.expect(insomnia.response.code).to.eql(200);
  const body = insomnia.response.json();
  insomnia.expect(body.tokenType).to.eql('Bearer');
  insomnia.expect(body.expiresIn).to.eql(1800);
  insomnia.expect(body.accessToken).to.be.a('string').and.not.empty;
  insomnia.environment.set('accessToken', body.accessToken);
});
```

### AUTH-02: Uppercase/trimmed login email

Send the registered email in uppercase and with surrounding spaces.

Expected: `200 OK`; login lookup normalizes the email.

### AUTH-03: Wrong password

Use the registered email with `Wrong@Password123`.

Expected: `401 Unauthorized`, code `INVALID_CREDENTIALS`, message
`Invalid email or password`.

### AUTH-04: Unknown email

Use `{{ unknownEmail }}` with any valid-length password.

Expected: the same `401`, code, and message as AUTH-03. The API must not reveal
whether the email exists.

### AUTH-05: Missing bearer token

```http
GET {{ gatewayUrl }}/api/v1/users/me
```

Do not configure authentication.

Expected: `401 Unauthorized`, code `UNAUTHORIZED`.

### AUTH-06: Malformed bearer token

Use Bearer token value `not-a-jwt`.

Expected: `401 Unauthorized`.

### AUTH-07: Modified JWT signature

Copy `accessToken` into `tamperedToken`, change one character in the signature
section after the second period, and send it to the profile endpoint.

Expected: `401 Unauthorized`. A modified token must never reach the controller.

### AUTH-08: Wrong user's identity cannot be selected

With the valid token, try:

```http
GET {{ gatewayUrl }}/api/v1/users/999999
```

Expected: there is no public endpoint for choosing another user. Self-service uses
only `/me`. Modifying the JWT `sub` would invalidate the signature and return 401.

### AUTH-09: Trace ID at Gateway

Send a profile request with a UUID header:

```http
X-Trace-Id: 11111111-1111-1111-1111-111111111111
```

Expected: the Gateway response has the same `X-Trace-Id`. Error-body trace IDs may
not yet match User Service responses until trace-header alignment is implemented.

## 8. Profile tests

All requests in this section use Bearer `{{ accessToken }}`.

### PROF-01: Read profile

```http
GET {{ gatewayUrl }}/api/v1/users/me
```

Expected: `200 OK` with `userId`, name, normalized email, mobile number, date of
birth, `ACTIVE`, and timestamps. No password data may appear.

### PROF-02: Update name only

```http
PATCH {{ gatewayUrl }}/api/v1/users/me
```

```json
{
  "name": "Gateway Demo Updated"
}
```

Expected: `200 OK`; name changes and mobile number remains unchanged.

### PROF-03: Update mobile only

```json
{
  "mobileNo": "{{ updatedMobileNo }}"
}
```

Expected: `200 OK`; mobile number changes and name remains unchanged.

### PROF-04: Update both permitted fields

```json
{
  "name": "Gateway Final Name",
  "mobileNo": "{{ mobileNo }}"
}
```

Expected: `200 OK`; both permitted fields change.

### PROF-05: Empty update

```json
{}
```

Expected: `400 Bad Request`; at least one permitted field is required.

### PROF-06: Attempt to change protected fields

```json
{
  "name": "Allowed Name Change",
  "email": "attacker@example.com",
  "status": "DELETED",
  "userId": 999999,
  "passwordHash": "fake"
}
```

Expected: the request either rejects unknown fields or ignores them, depending on
Jackson configuration. If it succeeds, only `name` may change. A following GET and
Oracle query must prove that email, status, user ID, and password hash did not
change.

### PROF-07: Invalid permitted values

Use a four-character name or invalid mobile number.

Expected: `400 Bad Request` with field-specific validation errors.

### PROF-08: Concurrent update (advanced)

Send two PATCH requests for the same user as close together as possible using two
Insomnia tabs or the Collection Runner.

Expected design: if both read the same `UPDATED_AT`, only one update succeeds and
the other returns `409 CONCURRENT_UPDATE`.

This race is timing-dependent and may not reproduce manually. It requires a focused
automated concurrency test for deterministic verification.

## 9. Password change tests

### PASS-01: Weak new password

```http
PUT {{ gatewayUrl }}/api/v1/users/me/password
Authorization: Bearer {{ accessToken }}
```

```json
{
  "currentPassword": "{{ password }}",
  "newPassword": "weakpassword"
}
```

Expected: `400 Bad Request`; the stored password hash remains unchanged.

### PASS-02: Incorrect current password

```json
{
  "currentPassword": "Wrong@Password123",
  "newPassword": "{{ newPassword }}"
}
```

Expected: `401 Unauthorized`, code `INVALID_CURRENT_PASSWORD`.

### PASS-03: Successful password change

```json
{
  "currentPassword": "{{ password }}",
  "newPassword": "{{ newPassword }}"
}
```

Expected: `200 OK` with no body.

### PASS-04: Old password no longer works

Repeat AUTH-01 using `{{ password }}`.

Expected: `401 INVALID_CREDENTIALS`.

### PASS-05: New password works

Repeat AUTH-01 using `{{ newPassword }}`.

Expected: `200 OK`. Save the new `accessToken`; use it for the remaining tests.

## 10. Stateless logout tests

### LOGOUT-01: Logout without a token

```http
POST {{ gatewayUrl }}/api/v1/auth/logout
```

Expected: `401 Unauthorized`.

### LOGOUT-02: Logout with a token

```http
POST {{ gatewayUrl }}/api/v1/auth/logout
Authorization: Bearer {{ accessToken }}
```

Expected: `204 No Content`.

### LOGOUT-03: Demonstrate stateless behavior

Immediately reuse the same token for:

```http
GET {{ gatewayUrl }}/api/v1/users/me
```

Expected in V1: `200 OK`. Logout tells the client to delete the token; there is no
server-side token denylist, so an already-issued token remains valid until expiry.

## 11. Internal service API tests

Internal API calls go directly to port 8081 and use
`X-Internal-Service-Token`, not a bearer JWT.

### INT-01: Status without internal token

```http
GET {{ userServiceUrl }}/internal/v1/users/{{ userId }}/status
```

Expected: access denied (`401` or `403`), never `200`.

### INT-02: Status with invalid internal token

```http
X-Internal-Service-Token: wrong-token
```

Expected: access denied, never `200`.

### INT-03: Status with valid internal token

```http
GET {{ userServiceUrl }}/internal/v1/users/{{ userId }}/status
X-Internal-Service-Token: {{ internalServiceToken }}
```

Expected: `200 OK`:

```json
{
  "userId": 1,
  "status": "ACTIVE"
}
```

Only `userId` and `status` may be returned.

### INT-04: Missing user status

Use user ID `999999999` with the valid internal token.

Expected: `404 Not Found`, code `USER_NOT_FOUND`.

### INT-05: Resolve by mobile

```http
POST {{ userServiceUrl }}/internal/v1/users/resolve
X-Internal-Service-Token: {{ internalServiceToken }}
Content-Type: application/json
```

```json
{
  "mobileNo": "{{ mobileNo }}"
}
```

Expected: `200 OK` with only the matching `userId` and status.

### INT-06: Resolve invalid mobile

Use `123`.

Expected: `400 Bad Request`, code `VALIDATION_FAILED`.

### INT-07: Resolve unknown mobile

Use a valid-format mobile number that is not stored in the table.

Expected: `404 Not Found`, code `USER_NOT_FOUND`.

### INT-08: Internal path through Gateway

```http
GET {{ gatewayUrl }}/internal/v1/users/{{ userId }}/status
X-Internal-Service-Token: {{ internalServiceToken }}
```

Expected: request rejected. The Gateway must never expose `/internal/**`, even when
the internal token is correct.

## 12. Short-lived and expired JWT test

This test requires a temporary User Service restart. Run it only after the normal
profile and password tests.

1. Set `JWT_EXPIRY=PT1S` in the User Service run configuration.
2. Restart User Service.
3. Log in with the new password and save the result as `shortLivedToken` without
   replacing the main `accessToken`.
4. Confirm login returns `expiresIn: 1`.
5. Wait at least 65 seconds. Spring Security permits a small default clock skew.
6. Call the Gateway profile endpoint with `shortLivedToken`.

Expected: `401 Unauthorized` because the JWT is expired.

Restore `JWT_EXPIRY=PT30M`, restart User Service, log in again with the new
password, and update `accessToken` before continuing.

## 13. Deactivation tests — run last

### DEACT-01: Deactivate without JWT

```http
DELETE {{ gatewayUrl }}/api/v1/users/me
```

Expected: `401 Unauthorized`.

### DEACT-02: Deactivate authenticated user

```http
DELETE {{ gatewayUrl }}/api/v1/users/me
Authorization: Bearer {{ accessToken }}
```

Expected: `204 No Content`.

### DEACT-03: Login after deactivation

Log in using `{{ email }}` and `{{ newPassword }}`.

Expected: `403 Forbidden`, code `INACTIVE_USER`.

### DEACT-04: Existing token after deactivation

Use the previously issued token on:

```http
GET {{ gatewayUrl }}/api/v1/users/me
```

Expected: Gateway accepts the still-valid signature, but User Service checks the
database status and returns `403 INACTIVE_USER`.

### DEACT-05: Internal status after deactivation

Repeat INT-03.

Expected: `200 OK`, status `INACTIVE`.

### DEACT-06: Deactivate again

Repeat DEACT-02 with the existing token.

Expected: `403 INACTIVE_USER`; status cannot be changed through a public request.

## 14. Eureka and failure behavior

### RES-01: Gateway uses Eureka rather than a hard-coded address

With all applications running, REG-02 and AUTH-01 should succeed through port 8080.
This, together with the `lb://USER-SERVICE` route and Eureka registration, verifies
discovery-based routing.

### RES-02: User Service unavailable

1. Stop User Service.
2. Wait for Eureka/load-balancer state to update.
3. Send a public Gateway registration or login request.

Expected: request fails with a service-unavailable/gateway error; it must not reach
another hard-coded URL.

Restart User Service and confirm it returns to `UP` in Eureka before continuing.

### RES-03: Invalid Gateway public key

This is an optional security test:

1. Temporarily configure Gateway with a different RSA public key.
2. Restart Gateway.
3. Send a previously valid User Service token to `/api/v1/users/me`.

Expected: `401 Unauthorized` because the signature cannot be verified.

Restore the correct `JWT_PUBLIC_KEY` immediately afterward.

## 15. Oracle verification

Run read-only queries in SQL Developer after the API tests. Substitute the test
email where required.

### DB-01: Normalized email and status

```sql
SELECT user_id, email, mobile_no, status, created_at, updated_at
FROM app_user
WHERE email = 'gateway.insomnia.20260917@example.com';
```

Expected: lowercase email, numeric mobile, and final status `INACTIVE` after the
deactivation section.

### DB-02: Password is Argon2id, not plaintext

```sql
SELECT email,
       SUBSTR(password_hash, 1, 9) AS hash_scheme,
       LENGTH(password_hash) AS hash_length
FROM app_user
WHERE email = 'gateway.insomnia.20260917@example.com';
```

Expected: prefix identifies Argon2id and the stored value is not either submitted
password.

Do not copy or publish the complete hash.

### DB-03: Soft deactivation

```sql
SELECT user_id, status
FROM app_user
WHERE email = 'gateway.insomnia.20260917@example.com';
```

Expected: the row still exists and status is `INACTIVE`.

### DB-04: Updated timestamp

Compare `created_at` and `updated_at` after profile/password/deactivation changes.

Expected: `updated_at` is later, demonstrating that the Oracle update trigger ran.

### DB-05: Unique email constraint

REG-04 must return 409, and this query must return one row for the normalized email:

```sql
SELECT email, COUNT(*)
FROM app_user
WHERE email = 'gateway.insomnia.20260917@example.com'
GROUP BY email;
```

Expected count: `1`.

## 16. Final result checklist

Record pass/fail for every required behavior:

- [ ] Discovery Server health is UP.
- [ ] User Service health is UP.
- [ ] Gateway health is UP.
- [ ] Eureka lists User Service and Gateway.
- [ ] Direct registration succeeds.
- [ ] Gateway registration succeeds.
- [ ] Validation failures return 400.
- [ ] Duplicate normalized email returns 409.
- [ ] Argon2id hash is stored; plaintext is not stored.
- [ ] Correct login returns an RS256 JWT.
- [ ] Unknown email and wrong password share the generic message.
- [ ] Missing, malformed, modified, expired, and wrongly verified tokens are rejected.
- [ ] Profile reads use only JWT `sub`.
- [ ] Only name and mobile number can be updated.
- [ ] Password change requires the current password.
- [ ] Old password stops working and new password works.
- [ ] Logout returns 204 and remains explicitly stateless.
- [ ] Internal APIs require the internal service token.
- [ ] Internal APIs expose only user ID and status.
- [ ] Gateway does not expose `/internal/**`.
- [ ] Deactivation changes status to `INACTIVE` without deleting the row.
- [ ] Inactive users cannot log in or use protected User Service operations.
- [ ] No password, hash, private key, or JWT appears in application logs.

## 17. After live testing

From the repository root, run the automated build verification:

```powershell
cd "C:\Users\Nishanth S\Desktop\Work\Training\Final Project\Contact_Transactions_App"
.\mvnw.cmd clean verify
```

Keep the Insomnia result screenshots or exported test report if the project review
requires evidence. Before exporting the collection, clear `accessToken`,
`tamperedToken`, `shortLivedToken`, and `internalServiceToken`.

## Insomnia references

- [Insomnia environments](https://developer.konghq.com/insomnia/environments/)
- [Insomnia pre-request and after-response scripts](https://developer.konghq.com/insomnia/scripts/)
- [Saving a response value as an environment variable](https://developer.konghq.com/how-to/set-a-value-from-a-response-as-an-environment-variable/)
