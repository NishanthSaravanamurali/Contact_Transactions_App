# Transaction service layer

## Structure

`abstractions` contains the contracts consumed by controllers.
`implementations` contains Spring services implementing those contracts.
Names retain the Query/Command distinction: queries read; commands change state.

| Contract | Implementation | Operations |
| --- | --- | --- |
| AccountQueryService | AccountQueryServiceImpl | getAccounts, getBalance |
| WalletQueryService | WalletQueryServiceImpl | getBalance |
| TransactionQueryService | TransactionQueryServiceImpl | getById, getAll |
| AccountCommandService | AccountCommandServiceImpl | addAccount |
| WalletCommandService | WalletCommandServiceImpl | addFunds, makePayment |

Inject an interface into a controller, for example `WalletQueryService`.
Supply currentUserId from authenticated server context, never a request field.
Authentication must reject nonexistent/inactive profiles on every request.
Query services scope data to the current user; they do not implement authentication.
History uses unsorted PageRequest objects with size 1–100; repository ordering
is createdAt descending then transactionId descending.

## Current design

Uses the team's handoff: numeric IDs, account/wallet/transactions entities,
NUMBER(20,2), and the requested additional-account operation. No entity,
repository, security configuration or database credential was changed here.

Opening account balances are simulated integer INR, inclusive bounds:
- simulator.account.opening-min (default 1000)
- simulator.account.opening-max (default 10000)

These optional settings have defaults; no properties file changes are necessary.
Account creation does not provision a wallet or register an Identity profile.
Repeated explicit calls create additional accounts; there is no request deduplication.

## Local money transaction

GuardedLocalTransaction obtains external lifecycle coordination first, then uses
TransactionTemplate to start/commit/roll back the local database transaction while
coordination remains held. It rejects an existing caller transaction; controllers
must not wrap money calls in @Transactional.

A2W locks the owned account, then the user's wallet.
W2W locks wallets through separate single-row queries in ascending userId order,
including payments in the opposite direction. Future operations touching these
rows must follow the same ordering: accounts first, wallets by userId.

Both debit and credit operate on managed entities. saveAndFlush records a terminal
transaction and flushes balance updates in the same transaction. Technical errors
roll back the whole local transaction. Insufficient funds returns a FAILED response
with unchanged balances and null completedAt. Controllers must inspect response
status instead of presenting every returned transaction as a successful payment.
Destination overflow is rejected before changing either balance.
No committed PENDING transaction or automatic money retry is introduced.
After an uncertain response, clients must check balance/history before explicitly
attempting another payment.

## Required integration, not implemented here

UserOperationGuard is a required integration port, NOT an implemented protocol.
No default/pass-through production bean is provided. Until an adapter is supplied,
Spring startup will fail with a missing UserOperationGuard dependency. This is
intentional; do not add a no-op implementation to enable transfers.

The adapter must validate active profiles and recipient/contact eligibility and
coordinate deletion, locking and remapping across the entire local transaction.
A single pre-transfer lookup cannot meet that contract. Failure recovery, in-flight
requests and coordination release after an uncertain commit must be agreed in the
microservices HLD before an adapter is implemented. The existing receiverUserId-only
request does not carry a contact ID; the team must settle how owned-contact
confirmation is supplied/validated when defining that integration API.

Controllers, JWT configuration, registration/wallet provisioning, profile deletion
and the Identity/Contact adapters remain separate work. The new services do not
claim those workflows are implemented. Deletion must be designed against the
team's current wallet-reference transaction schema; do not apply the older
payer/payee cleanup design unchanged.

getBasedOnConditions remains the later filter task from the handoff. The existing
repository has no filtered-query contract; add database-level filtering there
before exposing filters. Do not filter a page in memory.

## Validation

ServiceLayerTests uses mocked repositories and a tracking Spring transaction
manager. It checks money rules, ownership, lock call order, and that commit/rollback
happens while coordination is held. It does not start Spring or connect to Oracle.
The test guard is only a test fixture.

Real Oracle tests are still required for row locking under concurrency, rollback
restoration, schema constraints, mappings and paging query execution. Cross-service
lifecycle races require integration tests once the protocol/adapter exists.

