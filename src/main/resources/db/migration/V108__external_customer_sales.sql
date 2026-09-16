CREATE TABLE customer_product_allocations (
 id CHAR(36) PRIMARY KEY,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 customer_id CHAR(36) NOT NULL,
 biller_product_id CHAR(36) NOT NULL,
 arrangement_name VARCHAR(100) NOT NULL,
 total_commission_percentage DECIMAL(9,4) NOT NULL,
 agent_commission_percentage DECIMAL(9,4) NOT NULL,
 platform_commission_percentage DECIMAL(9,4) NOT NULL,
 status ENUM('ACTIVE','INACTIVE') NOT NULL,
 CONSTRAINT uk_customer_product_allocation UNIQUE(customer_id,biller_product_id),
 CONSTRAINT fk_allocation_customer FOREIGN KEY(customer_id) REFERENCES customers(id),
 CONSTRAINT fk_allocation_product FOREIGN KEY(biller_product_id) REFERENCES biller_products(id),
 CONSTRAINT ck_allocation_commissions CHECK(
   total_commission_percentage>=0 AND total_commission_percentage<=100
   AND agent_commission_percentage>=0 AND agent_commission_percentage<=100
   AND platform_commission_percentage>=0 AND platform_commission_percentage<=100
   AND total_commission_percentage=agent_commission_percentage+platform_commission_percentage)
) ENGINE=InnoDB;

CREATE TABLE external_sales (
 id CHAR(36) PRIMARY KEY,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 transaction_reference VARCHAR(50) NOT NULL UNIQUE,
 reversal_transaction_reference VARCHAR(50),
 customer_id CHAR(36) NOT NULL,
 wallet_id CHAR(36) NOT NULL,
 biller_product_id CHAR(36) NOT NULL,
 customer_reference VARCHAR(100) NOT NULL,
 face_value DECIMAL(19,4) NOT NULL,
 wallet_debit_amount DECIMAL(19,4) NOT NULL,
 total_commission_amount DECIMAL(19,4) NOT NULL,
 agent_commission_amount DECIMAL(19,4) NOT NULL,
 platform_commission_amount DECIMAL(19,4) NOT NULL,
 credit_sale BOOLEAN NOT NULL DEFAULT FALSE,
 collecting_agent_name VARCHAR(200),
 collecting_agent_mobile VARCHAR(40),
 collecting_agent_id_number VARCHAR(100),
 amount_due DECIMAL(19,4),
 credit_status VARCHAR(30),
 collected_at DATETIME(6),
 collected_by_user_id CHAR(36),
 provider_reference VARCHAR(200),
 provider_status VARCHAR(30) NOT NULL,
 request_metadata JSON,
 provider_metadata JSON,
 CONSTRAINT fk_external_sale_customer FOREIGN KEY(customer_id) REFERENCES customers(id),
 CONSTRAINT fk_external_sale_wallet FOREIGN KEY(wallet_id) REFERENCES wallets(id),
 CONSTRAINT fk_external_sale_product FOREIGN KEY(biller_product_id) REFERENCES biller_products(id),
 CONSTRAINT fk_external_sale_collected_by FOREIGN KEY(collected_by_user_id) REFERENCES users(id),
 CONSTRAINT ck_external_sale_amounts CHECK(face_value>0 AND wallet_debit_amount>=0
   AND total_commission_amount>=0 AND agent_commission_amount>=0
   AND platform_commission_amount>=0 AND (amount_due IS NULL OR amount_due>=0))
) ENGINE=InnoDB;

CREATE INDEX idx_allocations_customer_status
  ON customer_product_allocations(customer_id,status);
CREATE INDEX idx_external_sales_customer_date
  ON external_sales(customer_id,created_at);
CREATE INDEX idx_external_sales_credit
  ON external_sales(credit_sale,credit_status,created_at);

INSERT INTO transaction_types(
 id,created_at,updated_at,code,name,is_reversible,allows_fee,allows_commission,status)
VALUES (
 '10000000-0000-0000-0000-000000000009',NOW(6),NOW(6),
 'EXTERNAL_SALE','EXTERNAL_SALE',TRUE,FALSE,TRUE,'ACTIVE');
