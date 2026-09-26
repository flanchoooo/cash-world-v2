ALTER TABLE transactions_ledger
    ADD COLUMN platform_commission_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN api_metadata JSON NULL;
