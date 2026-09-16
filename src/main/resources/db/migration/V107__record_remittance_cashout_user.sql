ALTER TABLE remittances
  ADD COLUMN cashed_out_by_user_id CHAR(36),
  ADD CONSTRAINT fk_remittances_cashed_out_by_user_id
    FOREIGN KEY (cashed_out_by_user_id) REFERENCES users(id);

UPDATE remittances r
JOIN transaction_requests t
  ON t.transaction_reference = r.payout_transaction_reference
SET r.cashed_out_by_user_id = t.user_id
WHERE r.payout_transaction_reference IS NOT NULL
  AND r.cashed_out_by_user_id IS NULL;

CREATE INDEX idx_remittances_cashed_out_by
  ON remittances(cashed_out_by_user_id, updated_at);
