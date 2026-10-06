-- Run once in the Transaction Service's Oracle schema, before deploying the new code.
-- No existing payment/notification/user-outbox tables are altered.
CREATE TABLE payment_outbox (
    transaction_id NUMBER(19) NOT NULL,
    receiver_user_id NUMBER(19) NOT NULL,
    payload CLOB NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6),
    next_attempt_at TIMESTAMP(6) NOT NULL,
    publish_attempts NUMBER(10) DEFAULT 0 NOT NULL,
    last_error VARCHAR2(1000 CHAR),
    CONSTRAINT pk_payment_outbox PRIMARY KEY (transaction_id),
    CONSTRAINT ck_payment_outbox_attempts CHECK (publish_attempts >= 0)
);

CREATE INDEX ix_payment_outbox_pending
    ON payment_outbox (published_at, next_attempt_at, transaction_id);
-- Timestamps in this table are UTC. Only rows published >24 hours ago are cleaned.
