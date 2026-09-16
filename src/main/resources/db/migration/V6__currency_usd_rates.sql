ALTER TABLE currencies
  ADD COLUMN rate_against_usd DECIMAL(20,10) NOT NULL DEFAULT 1.0000000000;

UPDATE currencies
SET rate_against_usd = 1.0000000000
WHERE code = 'USD';
