CREATE TABLE payments (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_reference       VARCHAR(64)    NOT NULL,
    booking_id              BIGINT         NOT NULL,
    user_id                 BIGINT         NOT NULL,
    amount                  DECIMAL(12, 2) NOT NULL,
    currency                VARCHAR(3)     NOT NULL,
    status                  VARCHAR(20)    NOT NULL,
    provider                VARCHAR(20)    NOT NULL,
    provider_transaction_id VARCHAR(100),
    idempotency_key         VARCHAR(64),
    failure_reason          VARCHAR(500),
    created_at              DATETIME(6)    NOT NULL,
    updated_at              DATETIME(6)    NOT NULL,
    CONSTRAINT uk_payments_reference UNIQUE (payment_reference),
    CONSTRAINT uk_payments_idempotency UNIQUE (idempotency_key),
    INDEX idx_payments_booking (booking_id),
    INDEX idx_payments_status_created (status, created_at)
);

CREATE TABLE payment_attempts (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id        BIGINT       NOT NULL,
    attempt_number    INT          NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    provider_response VARCHAR(500),
    created_at        DATETIME(6)  NOT NULL,
    CONSTRAINT fk_attempt_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    INDEX idx_attempt_payment (payment_id)
);
