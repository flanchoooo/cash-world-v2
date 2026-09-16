INSERT INTO wallets(id,created_at,updated_at,wallet_number,currency_id,wallet_type,name,balance,allow_negative_balance,status,version)
SELECT UUID(),NOW(6),NOW(6),CONCAT('ZETDC_SETTLEMENT_',code),id,'BILLER',CONCAT('ZETDC ',code),0,FALSE,'ACTIVE',0 FROM currencies WHERE code IN ('USD','ZWG');
INSERT INTO billers(id,created_at,updated_at,code,name,category,settlement_wallet_id,status,supports_validation,supports_reversal,supports_enquiry)
SELECT '20000000-0000-0000-0000-000000000001',NOW(6),NOW(6),'ZETDC','ZETDC','ELECTRICITY',id,'ACTIVE',TRUE,TRUE,TRUE FROM wallets WHERE wallet_number='ZETDC_SETTLEMENT_USD';
INSERT INTO biller_products(id,created_at,updated_at,biller_id,code,name,currency_id,settlement_wallet_id,agent_reward_mode,agent_reward_type,agent_reward_value,status)
SELECT UUID(),NOW(6),NOW(6),'20000000-0000-0000-0000-000000000001',CONCAT('ZETDC_',c.code),CONCAT('ZETDC ',c.code),c.id,w.id,'DISCOUNT','PERCENTAGE',2,'ACTIVE' FROM currencies c JOIN wallets w ON w.wallet_number=CONCAT('ZETDC_SETTLEMENT_',c.code) WHERE c.code IN ('USD','ZWG');
INSERT INTO fees(id,created_at,updated_at,transaction_type_id,currency_id,calculation_type,fixed_amount,effective_from,priority,status)
SELECT UUID(),NOW(6),NOW(6),t.id,c.id,'FIXED',0,'2020-01-01',0,'ACTIVE' FROM transaction_types t CROSS JOIN currencies c WHERE t.allows_fee=TRUE;
