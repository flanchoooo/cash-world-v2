CREATE TABLE remittances (
 id CHAR(36) PRIMARY KEY,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 remittance_reference VARCHAR(50) NOT NULL UNIQUE,
 transaction_reference VARCHAR(50) NOT NULL UNIQUE,
 payout_transaction_reference VARCHAR(50) UNIQUE,
 sender_customer_id CHAR(36),
 receiver_customer_id CHAR(36),
 provider_biller_id CHAR(36),
 source_currency_id CHAR(36) NOT NULL,
 destination_currency_id CHAR(36) NOT NULL,
 send_amount DECIMAL(19,4) NOT NULL,
 exchange_rate DECIMAL(19,6) NOT NULL,
 payout_amount DECIMAL(19,4) NOT NULL,
 sender_name VARCHAR(200) NOT NULL,
 sender_mobile VARCHAR(40) NOT NULL,
 receiver_name VARCHAR(200) NOT NULL,
 receiver_mobile VARCHAR(40) NOT NULL,
 receiver_id_number VARCHAR(100),
 destination_country VARCHAR(2) NOT NULL,
 provider_reference VARCHAR(200),
 status ENUM('INITIATED','PENDING','AVAILABLE_FOR_PAYOUT','PAID','FAILED','REVERSED') NOT NULL
) ENGINE=InnoDB;
CREATE INDEX idx_remit_sender ON remittances(sender_customer_id,created_at);
CREATE INDEX idx_remit_receiver ON remittances(receiver_customer_id,created_at);
ALTER TABLE remittances ADD CONSTRAINT ck_remit_amounts CHECK(send_amount>0 AND payout_amount>0 AND exchange_rate>0);
