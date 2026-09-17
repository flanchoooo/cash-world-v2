# API endpoints

All endpoints require a bearer token except login, refresh, health and development OpenAPI. Financial commands require `Idempotency-Key`. Request/response schemas are available in Swagger.

| Method | Path |
| --- | --- |
| POST | `/api/auth/register` |
| POST | `/api/auth/login` |
| POST | `/api/auth/refresh` |
| GET | `/api/auth/me` |
| POST | `/api/auth/users/{id}/block` |
| POST | `/api/admin/billers` |
| PUT | `/api/admin/billers/{id}` |
| GET | `/api/admin/billers` |
| GET | `/api/admin/billers/{id}` |
| POST | `/api/admin/billers/{id}/activate` |
| POST | `/api/admin/billers/{id}/deactivate` |
| POST | `/api/admin/biller-products` |
| PUT | `/api/admin/biller-products/{id}` |
| GET | `/api/admin/biller-products` |
| GET | `/api/admin/biller-products/{id}` |
| POST | `/api/admin/biller-products/{id}/activate` |
| POST | `/api/admin/biller-products/{id}/deactivate` |
| POST | `/api/bill-payments` |
| POST | `/api/bill-payments/{ref}/enquire` |
| GET | `/api/agents/{number}/commissions` |
| GET | `/api/agents/{number}/commissions/summary` |
| POST | `/api/admin/currencies` |
| PUT | `/api/admin/currencies/{id}` |
| GET | `/api/admin/currencies` |
| GET | `/api/admin/currencies/{id}` |
| POST | `/api/admin/currencies/{id}/activate` |
| POST | `/api/admin/currencies/{id}/deactivate` |
| POST | `/api/admin/wallet-types` |
| PUT | `/api/admin/wallet-types/{id}` |
| GET | `/api/admin/wallet-types` |
| GET | `/api/admin/wallet-types/{id}` |
| POST | `/api/admin/wallet-types/{id}/activate` |
| POST | `/api/admin/wallet-types/{id}/deactivate` |
| POST | `/api/customers/individuals` |
| POST | `/api/customers/corporates` |
| GET | `/api/customers/{number}` |
| PUT | `/api/customers/{number}` |
| POST | `/api/customers/{number}/activate` |
| POST | `/api/customers/{number}/block` |
| POST | `/api/customers/{number}/make-agent` |
| POST | `/api/admin/fees` |
| PUT | `/api/admin/fees/{id}` |
| GET | `/api/admin/fees` |
| GET | `/api/admin/fees/{id}` |
| POST | `/api/admin/fees/{id}/activate` |
| POST | `/api/admin/fees/{id}/deactivate` |
| POST | `/api/remittances/send` |
| POST | `/api/remittances/{ref}/payout` |
| GET | `/api/remittances/{ref}` |
| POST | `/api/transactions/{ref}/reverse` |
| POST | `/api/admin/wallets/adjust` |
| GET | `/api/transactions/{ref}` |
| GET | `/api/customers/{number}/transactions` |
| POST | `/api/admin/transaction-types` |
| PUT | `/api/admin/transaction-types/{id}` |
| GET | `/api/admin/transaction-types` |
| GET | `/api/admin/transaction-types/{id}` |
| POST | `/api/admin/transaction-types/{id}/activate` |
| POST | `/api/admin/transaction-types/{id}/deactivate` |
| POST | `/api/admin/system-wallets` |
| PUT | `/api/admin/system-wallets/{id}` |
| GET | `/api/admin/system-wallets` |
| GET | `/api/admin/system-wallets/{id}` |
| POST | `/api/admin/system-wallets/{id}/activate` |
| POST | `/api/admin/system-wallets/{id}/deactivate` |
| POST | `/api/wallets` |
| GET | `/api/wallets/{number}` |
| POST | `/api/wallets/{number}/block` |
| POST | `/api/wallets/{number}/activate` |
| POST | `/api/wallets/deposit` |
| POST | `/api/wallets/withdraw` |
| POST | `/api/wallets/send-money` |
| GET | `/api/wallets/{number}/transactions` |
| GET | `/api/wallets/{number}/statement` |

## Request examples

Create an individual with `firstName`, `lastName`, and `mobileNumber`. Create a corporate with `companyName`, `registrationNumber`, and `mobileNumber`. The response contains the customer UUID and generated `CUS00000001`-style number.

Create a wallet:

```json
{"customerId":"<UUID>","walletTypeId":"<WALLET TYPE UUID>","currencyId":"<USD UUID>","name":"Personal USD"}

Creating a customer automatically creates six zero-balance wallets: Airtime USD/ZWG, Wallet USD/ZWG, and Bill Payment USD/ZWG. Additional active currencies remain available for manual wallet creation.
```

Register a customer user as SUPER_ADMIN:

```json
{"username":"agent1","password":"a-long-unique-password","customerId":"<UUID>","role":"AGENT"}
```

Deposit or withdraw:

```json
{"walletNumber":"<wallet number>","amount":"100.00"}
```

Send money:

```json
{"sourceWalletNumber":"<source>","destinationWalletNumber":"<destination>","amount":"50.00"}
```

Bill payment:

```json
{"walletNumber":"<wallet>","productCode":"ZETDC_USD","customerReference":"123456789","amount":"20.00"}
```

Adjustment:

```json
{"wallet":"<wallet>","amount":"10.00","direction":"CREDIT","reason":"Approved cash reconciliation"}
```

Reverse:

```json
{"reason":"Customer refund approved"}
```

Remittance send (rate is obtained from server configuration):

```json
{"walletNumber":"<source>","destinationCurrency":"BWP","amount":"100.00","receiverCustomerId":"<receiver UUID>","senderName":"Sender","senderMobile":"263771000001","receiverName":"Receiver","receiverMobile":"26771000001","receiverIdNumber":"ID123","destinationCountry":"BW"}
```

Payout:

```json
{"walletNumber":"<receiver or agent BWP wallet>","receiverIdNumber":"ID123"}
```

Fee configuration (use UUIDs from the admin GET endpoints):

```json
{"transactionTypeId":"<WITHDRAWAL UUID>","currencyId":"<USD UUID>","calculationType":"FIXED","fixedAmount":"1.00","effectiveFrom":"2026-01-01T00:00:00Z","priority":100,"status":"ACTIVE"}
```

Admin PUT operations replace the configurable fields; send the full request schema. Immutable identifiers, currency definitions and product currencies cannot be changed. There are no DELETE endpoints.

## Reads and errors

History responses group ledger rows by transaction reference. Statements include separate debit/credit columns and a running balance that carries forward across pages. Histories default to `offset=0&limit=50` (maximum 200); statements default to `offset=0&limit=100` (maximum 1000). Commission reports use the same 200-row maximum. Commission summaries return a list grouped by currency UUID.

Errors use `code`, `message`, and usually `timestamp`. Authentication filter errors may omit the timestamp. Validation returns 400; missing entities 404; unauthorized/forbidden operations 401/403; idempotency conflicts and duplicate reversals 409. A retryable database contention conflict is 409 and must be retried with the original key. Provider FAILED/PENDING are transaction result statuses, not HTTP transport failures.

The `biller`, `product` and wallet IDs in transaction/commission DTOs are UUID references. Resolve configuration descriptions through admin reads. No controller returns a JPA entity.
