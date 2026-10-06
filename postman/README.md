# Extended Postman end-to-end collection

Import `Contact-Transactions-Integration.postman_collection.json` into Postman. It contains **154 requests in nine ordered folders**, including optional cleanup requests.

## Run

1. Start Discovery, User Service, Contact Service, Transaction Service and API Gateway with their database and service-to-service configuration working.
2. Set the **collection variable** `gatewayBaseUrl` (default `http://localhost:8080`). All public requests use the gateway; the checked-in gateway permits contact routes.
3. Run the **entire collection, in order, with one iteration**, using Collection Runner. Use a current Postman version supporting `pm.execution.skipRequest()` and `pm.execution.setNextRequest()`.
4. Inspect the Tests results, including failures marked **known gap** or **ownership regression**. Expected rejection responses are successful tests only when their assertions pass. A server error does not count as a valid rejection.

The first request generates new emails and ten-digit phone numbers for Alice, Bob, Charlie and Diana, then clears prior run IDs, tokens and state. Run it again only to start a new dataset. Generated phones start with 6–9 to match the current profile API. Random phone collisions remain possible; a registration conflict stops setup rather than reusing another user's identity. Collection variables take precedence over environment/data variables; edit configuration in the collection itself.

Successful registration, login, contact setup and main money operations capture their response IDs/tokens. Unexpected setup responses stop the Runner. Other assertion failures remain visible while later checks continue. Running an individual request or folder requires the preceding setup and the expected data state; this is not a collection of independent requests.

## Coverage

| Folder | Checks |
| --- | --- |
| 01 | Four fresh registrations, separate logins and profile verification |
| 02 | Registration phone formats, duplicate email/phone, invalid name/email/password/DOB, wrong credentials and unauthenticated access |
| 03 | Linked/unlinked contacts, listing, reading, renaming, deletion, duplicate/self/unregistered links, invalid phones and cross-user ownership |
| 04 | Zero opening wallets, funding accounts, INR 500 top-up, payments to three recipients, exact wallet/account balances |
| 05 | Zero/negative/overprecision/overflow/null amounts, self/non-contact transfers, foreign account debit, FAILED insufficient-funds rows and unchanged balances |
| 06 | Phone update validation, duplicate phone, trimming, persisted change, stale contact rejection, relinking, successful payment and reuse of Bob's released phone by Diana |
| 07 | Expected transactions in each user's history, participant isolation, no committed PENDING rows, transaction detail access and pagination validation |
| 08 | Profile edits, password validation/change, old password rejection, new login and token revocation checks |
| 09 | Optional deactivation of this run's four generated users and rejected access/login afterwards |

Phone validation includes nine/eleven digits, letters, country prefix, internal spaces, empty/null values and Unicode digits. Profile/registration checks also cover the current API's invalid starting-digit rule. Contact validation follows its separate ten-ASCII-digit rule.

Alice's expected final wallet is INR 310; Bob's is INR 110, Charlie's INR 50 and Diana's INR 30. The total remains INR 500. Alice's funding account must decrease by exactly INR 500; Bob's account must remain unchanged. Failed money requests must not change those balances.

## Current implementation versus project requirements

This collection targets the actual checked-in public routes and response fields. The existing application uses numeric IDs and bearer tokens; the collection does not change the architecture or endorse that as the final project security design. Registration currently provisions a wallet, so the existing `addAccount` endpoint supplies the funding accounts for these tests. No internal provisioning workaround or internal service secret is included.

Some assertions deliberately expose gaps visible in source review:

- Phone changes do not currently clear incoming stored contact links. The `Stored incoming contact link is cleared [known gap]` assertion requires null `linkedUserId` and false `linkedToRegisteredUser`. The separate stale-payment rejection checks must still pass, then the collection explicitly relinks the contact.
- Password change and logout do not currently revoke issued bearer tokens. The two revocation checks require HTTP 401 or 403, so continued access is reported as a failure.
- Foreign-account and foreign-transaction lookups throw `ResourceNotFoundException`, which has no explicit HTTP mapping in the inspected Transaction Service handler. The ownership regression requests expect HTTP 404; HTTP 500 is an API defect, not a passing ownership check.
- Profile phones currently require an initial 6–9, contact names use a different length policy, and profile deletion currently deactivates a user. These differ from the design requirements. This collection does not claim to verify permanent cross-service deletion or deletion of wallet/account data.

## Cleanup and reruns

`enableCleanup` defaults to `false`; all folder 09 requests are skipped, including its login. Set the collection variable to `true` before a full run to deactivate only the generated users at the end. The current endpoint leaves data behind; deactivation is not permanent cleanup. Earlier runs are not deleted by later runs.

The normal run changes generated Alice's password and logs her out. Her new password is stored in `aliceNewPassword`. Other generated user credentials and tokens are available in collection variables; avoid sharing an exported collection containing populated tokens.

No money POST is automatically retried. After a timeout or uncertain outcome, inspect history and balances before explicitly trying again. This sequential collection does not establish deduplication, concurrency safety, lifecycle race handling or Oracle rollback correctness.

## Verification status

Collection JSON, request-body templates, variable references, embedded JavaScript syntax and fresh-run initialization are checked locally. No live services or Oracle database were exercised while preparing this artifact. Run the collection against your environment to obtain end-to-end results.
