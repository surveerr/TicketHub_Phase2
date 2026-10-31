-- Phase 2: Transactional Outbox, Idempotent Inbox, and Webhook Deduplication tables

CREATE TABLE payment_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL,
    topic VARCHAR(200) NOT NULL,
    message_key VARCHAR(100) NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP NULL,
    INDEX idx_payment_outbox_status (status, created_at)
);

CREATE TABLE payment_inbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    booking_id BIGINT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_payment_inbox_event UNIQUE (event_id)
);

CREATE TABLE payment_webhook_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    webhook_event_id VARCHAR(120) NOT NULL,
    provider_order_id VARCHAR(100) NULL,
    event_type VARCHAR(60) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_payment_webhook_id UNIQUE (webhook_event_id)
);
