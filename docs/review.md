# Payments engineering review

This review covers the implemented mock-provider MVP. See README for the live-provider boundary and operating prerequisites.

| Area | Implementation and verification |
| --- | --- |
| Double spending | The complete wallet posting set is locked in UUID order. Concurrent users withdrawing 80 from the same 100 wallet produce one success and one insufficient-funds result. |
| JPA stale state | Wallet lookup can happen before a competing transaction commits. LedgerService detaches only the unmodified wallet and reloads it with a write lock. Versioned updates use the locked current version. |
| MySQL gap locks | Financial commands use READ_COMMITTED. This avoids absent-key gap-lock contention when independent users create receipts. Pessimistic wallet and actor locks remain held through commit. |
| Opposite transfers | Two simultaneous transfers in opposite directions finish without losing balances. |
| Idempotency | User row serialization, a unique user/key constraint, request fingerprint, and persisted JSON response. Concurrent retries return the same result. Reused keys with different payloads conflict. |
| Transaction boundaries | IdempotencyService owns the financial transaction; LedgerService requires an existing transaction. Principal, fee, reward, receipt, audit and business metadata roll back together. Insufficient funds for a fee leave the principal untouched. |
| Ledger accounting | Each row debits and credits equal amounts in the same currency. Database composite foreign keys enforce both wallet currencies. System wallets start at zero and acquire balances only through ledger movements. |
| Balance ownership | Wallet has no public balance setter. Only LedgerService calls package-private applyBalance. No business service uses SQL to write balances. |
| Immutable history | No financial DELETE endpoints or ledger repository mutation methods. Hibernate @Immutable and MySQL triggers reject ledger UPDATE/DELETE, tested with direct SQL. |
| Duplicate fees/rewards | Entries share the same receipt/transaction boundary. Replays do not call the posting path. Pending mock transactions preserve their quoted posting instructions. |
| Agent discount | 100 initial, 20 face value, 2% discount produces principal 19.60 and commission expense 0.40 to settlement; final agent balance 80.40. Both rows reverse exactly to restore 100. |
| Cashback | Full face value goes to settlement and expense credits the agent separately. Compensation reverses both, so commission is not retained. |
| Reversal races | Lock original request and enforce unique original_transaction_reference. Concurrent reversals produce only one compensating transaction. |
| Remittance payout | Lock remittance before checking status. Destination currency, receiver identity and beneficiary/agent checks apply. Concurrent payout produces one financial posting. |
| Remittance reversal | Send cannot reverse while payout remains paid. Payout compensation reopens availability; send compensation then marks the remittance reversed. |
| FX | Rate is server-configured; payout is computed once using BigDecimal. Each ledger row has one currency. Destination settlement cannot go negative unless a super administrator explicitly enables it. |
| Precision | BigDecimal throughout. Positive/precision/overflow checks precede postings. Monetary JSON values use strings to avoid binary-number conversion. Fee/reward calculations round to currency precision. |
| Authorization | Ownership checks cover money movement and reads. Staff-only money creation/adjustment/reversal/payout. Corporate admins cannot attach users to another customer. Blocked JWT users are rejected. |
| Refresh tokens | Random high-entropy secret, hashed at rest, expiry, single-use rotation under a row lock. Blocked users cannot refresh. |
| Documentation | Distinct OpenAPI DTO schema names prevent nested Request/View records from overwriting one another. |

## Deliberate MVP limits

1. Remote provider work cannot be made atomic by a database transaction. The synchronous mock is disabled by the production profile. Live integration needs durable provider idempotency, reservations, bounded calls, reconciliation and crash recovery before activation. Pending mock requests hold no funds.
2. The application models remittance settlement, not an external remittance or FX network. Operators must independently verify identity and external prefunding. Static configured rates are suitable only for controlled MVP scenarios.
3. History and statement calculations currently load relevant ledger rows before paging the DTO response. Database-side range queries and pagination are needed for large histories. Commission totals are currency-separated.
4. Financial commands from one user serialize. This trades throughput for straightforward cross-instance idempotency. It does not globally serialize all users.
5. API-level registration and mutations are validated; production operators must control direct database access. Use a separate migration identity and least-privilege runtime credentials. DBA credentials can still alter/drop tables and triggers, so database access controls and backups remain necessary.
6. KYC fields, roles and audit records are present; jurisdiction-specific limits, sanctions screening, maker-checker approval, authentication throttling and regulatory reporting are outside this implementation.

No claim of atomic exactly-once execution against a live remote provider is made. Local database financial posting, idempotency and compensation are exercised against real MySQL.

## Verification record

Final Java 17 `mvn verify`: **33 tests passed**, zero failures, errors or skips. Tests used MySQL 8.4 through Testcontainers.

| Suite | Tests |
| --- | ---: |
| FeeSelectionTest | 1 |
| FeeRewardTest | 2 |
| WalletIT | 3 |
| IdentityIT | 4 |
| ConcurrencyIT | 5 |
| ApiSecurityIT | 5 |
| FinancialFlowsIT | 13 |
