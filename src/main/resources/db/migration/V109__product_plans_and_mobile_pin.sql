CREATE TABLE product_commission_plans (
 id CHAR(36) PRIMARY KEY,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 biller_product_id CHAR(36) NOT NULL,
 arrangement_name VARCHAR(100) NOT NULL,
 total_commission_percentage DECIMAL(9,4) NOT NULL,
 agent_commission_percentage DECIMAL(9,4) NOT NULL,
 platform_commission_percentage DECIMAL(9,4) NOT NULL,
 status ENUM('ACTIVE','INACTIVE') NOT NULL,
 CONSTRAINT fk_product_plan_product FOREIGN KEY(biller_product_id) REFERENCES biller_products(id),
 CONSTRAINT uk_product_plan_definition UNIQUE(
   biller_product_id,arrangement_name,total_commission_percentage,
   agent_commission_percentage,platform_commission_percentage),
 CONSTRAINT ck_product_plan_commissions CHECK(
   total_commission_percentage>=0 AND total_commission_percentage<=100
   AND agent_commission_percentage>=0 AND agent_commission_percentage<=100
   AND platform_commission_percentage>=0 AND platform_commission_percentage<=100
   AND total_commission_percentage=agent_commission_percentage+platform_commission_percentage)
) ENGINE=InnoDB;

INSERT INTO product_commission_plans(
 id,created_at,updated_at,biller_product_id,arrangement_name,
 total_commission_percentage,agent_commission_percentage,
 platform_commission_percentage,status)
SELECT UUID(),MIN(created_at),MAX(updated_at),biller_product_id,arrangement_name,
 total_commission_percentage,agent_commission_percentage,
 platform_commission_percentage,'ACTIVE'
FROM customer_product_allocations
GROUP BY biller_product_id,arrangement_name,total_commission_percentage,
 agent_commission_percentage,platform_commission_percentage;

ALTER TABLE customer_product_allocations ADD COLUMN commission_plan_id CHAR(36);

UPDATE customer_product_allocations allocation
JOIN product_commission_plans plan
  ON plan.biller_product_id=allocation.biller_product_id
 AND plan.arrangement_name=allocation.arrangement_name
 AND plan.total_commission_percentage=allocation.total_commission_percentage
 AND plan.agent_commission_percentage=allocation.agent_commission_percentage
 AND plan.platform_commission_percentage=allocation.platform_commission_percentage
SET allocation.commission_plan_id=plan.id;

ALTER TABLE customer_product_allocations
 MODIFY commission_plan_id CHAR(36) NOT NULL,
 ADD CONSTRAINT fk_allocation_commission_plan
   FOREIGN KEY(commission_plan_id) REFERENCES product_commission_plans(id);

CREATE INDEX idx_product_plans_product_status
  ON product_commission_plans(biller_product_id,status);

ALTER TABLE users ADD COLUMN mobile_pin_hash VARCHAR(255);
