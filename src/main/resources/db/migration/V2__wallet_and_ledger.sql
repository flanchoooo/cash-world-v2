CREATE TABLE wallets (
 id CHAR(36) PRIMARY KEY,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 wallet_number VARCHAR(80) NOT NULL UNIQUE,
 customer_id CHAR(36),
 currency_id CHAR(36) NOT NULL,
 wallet_type ENUM('CUSTOMER','BILLER','SYSTEM') NOT NULL,
 name VARCHAR(200) NOT NULL,
 balance DECIMAL(19,4) NOT NULL DEFAULT 0,
 allow_negative_balance BOOLEAN NOT NULL DEFAULT FALSE,
 status ENUM('ACTIVE','BLOCKED','CLOSED') NOT NULL,
 version BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB;

CREATE TABLE transaction_types (
 id CHAR(36) PRIMARY KEY,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 code VARCHAR(40) NOT NULL UNIQUE,
 name VARCHAR(100) NOT NULL,
 category VARCHAR(80),
 is_reversible BOOLEAN NOT NULL,
 allows_fee BOOLEAN NOT NULL,
 allows_commission BOOLEAN NOT NULL,
 status ENUM('ACTIVE','INACTIVE') NOT NULL
) ENGINE=InnoDB;

CREATE TABLE transaction_requests (
 id CHAR(36) PRIMARY KEY,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 transaction_reference VARCHAR(50) NOT NULL UNIQUE,
 user_id CHAR(36) NOT NULL,
 idempotency_key VARCHAR(128) NOT NULL,
 request_hash VARCHAR(64) NOT NULL,
 operation VARCHAR(80) NOT NULL,
 status ENUM('INITIATED','PENDING','SUCCESS','FAILED','REVERSED') NOT NULL,
 original_transaction_reference VARCHAR(50) UNIQUE,
 result_data JSON
) ENGINE=InnoDB;

CREATE TABLE transactions_ledger (
 id CHAR(36) PRIMARY KEY,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 transaction_reference VARCHAR(50) NOT NULL,
 transaction_type_id CHAR(36) NOT NULL,
 entry_type ENUM('PRINCIPAL','FEE','COMMISSION','REVERSAL','ADJUSTMENT') NOT NULL,
 debit_wallet_id CHAR(36) NOT NULL,
 credit_wallet_id CHAR(36) NOT NULL,
 amount DECIMAL(19,4) NOT NULL,
 currency_id CHAR(36) NOT NULL,
 customer_id CHAR(36),
 agent_customer_id CHAR(36),
 biller_id CHAR(36),
 biller_product_id CHAR(36),
 face_value DECIMAL(19,4),
 fee_amount DECIMAL(19,4),
 commission_amount DECIMAL(19,4),
 reward_mode ENUM('NONE','DISCOUNT','CASHBACK'),
 external_reference VARCHAR(200),
 provider_reference VARCHAR(200),
 original_transaction_reference VARCHAR(50),
 status ENUM('INITIATED','PENDING','SUCCESS','FAILED','REVERSED') NOT NULL,
 narration VARCHAR(500),
 idempotency_key VARCHAR(128),
 completed_at DATETIME(6) NOT NULL,
 sequence_number BIGINT NOT NULL AUTO_INCREMENT UNIQUE
) ENGINE=InnoDB;
ALTER TABLE wallets ADD CONSTRAINT uq_customer_currency UNIQUE(customer_id,currency_id);
ALTER TABLE wallets ADD CONSTRAINT ck_wallet_balance CHECK (allow_negative_balance OR balance >= 0);
ALTER TABLE wallets ADD CONSTRAINT ck_wallet_owner CHECK ((wallet_type='CUSTOMER' AND customer_id IS NOT NULL AND allow_negative_balance=FALSE) OR (wallet_type IN ('SYSTEM','BILLER') AND customer_id IS NULL));
ALTER TABLE transactions_ledger ADD CONSTRAINT ck_positive_amount CHECK (amount>0), ADD CONSTRAINT ck_distinct_wallets CHECK (debit_wallet_id<>credit_wallet_id);
ALTER TABLE transaction_requests ADD CONSTRAINT uq_request_key UNIQUE(user_id,idempotency_key);
CREATE INDEX idx_ledger_reference ON transactions_ledger(transaction_reference);
CREATE INDEX idx_ledger_debit ON transactions_ledger(debit_wallet_id,sequence_number);
CREATE INDEX idx_ledger_credit ON transactions_ledger(credit_wallet_id,sequence_number);
CREATE INDEX idx_ledger_customer ON transactions_ledger(customer_id,created_at);
CREATE INDEX idx_ledger_agent ON transactions_ledger(agent_customer_id,entry_type);
CREATE INDEX idx_ledger_type ON transactions_ledger(transaction_type_id,created_at);
CREATE INDEX idx_ledger_idempotency ON transactions_ledger(idempotency_key);
CREATE INDEX idx_ledger_created ON transactions_ledger(created_at);
INSERT INTO transaction_types(id,created_at,updated_at,code,name,is_reversible,allows_fee,allows_commission,status) VALUES ('10000000-0000-0000-0000-000000000001',NOW(6),NOW(6),'DEPOSIT','DEPOSIT',TRUE,TRUE,FALSE,'ACTIVE');
INSERT INTO transaction_types(id,created_at,updated_at,code,name,is_reversible,allows_fee,allows_commission,status) VALUES ('10000000-0000-0000-0000-000000000002',NOW(6),NOW(6),'WITHDRAWAL','WITHDRAWAL',TRUE,TRUE,FALSE,'ACTIVE');
INSERT INTO transaction_types(id,created_at,updated_at,code,name,is_reversible,allows_fee,allows_commission,status) VALUES ('10000000-0000-0000-0000-000000000003',NOW(6),NOW(6),'SEND_MONEY','SEND_MONEY',TRUE,TRUE,FALSE,'ACTIVE');
INSERT INTO transaction_types(id,created_at,updated_at,code,name,is_reversible,allows_fee,allows_commission,status) VALUES ('10000000-0000-0000-0000-000000000004',NOW(6),NOW(6),'BILL_PAYMENT','BILL_PAYMENT',TRUE,TRUE,TRUE,'ACTIVE');
INSERT INTO transaction_types(id,created_at,updated_at,code,name,is_reversible,allows_fee,allows_commission,status) VALUES ('10000000-0000-0000-0000-000000000005',NOW(6),NOW(6),'ACCOUNT_ADJUSTMENT','ACCOUNT_ADJUSTMENT',TRUE,FALSE,FALSE,'ACTIVE');
INSERT INTO transaction_types(id,created_at,updated_at,code,name,is_reversible,allows_fee,allows_commission,status) VALUES ('10000000-0000-0000-0000-000000000006',NOW(6),NOW(6),'REMITTANCE_SEND','REMITTANCE_SEND',TRUE,TRUE,FALSE,'ACTIVE');
INSERT INTO transaction_types(id,created_at,updated_at,code,name,is_reversible,allows_fee,allows_commission,status) VALUES ('10000000-0000-0000-0000-000000000007',NOW(6),NOW(6),'REMITTANCE_PAYOUT','REMITTANCE_PAYOUT',TRUE,TRUE,FALSE,'ACTIVE');
INSERT INTO transaction_types(id,created_at,updated_at,code,name,is_reversible,allows_fee,allows_commission,status) VALUES ('10000000-0000-0000-0000-000000000008',NOW(6),NOW(6),'REVERSAL','REVERSAL',FALSE,FALSE,FALSE,'ACTIVE');
