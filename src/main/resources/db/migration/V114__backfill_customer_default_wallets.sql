INSERT INTO wallets(
  id,created_at,updated_at,wallet_number,customer_id,currency_id,wallet_type_id,
  wallet_type,name,balance,allow_negative_balance,status,version)
SELECT
  UUID(),NOW(6),NOW(6),
  CONCAT('WAL',REPLACE(UUID(),'-','')),
  c.id,cu.id,wt.id,
  'CUSTOMER',CONCAT(wt.name,' ',cu.code,' Wallet'),0,FALSE,'ACTIVE',0
FROM customers c
CROSS JOIN currencies cu
CROSS JOIN wallet_types wt
WHERE cu.code IN ('USD','ZWG')
  AND cu.status = 'ACTIVE'
  AND wt.code IN ('AIRTIME','WALLET','BILL_PAYMENT')
  AND wt.scope = 'CUSTOMER'
  AND wt.status = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1 FROM wallets existing
    WHERE existing.customer_id = c.id
      AND existing.currency_id = cu.id
      AND existing.wallet_type_id = wt.id
  );
