ALTER TABLE biller_products ADD COLUMN wallet_type_id CHAR(36);
UPDATE biller_products SET wallet_type_id='11000000-0000-0000-0000-000000000001' WHERE wallet_type_id IS NULL;
ALTER TABLE biller_products MODIFY wallet_type_id CHAR(36) NOT NULL;
ALTER TABLE biller_products ADD CONSTRAINT fk_biller_product_wallet_type FOREIGN KEY(wallet_type_id) REFERENCES wallet_types(id);
