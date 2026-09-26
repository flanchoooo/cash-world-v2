INSERT INTO currencies (
  id, created_at, updated_at, code, name, symbol, decimal_places, rate_against_usd, status
) VALUES
  ('00000000-0000-0000-0000-000000000003', NOW(6), NOW(6), 'ZAR', 'South African Rand', 'R', 2, 1.0000000000, 'ACTIVE'),
  ('00000000-0000-0000-0000-000000000002', NOW(6), NOW(6), 'ZWG', 'Zimbabwe Gold', 'ZiG', 2, 1.0000000000, 'ACTIVE'),
  ('00000000-0000-0000-0000-000000000001', NOW(6), NOW(6), 'USD', 'US Dollar', '$', 2, 1.0000000000, 'ACTIVE'),
  ('00000000-0000-0000-0000-000000000004', NOW(6), NOW(6), 'BWP', 'Botswana Pula', 'P', 2, 1.0000000000, 'ACTIVE')
ON DUPLICATE KEY UPDATE
  symbol = IF(symbol IS NULL OR symbol = '', VALUES(symbol), symbol);
