CREATE TABLE customers (
 id CHAR(36) PRIMARY KEY,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 customer_number VARCHAR(32) NOT NULL UNIQUE,
 customer_type ENUM('INDIVIDUAL','CORPORATE') NOT NULL,
 first_name VARCHAR(100),
 last_name VARCHAR(100),
 company_name VARCHAR(200),
 registration_number VARCHAR(100),
 national_id VARCHAR(100),
 mobile_number VARCHAR(40) NOT NULL,
 email VARCHAR(254),
 address VARCHAR(500),
 is_agent BOOLEAN NOT NULL DEFAULT FALSE,
 agent_type ENUM('STANDARD','SUPER_AGENT','DISTRIBUTOR'),
 kyc_status ENUM('PENDING','VERIFIED','REJECTED') NOT NULL,
 status ENUM('ACTIVE','BLOCKED','SUSPENDED','CLOSED') NOT NULL
) ENGINE=InnoDB;

CREATE TABLE users (
 id CHAR(36) PRIMARY KEY,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 customer_id CHAR(36),
 username VARCHAR(100) NOT NULL UNIQUE,
 mobile_number VARCHAR(40),
 email VARCHAR(254),
 password_hash VARCHAR(255) NOT NULL,
 role ENUM('SUPER_ADMIN','OPERATIONS','CUSTOMER','CORPORATE_ADMIN','CORPORATE_USER','AGENT') NOT NULL,
 status ENUM('ACTIVE','BLOCKED','DISABLED') NOT NULL,
 last_login_at DATETIME(6),
 token_version BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB;

CREATE TABLE refresh_tokens (
 id CHAR(36) PRIMARY KEY,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 user_id CHAR(36) NOT NULL,
 token_hash VARCHAR(64) NOT NULL UNIQUE,
 expires_at DATETIME(6) NOT NULL,
 revoked BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE=InnoDB;

CREATE TABLE currencies (
 id CHAR(36) PRIMARY KEY,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 code VARCHAR(3) NOT NULL UNIQUE,
 name VARCHAR(100) NOT NULL,
 symbol VARCHAR(10),
 decimal_places INT NOT NULL,
 status ENUM('ACTIVE','INACTIVE') NOT NULL
) ENGINE=InnoDB;

CREATE TABLE audit_logs (
 id CHAR(36) PRIMARY KEY,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 user_id CHAR(36),
 customer_id CHAR(36),
 action VARCHAR(100) NOT NULL,
 entity_type VARCHAR(100) NOT NULL,
 entity_id VARCHAR(100) NOT NULL,
 before_data JSON,
 after_data JSON,
 ip_address VARCHAR(64)
) ENGINE=InnoDB;
CREATE TABLE number_sequences (name VARCHAR(30) PRIMARY KEY, next_value BIGINT NOT NULL);
INSERT INTO number_sequences VALUES ('customer',0);
INSERT INTO currencies (id,created_at,updated_at,code,name,decimal_places,status) VALUES ('00000000-0000-0000-0000-000000000001',NOW(6),NOW(6),'USD','US Dollar',2,'ACTIVE');
INSERT INTO currencies (id,created_at,updated_at,code,name,decimal_places,status) VALUES ('00000000-0000-0000-0000-000000000002',NOW(6),NOW(6),'ZWG','Zimbabwe Gold',2,'ACTIVE');
INSERT INTO currencies (id,created_at,updated_at,code,name,decimal_places,status) VALUES ('00000000-0000-0000-0000-000000000003',NOW(6),NOW(6),'ZAR','South African Rand',2,'ACTIVE');
INSERT INTO currencies (id,created_at,updated_at,code,name,decimal_places,status) VALUES ('00000000-0000-0000-0000-000000000004',NOW(6),NOW(6),'BWP','Botswana Pula',2,'ACTIVE');
