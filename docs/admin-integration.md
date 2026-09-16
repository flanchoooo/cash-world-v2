# Administration integration

## Implemented

The React administration application covers the approved foundations and operational steps. It calls the existing customer, wallet, ledger, bill payment, remittance, commission and configuration services. New read endpoints supply the administration lists and dashboard. No parallel financial ledger or browser balance calculator has been added.

| UI area | Backend integration |
| --- | --- |
| Browser session | `/api/auth/browser/login`, `/refresh`, `/logout`, plus bearer `/api/auth/me` |
| Dashboard | `GET /api/admin/workspace/dashboard` |
| Operational lists | `GET /api/admin/workspace/{resource}` for customers, wallets, users, transactions, bill-payments, remittances, audit |
| Available currencies/products | `GET /api/admin/workspace/catalog` |
| Customer actions | Existing `/api/customers` registration, update, activate, block, make-agent |
| Wallet actions/statements | Existing `/api/wallets` and `/api/admin/wallets/adjust` |
| Transactions | Existing transaction details and reverse endpoint |
| Bills/remittances | Existing purchase/enquiry and send/payout endpoints |
| Commissions | Existing agent report and currency summary endpoints |
| Configuration | Existing six configuration resource APIs |
| User access | Existing register; new `PUT /api/auth/users/{id}` with `{role,status}`; existing block also uses the guarded access update |

## Read contract and boundaries

Record list parameters: `search`, `status`, `from`, `to`, `page`, `size`; customers also accept `agents=true`. Timestamps are ISO instants; `from` is inclusive and `to` exclusive. Page starts at 0; size is 1–100. Response is `{items,total,page,size}`. SQL projections are explicitly selected from an allowlisted resource definition, all filter values are bound parameters, and results are database-paginated in stable created-at/id order.

SUPER_ADMIN and OPERATIONS have staff data access; OPERATIONS cannot list users. CORPORATE_ADMIN can list only its own wallets, bill purchases (by the purchase wallet owner), sent remittances and users. It cannot read the global customer, transaction or audit lists. Retail roles are rejected. Corporate scope is taken from the authenticated user's customer, never a requested tenant ID.

DTO projections omit password hashes, refresh credentials, government identity numbers and raw provider request/response bodies. Audit reads expose actor, action, entity, ID and timestamp, not raw identity snapshots. Customer updates preserve a redacted national ID when the optional field is omitted.

Customer balance totals exclude system/biller wallets and are separated by currency. Transaction requests are counted once per request, not once per ledger row. Displayed transaction reversal status is derived from the compensating request while original ledger rows stay immutable.

## Verification and limits

Java 17 `mvn verify`: 45 tests passed, zero failures/errors/skips, including seven administration integration tests with MySQL Testcontainers. Frontend checks cover permissions, session renewal, decimal string submission, review-before-submit, stable retries and workspace interactions. The Nginx deployment was exercised with an isolated MySQL database and synthetic financial data.

Existing per-wallet statements and commission/history services aggregate their ledger histories in memory before pagination; large-history optimization remains a backend scale limitation. CSV exports are explicitly page-level for operational records. Search uses substring matching and may need indexed search for very large installations. There are no new live payment-network integrations or maker/checker approvals.
