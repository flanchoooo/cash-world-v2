-- Remittances can be collected in one currency and paid out in another.
-- The destination settlement position is therefore allowed to go negative until treasury
-- reconciles the cross-currency settlement. Customer wallets remain strictly non-negative.
UPDATE wallets
SET allow_negative_balance = TRUE
WHERE wallet_type = 'SYSTEM'
  AND wallet_number LIKE 'REMITTANCE_SETTLEMENT\_%';
