# Transaction Service Kafka Integration

This document is the implemented User Service contract for the Transaction
Service team. It explains how a newly registered user reaches Transaction Service
and how Transaction Service should create that user's wallet safely.

## Integration change

User Service no longer calls:

```http
POST /internal/v1/wallets
```

after registration. The old synchronous REST callback and local Spring event have
been removed from User Service.

The new flow is:

```text
User registration
  -> APP_USER and OUTBOX_EVENT are inserted in one Oracle transaction
  -> registration commits and returns independently of Kafka availability
  -> User Service's scheduled publisher reads the pending outbox row
  -> User Service publishes UserRegistered to Kafka
  -> Transaction Service consumes UserRegistered
  -> Transaction Service creates or finds the wallet for that userId
```

## Kafka contract

Topic:

```text
user.lifecycle.v1
```

Key serializer:

```text
StringSerializer
```

The key is the decimal string representation of the User Service ID:

```text
42
```

Value serializer:

```text
StringSerializer
```

The value is a JSON event envelope:

```json
{
  "eventId": "62ed213e-faf0-43bf-9f0f-0b932eae5ee9",
  "eventType": "UserRegistered",
  "eventVersion": 1,
  "aggregateId": 42,
  "occurredAt": "2026-09-22T10:00:00Z",
  "payload": {
    "userId": 42
  }
}
```

Field meanings:

| Field | Type | Meaning |
| --- | --- | --- |
| `eventId` | UUID string | Globally unique event identity used for deduplication |
| `eventType` | string | Currently `UserRegistered` |
| `eventVersion` | integer | Contract version; currently `1` |
| `aggregateId` | number | Numeric User Service ID |
| `occurredAt` | ISO-8601 UTC string | Time the outbox event was created |
| `payload.userId` | number | User whose wallet should be created |

The event deliberately does not contain email, mobile number, date of birth,
passwords, password hashes, JWTs, or private authentication information.

## Required consumer validation

Before processing a message, Transaction Service should verify:

1. `eventId` is present and is a valid UUID.
2. `eventType` equals `UserRegistered`.
3. `eventVersion` equals `1`.
4. `aggregateId` is present and positive.
5. `payload.userId` equals `aggregateId`.
6. The Kafka record key equals `aggregateId.toString()`.

An invalid event should not create a wallet. Log the `eventId` and a safe error
code, but do not log the complete event body unnecessarily.

## No registration callback

The committed `UserRegistered` event is the input contract for wallet
provisioning. Transaction Service does not call User Service over HTTP to confirm
the event or retrieve the user before creating the wallet. Existing synchronous
status checks remain applicable to later sensitive money operations, not to this
registration consumer.

## Wallet creation must be idempotent

The User Service publisher provides at-least-once delivery. This failure sequence
is possible:

```text
Kafka accepts UserRegistered
-> User Service crashes before committing OUTBOX_EVENT.published_at
-> User Service publishes the same event again after restart
```

Transaction Service must therefore handle duplicate delivery safely.

The implemented side effect is naturally keyed by user ID, so Transaction Service
enforces one wallet per `userId` with a database unique constraint.

The consumer transaction should perform the following atomically in the
Transaction Service database:

```text
Find wallet by userId
  -> if present, return successfully
  -> otherwise create it
  -> commit
```

The existing `WalletCommandService.createWallet(userId)` can be reused, provided
the database also enforces uniqueness for `WALLET.USER_ID`. An application-side
`findByUserId` check alone does not prevent two concurrent consumers from creating
duplicates.

Kafka offset acknowledgement must occur only after this database transaction
commits. If the database transaction fails, the listener should throw and Kafka
should redeliver the record.

## Suggested Transaction Service dependency

Transaction Service uses Spring Boot `4.1.1`, so use Spring Boot's Kafka starter
without a manual version. The starter provides Spring Kafka and the Boot
auto-configuration that creates the required Kafka infrastructure beans:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-kafka</artifactId>
</dependency>
```

Do not introduce a second unrelated Kafka client library.

## Suggested consumer configuration

Environment variables:

```text
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_USER_LIFECYCLE_TOPIC=user.lifecycle.v1
KAFKA_USER_LIFECYCLE_DLT_TOPIC=user.lifecycle.v1.DLT
KAFKA_USER_LIFECYCLE_GROUP=transaction-wallet-provisioner
```

Example properties:

```properties
spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
spring.kafka.consumer.group-id=${KAFKA_USER_LIFECYCLE_GROUP:transaction-wallet-provisioner}
spring.kafka.consumer.auto-offset-reset=${KAFKA_AUTO_OFFSET_RESET:earliest}
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.enable-auto-commit=false
spring.kafka.listener.ack-mode=record

messaging.kafka.user-lifecycle-topic=${KAFKA_USER_LIFECYCLE_TOPIC:user.lifecycle.v1}
messaging.kafka.user-lifecycle-dlt-topic=${KAFKA_USER_LIFECYCLE_DLT_TOPIC:user.lifecycle.v1.DLT}
```

Use a stable group ID. Changing the group ID makes Kafka treat the consumer as a
new consumer group with independent offsets.

## Suggested DTOs

Because Transaction Service uses Java 17, records can express the contract
compactly:

```java
public record UserRegisteredEnvelope(
        String eventId,
        String eventType,
        int eventVersion,
        Long aggregateId,
        Instant occurredAt,
        UserRegisteredPayload payload) {
}

public record UserRegisteredPayload(Long userId) {
}
```

Spring Boot 4 uses Jackson 3, whose `ObjectMapper` package is:

```java
import tools.jackson.databind.ObjectMapper;
```

## Suggested listener outline

This is an implementation outline, not code already added to Transaction Service:

```java
@Component
public class UserLifecycleListener {

    private final ObjectMapper objectMapper;
    private final UserRegistrationConsumerService consumerService;

    public UserLifecycleListener(
            ObjectMapper objectMapper,
            UserRegistrationConsumerService consumerService) {
        this.objectMapper = objectMapper;
        this.consumerService = consumerService;
    }

    @KafkaListener(topics = "${messaging.kafka.user-lifecycle-topic}")
    public void consume(ConsumerRecord<String, String> record)
            throws JacksonException {
        UserRegisteredEnvelope event = objectMapper.readValue(
                record.value(), UserRegisteredEnvelope.class);

        validate(record.key(), event);
        consumerService.process(event);
    }
}
```

`UserRegistrationConsumerService.process(...)` should be `@Transactional` and
own the event-deduplication and wallet-creation transaction. Keep database work
out of the listener class itself.

## Error and retry rules

| Situation | Transaction Service behavior |
| --- | --- |
| Duplicate `eventId` | Treat as success; do not create another wallet |
| Wallet already exists for `userId` | Treat as success |
| User status is `ACTIVE` | Create/find wallet and record event as processed |
| User status is inactive | Do not create a wallet; apply the team's documented terminal-event policy |
| User Service temporarily unavailable | Throw so Kafka retries |
| Transaction database unavailable | Roll back and throw so Kafka retries |
| Malformed or unsupported event | Do not create a wallet; route to an error/DLT policy rather than retrying forever |

Do not catch every exception and return normally. Returning normally tells the
Kafka container that processing succeeded and may commit the offset.

## Verification checklist

1. Start Kafka and ensure `user.lifecycle.v1` exists.
2. Start Eureka, User Service, and Transaction Service.
3. Register a new user.
4. Confirm User Service created one `OUTBOX_EVENT` row with
   `published_at = null` initially.
5. Confirm the publisher sends a record keyed by that user ID.
6. Confirm `published_at` becomes non-null only after Kafka acknowledgement.
7. Confirm Transaction Service creates exactly one wallet for the user.
8. Republish the same event and confirm no duplicate wallet is created.
9. Stop Kafka, register another user, and confirm registration still succeeds.
10. Confirm the pending outbox row records failed attempts while remaining
    unpublished.
11. Restart Kafka and confirm the pending event is eventually published and
    processed.

## Current scope

User Service currently publishes only `UserRegistered`. It does not yet publish
`UserUpdated` or `UserDeactivated`. Transaction Service must continue using the
internal status API before sensitive financial operations.
