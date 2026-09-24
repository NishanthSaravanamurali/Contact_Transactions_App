# Payment notifications: contact name first, registered name fallback

## What changed

For Priya (sender) paying Arjun (receiver):

1. The existing protected User Service status lookup now includes Priya's registered name.
2. The existing Contact Service payment-eligibility lookup still checks that Priya
   has Arjun saved and that the receiver is active.
3. In that same internal HTTP response, Contact Service also looks in **Arjun's**
   contacts for Priya. It returns senderNameForReceiver, or null if no reverse
   linked contact exists.
4. Transaction Service selects the receiver's saved label, otherwise Priya's
   registered name. It performs these HTTP checks before acquiring wallet locks.
5. A successful makePayment saves its event in payment_outbox in the same Oracle
   transaction as the balance changes and transaction row.
6. A background publisher delivers the stored JSON to payments.completed.v1 using
   receiverUserId as the Kafka key. Notification Service consumes its existing flat
   event contract and displays "<selected name> has sent you ₹500.00".

Examples:
- Arjun saved Priya as "Priya Office": "Priya Office has sent you ₹500.00".
- Arjun has not saved Priya: "Priya Sharma has sent you ₹500.00", using her registered name.

This does not change payment eligibility. The sender must still have the receiver
saved, but the receiver does not have to have the sender saved.
A Contact/User Service failure is an error, NOT a missing-contact fallback.
If no valid display name can be obtained, the payment does not start.

No additional HTTP request was introduced for display-name resolution. Existing
user/status and contact/eligibility responses were extended. Deploy the User and
Contact Service changes before the new Transaction Service.

The receiver's private label stays inside internal responses and the payment event.
The public payment response has NOT been extended with that label. Restrict Kafka
topic access to trusted services. Names are snapshots: later renames do not change
already queued/stored notifications. The lookup uses linkedUserId, not a guessed
phone-number match; an unlinked contact falls back to the registered name.

## Required database setup (NOT executed automatically)

Run docs/sql/payment-outbox.sql once in the **Transaction Service's Oracle schema**
before deploying/restarting the new Transaction Service.

It adds only payment_outbox. It does not modify the existing transactions,
notifications, wallets, contacts or User Service outbox tables.

The service uses ddl-auto=validate, so it will refuse to start if the new table is
missing. No credentials or live database data were changed by this implementation.

## Outbox delivery and cleanup

- Only successful wallet-to-wallet payments create rows. Failed payments and top-ups do not.
- transaction_id is the outbox primary key; no extra event UUID is needed.
- Saving the outbox row fails -> the payment/balance transaction rolls back.
- Kafka unavailable -> the committed payment stays valid; its event stays pending.
- Publisher checks every 5 seconds by default, up to 50 eligible rows per pass.
- Each publication locks its outbox row, not wallets, and waits for broker acknowledgement.
- Acknowledged rows get published_at; failures keep that field null and back off
  from 5 seconds to at most 5 minutes between attempts.
- Publication uses a separate database transaction per row. A send succeeding but
  its database commit failing can cause a repeat delivery. Notification Service's
  unique transaction_id makes that safe.
- Cleanup runs hourly, deleting only rows whose published_at is older than 24 hours.
  It processes up to 20 batches of 50 per run; large backlogs may take further runs.
  Pending/failed rows are NEVER removed by this cleanup.
- Cleanup applies ONLY to payment_outbox. The pre-existing User Service outbox
  retention policy is unchanged.
- Kafka acknowledgement does not mean the notification has already been consumed.
  Configure Kafka retention for expected outages. After three processing failures,
  the consumer logs and skips the event; a notification may remain missing.

Environment overrides:
- PAYMENT_COMPLETED_TOPIC (default payments.completed.v1; must match Notification Service)
- PAYMENT_OUTBOX_ENABLED (default true)
- PAYMENT_OUTBOX_POLL_MS (default 5000)
- PAYMENT_OUTBOX_RETENTION_HOURS (default 24, minimum 1)

Disabling the publisher does not disable transactional outbox writes.
It deliberately leaves pending rows until publishing is enabled again.

## Kafka and running the app

Kafka must run separately. Create payments.completed.v1 before starting Notification Service.
The existing user.lifecycle.v1 Kafka flow was preserved.

Configure Notification Service's NOTIFICATION_DB_URL, NOTIFICATION_DB_USERNAME,
NOTIFICATION_DB_PASSWORD, JWT_PUBLIC_KEY and Kafka bootstrap address as described
in NotificationService/.env.example. No .env files were edited or copied.

Start the updated User/Contact services, Transaction Service, Notification Service
and gateway. The new gateway route forwards both /api/v1/notifications and its
subpaths to lb://NOTIFICATION-SERVICE, and requires JWT authentication.
No gateway response timeout is configured in this checkout; preserve SSE streaming
when introducing a reverse proxy or additional timeout configuration.

Frontend bell wiring remains separate. The launcher now includes NotificationService
on port 8084. See run-backend-and-kafka.md for Kafka setup and startup/shutdown.
The notification table's All/Read/Unread history and notification code are unchanged.

## Tests

Run the isolated backend suites from the backend root:

~~~powershell
mvn -B -ntp -pl user-service,Contact_Service,TransactionMicroservice,api-gateway "-Dtest=*Test,*Tests,!TransactionMicroserviceApplicationTests,!ContactServiceApplicationTests,!ContactRepositoryOracleTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
~~~

These tests do not require a live Oracle database. PaymentOutboxTransactionTest uses
H2 to check atomic rollback, pending/acknowledged states, backoff/retry and cleanup.
The name-resolution tests mock the existing HTTP calls and check correct contact
direction, fallback, unavailable-service errors and unchanged eligibility.
Gateway tests verify authentication and route configuration; they do not exercise
a running Eureka/gateway/SSE deployment.

A live Oracle/Kafka end-to-end smoke test remains necessary after applying the SQL
and supplying environment configuration. An API payment timeout/failure can be
ambiguous; don't blindly retry a payment merely because a notification is delayed.

## Existing unrelated test failure

The broad regression command above currently encounters one pre-existing failure:
ContactMapperTest.demonstratesThatNumericStorageCannotPreserveALeadingZero expects
0123456789 to be accepted, while the unchanged ContactMapper only accepts numbers
matching [6-9][0-9]{9}. Neither the mapper nor that test was changed here.

To run/package the otherwise passing backend suites while explicitly excluding
that one known test (as well as live-Oracle/startup tests), use:

~~~powershell
mvn -B -ntp -pl user-service,Contact_Service,TransactionMicroservice,api-gateway "-Dtest=*Test,*Tests,!TransactionMicroserviceApplicationTests,!ContactServiceApplicationTests,!ContactRepositoryOracleTest,!ContactMapperTest#demonstratesThatNumericStorageCannotPreserveALeadingZero" "-Dsurefire.failIfNoSpecifiedTests=false" verify
~~~
