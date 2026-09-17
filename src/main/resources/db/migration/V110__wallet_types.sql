CREATE TABLE wallet_types (
 id CHAR(36) PRIMARY KEY,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 code VARCHAR(40) NOT NULL UNIQUE,
 name VARCHAR(100) NOT NULL,
 description VARCHAR(500),
 scope ENUM('CUSTOMER','BILLER','SYSTEM') NOT NULL,
 status ENUM('ACTIVE','INACTIVE') NOT NULL
) ENGINE=InnoDB;

INSERT INTO wallet_types(id,created_at,updated_at,code,name,scope,status) VALUES
 ('11000000-0000-0000-0000-000000000001',NOW(6),NOW(6),'CUSTOMER','Wallet','CUSTOMER','ACTIVE'),
 ('11000000-0000-0000-0000-000000000002',NOW(6),NOW(6),'BILLER','Biller wallet','BILLER','ACTIVE'),
 ('11000000-0000-0000-0000-000000000003',NOW(6),NOW(6),'SYSTEM','System wallet','SYSTEM','ACTIVE');

ALTER TABLE wallets ADD COLUMN wallet_type_id CHAR(36);
UPDATE wallets SET wallet_type_id=CASE wallet_type
 WHEN 'CUSTOMER' THEN '11000000-0000-0000-0000-000000000001'
 WHEN 'BILLER' THEN '11000000-0000-0000-0000-000000000002'
 WHEN 'SYSTEM' THEN '11000000-0000-0000-0000-000000000003' END;
ALTER TABLE wallets MODIFY wallet_type_id CHAR(36) NOT NULL;
ALTER TABLE wallets ADD CONSTRAINT fk_wallet_type FOREIGN KEY(wallet_type_id) REFERENCES wallet_types(id);
-- The old unique index also supplied the leading customer_id index required by fk_wallets_customer_id.
CREATE INDEX idx_wallets_customer_id ON wallets(customer_id);
ALTER TABLE wallets DROP INDEX uq_customer_currency;
ALTER TABLE wallets ADD CONSTRAINT uq_customer_wallet_type_currency UNIQUE(customer_id,wallet_type_id,currency_id);
