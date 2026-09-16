ALTER TABLE remittances
  ADD COLUMN destination_fee_amount DECIMAL(19,4) NOT NULL DEFAULT 0;

UPDATE remittances
SET destination_fee_amount = ROUND(fee_amount * exchange_rate, 4);

ALTER TABLE transaction_requests
  ADD COLUMN remittance_reference VARCHAR(50),
  ADD COLUMN source_currency VARCHAR(3),
  ADD COLUMN destination_currency VARCHAR(3),
  ADD COLUMN source_amount DECIMAL(19,4),
  ADD COLUMN source_fee_amount DECIMAL(19,4),
  ADD COLUMN destination_fee_amount DECIMAL(19,4),
  ADD COLUMN exchange_rate DECIMAL(19,6),
  ADD COLUMN recipient_amount DECIMAL(19,4),
  ADD COLUMN total_source_amount DECIMAL(19,4);

UPDATE transaction_requests t
JOIN remittances r
  ON t.transaction_reference = r.transaction_reference
  OR t.transaction_reference = r.payout_transaction_reference
JOIN currencies sc ON sc.id = r.source_currency_id
JOIN currencies dc ON dc.id = r.destination_currency_id
SET t.remittance_reference = r.remittance_reference,
    t.source_currency = sc.code,
    t.destination_currency = dc.code,
    t.source_amount = r.send_amount,
    t.source_fee_amount = r.fee_amount,
    t.destination_fee_amount = r.destination_fee_amount,
    t.exchange_rate = r.exchange_rate,
    t.recipient_amount = r.payout_amount,
    t.total_source_amount = r.send_amount + r.fee_amount;

CREATE INDEX idx_transaction_fx_reporting
  ON transaction_requests(operation, source_currency, destination_currency, created_at);
