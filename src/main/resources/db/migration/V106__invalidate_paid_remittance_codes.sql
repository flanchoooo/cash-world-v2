UPDATE remittances
SET collection_code = NULL
WHERE status = 'PAID'
  AND collection_code IS NOT NULL;
