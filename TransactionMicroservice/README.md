# Transaction Microservice

This service owns application accounts, wallets, balance movements, and transaction history. It is registered with Eureka as `TransactionMicroservice` and exposes its public API through the API Gateway under `/api/v1/money/**`.

## Responsibilities

- Create an account for the authenticated user with a simulated opening balance.
- Maintain one wallet per user.
- Move money from an account into the owner's wallet (`A2W`).
- Move money between two users' wallets (`W2W`).
- Return transaction history visible to the authenticated user.

The service owns its own Oracle tables. User Service and Contact Service must not read or write this database directly.

## Authentication and service-to-service calls

### Public API

All `/api/v1/money/**` routes require a User Service JWT:

```http
Authorization: Bearer <access-token>
```

The service validates the JWT with the User Service RSA public key and treats JWT `sub` as the authenticated numeric user ID. Clients must not submit a sender user ID, account owner ID, or wallet owner ID in request bodies.

### Internal API

Internal routes are not for browsers or API Gateway routing. They require:

```http
X-Internal-Service-Token: <INTERNAL_SERVICE_TOKEN>
```

For this first version, User Service and Transaction Service share the same `INTERNAL_SERVICE_TOKEN`. It is injected through each service's environment and must never be committed to source control.

## Public API

Base path: `/api/v1/money`

### Create an account

```http
POST /api/v1/money/accounts/addAccount
Authorization: Bearer <access-token>
```

No request body is required. The owner comes from JWT `sub`. The service generates an opening balance using its configured simulator range.

Response: `201 Created`

```json
{
  "accountId": 10,
  "balance": 2500.00,
  "status": "ACTIVE",
  "createdAt": "2026-09-18T10:30:00",
  "updatedAt": "2026-09-18T10:30:00"
}
```

### List the current user's accounts

```http
GET /api/v1/money/accounts/getAccounts
Authorization: Bearer <access-token>
```

Response: `200 OK`

```json
[
  {
    "accountId": 10,
    "balance": 2500.00,
    "status": "ACTIVE",
    "createdAt": "2026-09-18T10:30:00",
    "updatedAt": "2026-09-18T10:30:00"
  }
]
```

### Get an account balance

```http
GET /api/v1/money/accounts/getBalance?accountId=10
Authorization: Bearer <access-token>
```

The account must belong to JWT `sub`.

Response: `200 OK` with an `AccountResponse` object.

### Add funds to a wallet

```http
POST /api/v1/money/wallet/addFunds
Authorization: Bearer <access-token>
Content-Type: application/json

{
  "accountId": 10,
  "amount": 500.00
}
```

The service verifies the current user is active through User Service, locks the account and current user's wallet, then moves the amount from the account to the wallet. The account must belong to the JWT user and be `ACTIVE`.

Transaction type: `A2W`.

### Pay another user's wallet

```http
POST /api/v1/money/wallet/makePayment
Authorization: Bearer <access-token>
Content-Type: application/json

{
  "receiverUserId": 99,
  "amount": 125.00
}
```

The sender is always JWT `sub`; it is not accepted in the body. The service verifies both sender and receiver are active through User Service, locks both wallet rows in a consistent order, then moves the amount.

Transaction type: `W2W`.

### Get current wallet balance

```http
GET /api/v1/money/wallet/getBalance
Authorization: Bearer <access-token>
```

Response: `200 OK`

```json
{
  "walletId": 25,
  "balance": 625.00,
  "createdAt": "2026-09-18T10:30:00",
  "updatedAt": "2026-09-18T11:00:00"
}
```

### List visible transactions

```http
GET /api/v1/money/transactions/getAll?page=0&size=20
Authorization: Bearer <access-token>
```

`page` starts at `0`. `size` must be from `1` to `100`. Results are newest first.

Completed W2W transactions are visible to both wallet owners. Failed W2W attempts are visible only to the source-wallet owner, so an intended recipient does not see money that never moved. A2W transactions are visible to the source-account owner. The response is a standard Spring `Page` containing `content` and paging metadata.

### Get a single visible transaction

```http
GET /api/v1/money/transactions/get/500
Authorization: Bearer <access-token>
```

Only a transaction visible to the JWT user can be returned.

### Transaction response

`addFunds`, `makePayment`, and transaction query endpoints return this shape:

```json
{
  "transactionId": 500,
  "transactionType": "W2W",
  "sourceWalletId": 25,
  "sourceUserId": 7,
  "sourceUserName": "Alice Sharma",
  "sourceAccountId": null,
  "destinationWalletId": 90,
  "destinationUserId": 12,
  "destinationUserName": "Bob Tester",
  "amount": 125.00,
  "status": "COMPLETED",
  "createdAt": "2026-09-18T11:00:00",
  "completedAt": "2026-09-18T11:00:00"
}
```

For `A2W`, `sourceWalletId`, `sourceUserId`, and `sourceUserName` are `null`, while `sourceAccountId` is populated. For `W2W`, `sourceAccountId` is `null`. The user IDs belong to the corresponding wallets. Transaction query endpoints resolve registered profile names in one internal User Service request per page; name fields remain nullable when a user is missing/deleted or Identity Service is unavailable. The frontend should prefer its current user's saved contact nickname, then use the registered name as the fallback. Money command responses may also leave the name fields `null` because the requested change is applied to transaction queries.

If funds are insufficient, the service records and returns a transaction with `status: "FAILED"`; balances are unchanged.

## Internal API

### Create a wallet after user registration

```http
POST /internal/v1/wallets
X-Internal-Service-Token: <INTERNAL_SERVICE_TOKEN>
Content-Type: application/json

{
  "userId": 42
}
```

Response: `201 Created`

```json
{
  "walletId": 25,
  "balance": 0.00,
  "createdAt": "2026-09-18T10:30:00",
  "updatedAt": "2026-09-18T10:30:00"
}
```

This endpoint is called by User Service after it successfully creates a user. It is idempotent for normal retries: if a wallet already exists for that user, the existing wallet is returned.

User Service must call this endpoint directly through Eureka, not through API Gateway. The User Service HTTP client uses the Transaction Service's Eureka service ID, for example:

```text
http://TRANSACTIONMICROSERVICE/internal/v1/wallets
```

Confirm the exact registered service ID in the Eureka dashboard.

## Required User Service contract

Transaction Service checks user status before money-changing operations. User Service must provide this internal endpoint:

```http
GET /internal/v1/users/{userId}/status
X-Internal-Service-Token: <INTERNAL_SERVICE_TOKEN>
```

Expected response:

```json
{
  "status": "ACTIVE"
}
```

Any status other than `ACTIVE`, an unavailable User Service, or an invalid internal token blocks the money operation.

## Contact Service integration

The implementation checks sender and receiver activity through User Service and calls `POST /internal/v1/contacts/payment-eligibility` before entering the local wallet transaction. Contact Service re-resolves the sender's stored contact phone and requires it to map to the requested active receiver. A stale or reassigned phone therefore rejects the payment before wallet locks, balance changes, or transaction-row creation.

For a stale phone, `makePayment` returns HTTP `403` with a JSON body such as:

```json
{
  "status": 403,
  "errorCode": "CONTACT_PHONE_MISMATCH",
  "message": "This contact’s phone number no longer matches the registered user. Update the contact before transferring money.",
  "path": "/api/v1/money/wallet/makePayment",
  "traceId": "31ac7274-1405-40a2-8a0f-8fb505f67dd5",
  "fieldErrors": {}
}
```

## Validation and business rules

- Amount must be positive and have at most 18 whole digits and 2 fractional digits.
- A user cannot pay their own wallet.
- The source account must belong to the JWT user and be `ACTIVE`.
- A wallet can only be credited within the supported money range.
- One user has one wallet; `wallet.user_id` is unique.
- Concurrent money operations use pessimistic database locks to prevent double spending.

## Runtime configuration

Required environment variables:

```text
JWT_PUBLIC_KEY=<X.509 RSA public key from User Service>
INTERNAL_SERVICE_TOKEN=<shared backend secret>
```

Optional environment variables with local defaults:

```text
EUREKA_URL=http://localhost:8761/eureka/
USER_SERVICE_ID=USER-SERVICE
```

The database configuration is currently local-development configuration. Production must use a dedicated Oracle application user and environment-managed database credentials.

## Current limitations

- Transaction filtering/search is not implemented yet.
- Contact eligibility validation is not implemented yet.
- A global API exception-response format is not implemented yet.
- Oracle integration and API/security tests still need to be added.
