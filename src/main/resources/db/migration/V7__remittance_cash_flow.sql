ALTER TABLE remittances
  ADD COLUMN created_by_user_id CHAR(36),
  ADD COLUMN fee_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
  ADD COLUMN fee_overridden BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN reason_for_sending VARCHAR(300),
  ADD COLUMN source_of_funds VARCHAR(200),
  ADD COLUMN collection_code CHAR(6),
  ADD COLUMN proof_of_payment_name VARCHAR(255),
  ADD COLUMN proof_of_payment_content_type VARCHAR(100),
  ADD COLUMN proof_of_payment_data LONGBLOB;

ALTER TABLE remittances
  ADD CONSTRAINT fk_remittances_created_by_user_id
    FOREIGN KEY (created_by_user_id) REFERENCES users(id),
  ADD CONSTRAINT ck_remittances_fee_amount CHECK (fee_amount >= 0),
  ADD CONSTRAINT ck_remittances_collection_code
    CHECK (collection_code IS NULL OR collection_code REGEXP '^[0-9]{6}$');

CREATE INDEX idx_remittances_created_by ON remittances(created_by_user_id, created_at);
