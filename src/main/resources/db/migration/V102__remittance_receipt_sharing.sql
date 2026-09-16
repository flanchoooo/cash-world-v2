ALTER TABLE remittances
  ADD COLUMN receipt_share_token VARCHAR(64);

CREATE UNIQUE INDEX uk_remittances_receipt_share_token
  ON remittances(receipt_share_token);
