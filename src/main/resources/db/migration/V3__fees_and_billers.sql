CREATE TABLE billers (
 id CHAR(36) PRIMARY KEY,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 code VARCHAR(50) NOT NULL UNIQUE,
 name VARCHAR(150) NOT NULL,
 category ENUM('ELECTRICITY','AIRTIME','TV','INTERNET','INSURANCE','SCHOOL','MUNICIPALITY','REMITTANCE','OTHER') NOT NULL,
 settlement_wallet_id CHAR(36) NOT NULL,
 status ENUM('ACTIVE','INACTIVE') NOT NULL,
 supports_validation BOOLEAN NOT NULL,
 supports_reversal BOOLEAN NOT NULL,
 supports_enquiry BOOLEAN NOT NULL
) ENGINE=InnoDB;

CREATE TABLE biller_products (
 id CHAR(36) PRIMARY KEY,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 biller_id CHAR(36) NOT NULL,
 code VARCHAR(60) NOT NULL UNIQUE,
 name VARCHAR(150) NOT NULL,
 currency_id CHAR(36) NOT NULL,
 settlement_wallet_id CHAR(36) NOT NULL,
 agent_reward_mode ENUM('NONE','DISCOUNT','CASHBACK') NOT NULL,
 agent_reward_type ENUM('FIXED','PERCENTAGE') NOT NULL,
 agent_reward_value DECIMAL(19,4) NOT NULL,
 minimum_commission DECIMAL(19,4),
 maximum_commission DECIMAL(19,4),
 status ENUM('ACTIVE','INACTIVE') NOT NULL
) ENGINE=InnoDB;

CREATE TABLE fees (
 id CHAR(36) PRIMARY KEY,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 transaction_type_id CHAR(36) NOT NULL,
 biller_product_id CHAR(36),
 customer_type ENUM('INDIVIDUAL','CORPORATE'),
 agent_only BOOLEAN,
 currency_id CHAR(36) NOT NULL,
 calculation_type ENUM('FIXED','PERCENTAGE','FIXED_PLUS_PERCENTAGE') NOT NULL,
 fixed_amount DECIMAL(19,4),
 percentage DECIMAL(10,4),
 minimum_fee DECIMAL(19,4),
 maximum_fee DECIMAL(19,4),
 min_transaction_amount DECIMAL(19,4),
 max_transaction_amount DECIMAL(19,4),
 effective_from DATETIME(6) NOT NULL,
 effective_to DATETIME(6),
 priority INT NOT NULL,
 status ENUM('ACTIVE','INACTIVE') NOT NULL
) ENGINE=InnoDB;

CREATE TABLE bill_payments (
 id CHAR(36) PRIMARY KEY,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 transaction_reference VARCHAR(50) NOT NULL UNIQUE,
 biller_id CHAR(36) NOT NULL,
 biller_product_id CHAR(36) NOT NULL,
 customer_reference VARCHAR(100) NOT NULL,
 provider_reference VARCHAR(200),
 provider_status VARCHAR(30),
 request_data JSON,
 response_data JSON
) ENGINE=InnoDB;
CREATE INDEX idx_fee_lookup ON fees(transaction_type_id,currency_id,status,priority);
CREATE INDEX idx_ledger_biller ON transactions_ledger(biller_id,created_at);
CREATE INDEX idx_ledger_product ON transactions_ledger(biller_product_id,created_at);
