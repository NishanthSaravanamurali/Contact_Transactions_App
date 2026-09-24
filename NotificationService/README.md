# Notification Service

This standalone service belongs to the separate NotificationService branch/checkout.
It stores one receiver notification per successful wallet-to-wallet transaction,
consumes Kafka events, and exposes authenticated REST and SSE endpoints.

## Implemented scope

- Oracle notifications table mapping; no schema creation or data migration at runtime.
- Paginated All, Unread, and Read views; unread count; mark-one/all-read.
- RSA JWT verification and receiver ownership checks on every user operation.
- Kafka consumption, transaction-ID deduplication, bounded retries and dead-letter publishing.
- Best-effort live SSE delivery after notification/read-state database commits.
- Isolated tests using H2, generated test RSA keys and an embedded Kafka broker.

The existing Transaction Service, API gateway, frontend and launch scripts have NOT
been modified. There is no public endpoint for creating notifications.

## Prerequisites and configuration

Use a JDK supported by your existing Spring Boot 4.1.1 project and Maven (or mvnw.cmd).
The project retains its Java 17 compilation target and WAR packaging.

1. Use the Oracle schema where you created the notifications table. The required
   columns are notification_id, transaction_id, sender_user_id, receiver_user_id,
   message, created_at and read_at. transaction_id must have the unique constraint
   uq_notifications_transaction. Both timestamps are TIMESTAMP(6) WITH TIME ZONE.
2. Set the environment variables listed in .env.example in your IDE or shell.
   .env.example is documentation: Spring Boot does not automatically read .env files.
   NOTIFICATION_DB_URL/USERNAME/PASSWORD refer to this service's schema.
   Do not put a private JWT key in this process environment.
3. JWT_PUBLIC_KEY must contain the existing User Service's X.509 PEM RSA public key.
   Actual newlines or literal backslash-n sequences are supported. Tokens must use
   RS256, have a positive numeric user ID in sub and contain an unexpired exp claim.
   Current validation matches the existing subject-based token contract; agree
   issuer/audience claims across services before enabling extra issuer/audience checks.
4. Start Kafka separately and create the two topics below before starting this service.
   Topic auto-creation is disabled for the consumer.
5. Run Eureka, or set EUREKA_ENABLED=false for standalone development.

Default port: 8084. Eureka service ID: notification-service.
Kafka bootstrap server default: localhost:9092.

Topic names (overridable by environment):

- payments.completed.v1
- payments.completed.v1.notification-dlt

Provision these topics with appropriate partitions, retention, replication and ACLs.
The payment producer needs write access only to the payment topic; this service
needs read/group access there and write access to its dead-letter topic. Configure
Kafka TLS/SASL through Spring Kafka properties outside local development.
Kafka is a separate process; the Maven dependency does not start a broker.

From this service directory in PowerShell:

~~~powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
~~~

The tests do not require your Oracle credentials or a running Kafka/Eureka instance.
Only tests use H2 and create/drop their test schemas. Normal startup uses
spring.jpa.hibernate.ddl-auto=validate and will fail if the Oracle schema is missing
or incompatible. Oracle-specific behavior still needs a real-environment smoke test.

Health: GET http://localhost:8084/actuator/health (public, no database details).
A green health response is not proof that the transaction publisher is connected.

## Payment event contract

Publish UTF-8 JSON to payments.completed.v1. Suggested Kafka key: receiverUserId as
a string, so events for one receiver go to the same partition.

~~~json
{
  "transactionId": 123,
  "senderUserId": 1,
  "senderName": "User1",
  "receiverUserId": 2,
  "amount": "500.00",
  "currency": "INR",
  "completedAt": "2026-09-23T10:00:00Z"
}
~~~

Only committed, successful wallet-to-wallet payments belong on this topic.
Sender identity and display name must come from trusted backend data, not
unverified frontend input. senderName is a snapshot, maximum 100 characters.
Amount is positive, at most 18 integer digits and 2 decimal places; currency is INR.
The current transaction timestamps have no offset: the future publisher must
explicitly choose/convert their timezone instead of blindly appending Z.

No JWT or separate event UUID is included. The table's unique transaction_id
constraint enforces the current one-receiver-notification-per-payment rule.
Replaying an existing transaction does not create another row or make a read
notification unread again.

A notification message is constructed as "User1 has sent you ₹500.00".
createdAt is when this service stores the notification, not payment completion time.
No cross-service JPA relationships or database joins are used.

The consumer commits a successful record's offset only after database commit.
Database failures get two retries, one second apart. Malformed/invalid events go
directly to the DLT; exhausted transient failures also go there. DLT send failures
are propagated so the original record is not acknowledged as recovered. Monitor
the DLT and replay corrected/transiently failed records into the payment topic after
fixing the cause; transaction-ID deduplication makes repeated valid deliveries safe.
Do not blindly replay invalid records.

This is at-least-once event handling with database deduplication, not distributed
exactly-once delivery. Kafka producer idempotence alone does not close the
transaction database-to-Kafka publication gap: that still requires a transaction-side outbox.

## REST API

All these routes require Authorization: Bearer <access-token>.
The service derives the user ID from the verified JWT; clients cannot select another
receiver. Missing and other users' notification IDs both return 404.

| Method | Route | Result |
| --- | --- | --- |
| GET | /api/v1/notifications?filter=all&page=0&size=20 | Paginated history |
| GET | /api/v1/notifications?filter=unread | Unread history |
| GET | /api/v1/notifications?filter=read | Read history |
| GET | /api/v1/notifications/unread-count | {"unreadCount": 2} |
| PATCH | /api/v1/notifications/{id}/read | 204; idempotently mark read |
| PATCH | /api/v1/notifications/read-all | 204; mark current user's notifications read |
| GET | /api/v1/notifications/stream | text/event-stream |

History is sorted by createdAt DESC, notificationId DESC. Page is zero-based;
size is 1–100. The stable response envelope is:

~~~json
{
  "content": [
    {
      "notificationId": 10,
      "transactionId": 123,
      "message": "User1 has sent you ₹500.00",
      "createdAt": "2026-09-23T10:00:01Z",
      "readAt": null
    }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 1,
    "totalPages": 1
  }
}
~~~

Reading sets readAt and keeps the row in All/Read. The badge counts readAt IS NULL.
Repeated mark-read calls preserve the original read time.

## SSE contract and frontend integration (not implemented here)

Use authenticated fetch streaming so the browser can send the Authorization header.
Do not place JWTs in URLs. Native EventSource cannot set that header.
Do not use the ordinary API client's short request timeout for this long-lived request.

Events:

- ready: {"refresh":true}. On EVERY connection/reconnection, reload history and
  unread count from REST after receiving this event.
- notification-created: NotificationResponse. Reload the active tab/count; render
  message as text, not HTML.
- notifications-read: {"refresh":true}. Reload the active tab/count across tabs.
- A keepalive comment approximately every 15 seconds detects disconnected clients.

Coalesce overlapping refreshes to avoid stale responses overwriting newer state.
There is no periodic history polling and no unread counter stored in a process cache.
Refresh REST state on tab focus as well, since live delivery is best-effort.
The stream expires after at most five minutes or at JWT expiry, whichever is earlier.
Reconnect using a valid token; respect retry/backoff and connection-limit responses.
User logout must cancel the stream.

SSE events are transient hints; the database remains the source of truth. There is
no Last-Event-ID replay buffer. A crash between database commit and SSE dispatch can
lose that live hint, but not the persisted notification.

Connection limits default to five per user and 1000 overall. Writes use a bounded
executor; overloaded/disconnected clients are closed so they can reconnect/reload.

Run one Notification Service instance for now. Kafka consumer groups distribute
events across instances, while SSE connections are node-local. Multiple instances
need a separate cross-node fanout mechanism (sticky sessions alone do not solve this).
Gateway/proxy streaming must disable buffering and permit the long-lived connection.
CORS should be configured at the gateway for the frontend's origin.

## Remaining integration work

1. Transaction Service: save an outbox record with the successful payment and publish
   committed records to Kafka, using the exact contract above.
2. Gateway: route /api/v1/notifications/** (including the base route) to this service
   and configure streaming timeouts.
3. Frontend: connect the bell, tabs, unread count, read actions and authenticated SSE.
4. Launchers: add this separate service and decide how Kafka is started/stopped.

None of these changes are included in this service-only implementation.
