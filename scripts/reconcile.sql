-- Read-only reconciliation. Every row returned represents a discrepancy.
SELECT w.wallet_number, w.balance, COALESCE(m.ledger_balance,0) AS ledger_balance
FROM wallets w
LEFT JOIN (
 SELECT wallet_id, SUM(delta) AS ledger_balance FROM (
  SELECT credit_wallet_id AS wallet_id, amount AS delta FROM transactions_ledger
  UNION ALL
  SELECT debit_wallet_id AS wallet_id, -amount AS delta FROM transactions_ledger
 ) movements GROUP BY wallet_id
) m ON m.wallet_id=w.id
WHERE w.balance<>COALESCE(m.ledger_balance,0);

-- With all balances starting at zero, this must return zero per currency.
SELECT c.code, SUM(w.balance) AS net_balance
FROM wallets w JOIN currencies c ON c.id=w.currency_id
GROUP BY c.code HAVING SUM(w.balance)<>0;
