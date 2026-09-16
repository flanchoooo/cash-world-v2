# Sample ledger records

Each table row represents one `transactions_ledger` row, with debit and credit in the same row. References below are readable examples; the application generates `TX` plus a UUID. Amounts are in USD except the final payout example. The initial USD 100 is itself funded by a ledger entry.

## Agent discount and reversal

| Reference | Type | Entry | Debit | Credit | Amount | Original reference |
| --- | --- | --- | --- | --- | ---: | --- |
| TX-DEPOSIT | DEPOSIT | PRINCIPAL | CASH_SETTLEMENT_USD | Agent USD | 100.00 | — |
| TX-DISCOUNT | BILL_PAYMENT | PRINCIPAL | Agent USD | ZETDC_SETTLEMENT_USD | 19.60 | — |
| TX-DISCOUNT | BILL_PAYMENT | COMMISSION | COMMISSION_EXPENSE_USD | ZETDC_SETTLEMENT_USD | 0.40 | — |
| TX-REVERSE | REVERSAL | REVERSAL | ZETDC_SETTLEMENT_USD | Agent USD | 19.60 | TX-DISCOUNT |
| TX-REVERSE | REVERSAL | REVERSAL | ZETDC_SETTLEMENT_USD | COMMISSION_EXPENSE_USD | 0.40 | TX-DISCOUNT |

Both original purchase rows store `face_value=20.00`, `commission_amount=0.40`, `fee_amount=0.00`, the same customer/agent/product IDs, and `reward_mode=DISCOUNT`. Do not sum repeated face-value metadata across a transaction's rows. After purchase the agent has 80.40; after reversal, 100.00. Original ledger status remains SUCCESS; the grouped transaction status becomes REVERSED.

## Normal bill payment with a fee

| Reference | Entry | Debit | Credit | Amount |
| --- | --- | --- | --- | ---: |
| TX-BILL | PRINCIPAL | Customer USD | ZETDC_SETTLEMENT_USD | 20.00 |
| TX-BILL | FEE | Customer USD | FEE_REVENUE_USD | 0.50 |

Customer deduction: 20.50. Reversal swaps both rows exactly, refunding principal and fee.

## Cashback

| Reference | Entry | Debit | Credit | Amount |
| --- | --- | --- | --- | ---: |
| TX-CASHBACK | PRINCIPAL | Agent USD | ZETDC_SETTLEMENT_USD | 20.00 |
| TX-CASHBACK | COMMISSION | COMMISSION_EXPENSE_USD | Agent USD | 0.40 |

Net deduction: 19.60. The full principal plus any fee must be available before cashback. Reversal refunds 20.00 and returns 0.40 from agent to commission expense within one atomic batch.

## Remittance

| Reference | Type | Entry | Debit | Credit | Amount | Currency |
| --- | --- | --- | --- | --- | ---: | --- |
| TX-SEND | REMITTANCE_SEND | PRINCIPAL | Sender USD | REMITTANCE_SETTLEMENT_USD | 100.00 | USD |
| TX-SEND | REMITTANCE_SEND | FEE | Sender USD | FEE_REVENUE_USD | 3.00 | USD |
| TX-PAYOUT | REMITTANCE_PAYOUT | PRINCIPAL | REMITTANCE_SETTLEMENT_BWP | Receiver BWP | 1350.00 | BWP |

The remittance metadata stores exchange rate 13.500000 and payout amount 1350.00. The BWP settlement wallet must already have funding represented by earlier ledger entries. The USD source row never directly credits a BWP wallet.
