# Run the six-service backend and Kafka

Run commands below in PowerShell. Keep Rancher Desktop running.

## 1. Start the existing Kafka broker

This machine already has a stopped container named `broker` using apache/kafka:4.3.1.
Reuse that container; do not start a second broker on port 9092.

~~~powershell
docker start broker
docker exec broker /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
~~~

If the second command fails while Kafka is starting, wait a few seconds and retry it.
Use `docker logs --tail 50 broker` to diagnose startup errors.
Starting Docker alone does not necessarily start this container.

## 2. Create topics (first-time setup)

Kafka's "channels" are called topics. These commands are safe to repeat.
They use the application's default topic names; use matching names if you override
KAFKA_USER_LIFECYCLE_TOPIC or PAYMENT_COMPLETED_TOPIC.

~~~powershell
$topics = @(
    'user.lifecycle.v1',
    'payments.completed.v1'
)
foreach ($topic in $topics) {
    docker exec broker /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic $topic --partitions 1 --replication-factor 1
    if ($LASTEXITCODE -ne 0) { throw "Failed to create topic: $topic" }
}
~~~

One partition and one replica are for this single-broker local development setup,
not production resilience. No ZooKeeper is needed for this Kafka image.
Official reference: https://kafka.apache.org/quickstart/

Only these two topics are needed. Each consumer tries a record three times total,
then logs and skips it. Skipped registrations can leave missing wallets; skipped
payment events can leave missing notifications. Neither is automatically recovered.
Outbox retries for publishing failures remain unchanged.

## 3. Start the backend

From this checkout, run `start-all.bat` (or double-click it).
Startup order: Discovery 8761, User 8081, Contact 8082, Transaction 8083,
Notification 8084, Gateway 8080. Each service must become ready before the next starts.

The existing .env is read without executing it. Existing required settings remain:
DB_URL, DB_USERNAME, DB_PASSWORD, JWT_PRIVATE_KEY, JWT_PUBLIC_KEY and INTERNAL_SERVICE_TOKEN.
Kafka defaults to localhost:9092; set KAFKA_BOOTSTRAP_SERVERS for another broker.
Do not set EUREKA_ENABLED=false or disable the Kafka listeners/outbox publishers
when testing the complete notification flow.

If the notification table is in the existing database/schema, no new database
variables are needed: the launcher maps DB_* to NOTIFICATION_DB_*.
For a separate schema, supply all three NOTIFICATION_DB_URL,
NOTIFICATION_DB_USERNAME and NOTIFICATION_DB_PASSWORD. Partial overrides fail early.
No real .env file was changed.
The Notification Java service receives its database settings and public JWT key,
not the private JWT key or internal-service tokens. This is process configuration
hygiene, not OS-level isolation between applications running as the same user.

Both the notification table and the payment outbox table must exist in their
respective configured schemas. SQL/schema correctness still needs a live startup check.

`start-all.bat -Check` checks the launcher environment, Java executable,
Maven wrapper and service port availability without starting services.
It does NOT validate Oracle connectivity, Kafka readiness/topics or the Java version.

## 4. Stop

Close the launcher window, press Ctrl+C, or run `stop-all.bat`.
The managed process job stops all six services and their Maven/Java children.
Unknown processes occupying these ports are never killed.
A startup failure also cleans up the services already started.

Kafka is separate and intentionally survives backend restarts. To stop it too,
stop the backend first, then run:

~~~powershell
docker stop broker
~~~

Next time: `docker start broker`, wait for Kafka readiness, then `start-all.bat`.
Existing topics survive stop/start of the same container. Do not remove the
container if you need its local data; production needs explicit persistent storage.

Frontend startup is not managed by these backend scripts.
