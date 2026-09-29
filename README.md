# Poscloud Wallet

A single Java 17 / Spring Boot 3.5 application for customers, wallets, transfers, bill payments, agent rewards, reversals and remittances. Maven, MySQL 8.4, JPA, Validation, Spring Security, JWT, Flyway, Lombok and OpenAPI are included.

All financial movements use `transactions_ledger`. Each row contains both its debit and credit wallet. There are no separate bill-payment, remittance or commission ledgers.

## Run locally

Prerequisites: Docker with Compose and access to an existing MySQL database; Java 17 and Maven 3.6.3+ if running outside Docker.

```sh
docker compose up --build -d
```

The frontend listens on port `8089`; the backend API listens on port `8011`. Browser login accepts requests from the same frontend address, including HTTPS addresses behind a proxy. Swagger UI is at [localhost:8011/swagger-ui/index.html](http://localhost:8011/swagger-ui/index.html), and the OpenAPI document is at [localhost:8011/v3/api-docs](http://localhost:8011/v3/api-docs). Backend health is at `/actuator/health`.

Development login: `admin` / `local-admin-change-me`. Compose uses the Spring development settings in `application-dev.yml`; no Spring settings are needed in `.env`.

On the VPS, open `http://54.36.182.209:8089` or the configured HTTPS proxy address. Browser session requests use relative `/api` URLs on that same address; the frontend's `/api` proxy reaches the backend by its Compose service name.

To run Java directly:

```sh
mvn spring-boot:run
```

The backend uses the development profile settings in `application-dev.yml` when running directly.

For a locally installed MySQL server with user `root` and no password, activate the `dev` profile and leave `DB_USER` and `DB_PASSWORD` unset: the YAML defaults to `root` and an empty password. Do not load the Compose `.env` credentials for this setup. Remove stale `DB_PASSWORD` or `SPRING_DATASOURCE_PASSWORD` overrides from the shell or IDE run configuration if authentication still reports `using password: YES`. The production profile requires `DB_PASSWORD` explicitly.

The Compose file does not start or manage MySQL. The backend uses its database settings from Spring's `application.yml` and `application-dev.yml`.

When a different JDK is your default, set `JAVA_HOME` to a Java 17 installation first. On macOS, `export JAVA_HOME=$(/usr/libexec/java_home -v 17)` selects an installed Java 17 JDK.

## Test and package

```sh
mvn test       # Unit tests; no Docker required
mvn verify     # Unit and integration tests; Docker required
java -jar target/wallet-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

Integration tests launch a disposable real MySQL 8.4 container, apply all Flyway migrations, and validate JPA mappings. They fail if Docker is unavailable; they are not silently skipped. They cover the requested USD 100 → 80.40 → 100 agent scenario, fees, compensation, provider states, authentication, statements, cross-currency settlement funding, and concurrent debit/retry/reversal/payout requests.

If Testcontainers cannot locate Docker Desktop, set `DOCKER_HOST` to the active Docker context socket. Tests never use your normal application database.

After starting the app, run an HTTP smoke test:

```sh
python3 scripts/smoke.py
```

It creates a new development agent and wallet, deposits USD 100, buys USD 20 using the seeded 2% discount, reverses the purchase, and checks the final USD 100 balance. It adds financial history, so use it on a development database. `BASE_URL`, `ADMIN_USERNAME` and `ADMIN_PASSWORD` override its defaults.

If Docker build containers cannot reach Maven Central, package on the host and use the runtime image:

```sh
mvn -DskipTests package
docker build -f Dockerfile.runtime -t poscloud-wallet:local .
```

`Dockerfile.runtime` contains no Maven/network dependency-resolution step. It uses the same Java 17 runtime and non-root user as the source-build image. The main Dockerfile remains a complete source build for environments with repository access.

## Authentication and ownership

`POST /api/auth/login` returns a 24-hour JWT access token and a one-use, seven-day refresh token. Send `Authorization: Bearer <accessToken>`. Refresh tokens are random secrets stored only as SHA-256 hashes and rotated under a row lock. BCrypt hashes user passwords. Blocking a user revokes access through a database status check on every authenticated request.

Customer onboarding is staff-driven. Users authenticate; customers own funds. Public anonymous registration is deliberately not enabled. A super administrator registers users and assigns customers. A corporate administrator can register `CORPORATE_USER` users only for their own corporate customer. All members of one corporate customer share its wallets in this MVP.

| Role/action | Access |
| --- | --- |
| Create customers, activate/block customers, promote agents | SUPER_ADMIN, OPERATIONS |
| Register arbitrary users, block users | SUPER_ADMIN |
| Register own corporate users | CORPORATE_ADMIN |
| Deposit, adjustment, reversal, remittance payout | SUPER_ADMIN, OPERATIONS |
| Currency, transaction type and system-wallet configuration | SUPER_ADMIN |
| Fee, biller and product configuration | SUPER_ADMIN, OPERATIONS |
| Wallet creation, withdrawal, transfer, bill payment, remittance send | Owning customer users or staff |
| Customer, wallet, transaction, statement and commission reads | Owning/participating customer users or staff |

Customers do not receive negative-balance wallets. Only authorized system-wallet configuration can enable overdrafts for system or biller wallets. Customer and wallet status checks apply to both sides of every posting. KYC status is stored; this MVP does not impose jurisdiction-specific KYC tiers or limits.

## Financial rules

- `LedgerService` is the sole balance writer. A wallet exposes no public balance setter.
- A financial command, its idempotency receipt, every ledger entry and its metadata commit in one database transaction.
- Financial commands use `READ_COMMITTED`. The requesting user row serializes that user's retries across application instances. The unique `(user_id, idempotency_key)` constraint prevents duplicate requests.
- The full wallet set is locked in ascending UUID order with `SELECT FOR UPDATE`. Stale, unmodified managed wallet instances are detached before locking so their JPA versions cannot override fresh database state. `@Version` remains an additional safeguard.
- Amounts must be positive and fit both `DECIMAL(19,4)` and the currency's configured precision. Unsupported fractional cents are rejected. Fees, rewards and FX payout calculations round `HALF_UP` once to currency precision.
- Responses encode money as decimal strings. Inputs accept numbers or strings. This preserves exact values in MySQL JSON and JavaScript clients.
- Ledger rows are immutable at ORM and database levels. Triggers reject UPDATE and DELETE. Reversals insert compensating entries under a new unique transaction reference; original rows remain `SUCCESS`. The history API derives `REVERSED` from the compensating request.
- A reversal locks the original request and has a unique original-reference constraint. It reverses every original row, including fees and rewards, using original amounts. A reversal cannot itself be reversed.
- All wallet opening balances are zero. Fund them through a deposit or audited adjustment. `scripts/reconcile.sql` compares balances with the ledger and checks zero net balance per currency.

`Idempotency-Key` is required for all financial POSTs, including adjustments. Use one fresh key for each intended operation and reuse it for transport retries. Keys are scoped to the authenticated user across operation types. Reusing a key with a different operation or DTO payload returns `409 IDEMPOTENCY_CONFLICT`. Payload fingerprints include decimal scale: retry the same payload. Failed validation or insufficient-funds requests roll back entirely and can be retried. A recorded provider `FAILED` result remains failed under the same key. Provider-pending receipts progress when explicitly enquired.

## Fees and agent rewards

Fee lookup considers type, product, customer type, agent flag, currency, amount interval, effective dates and priority. Higher priority wins; ties prefer product, customer type, then agent specificity, followed by UUID for deterministic selection. Effective dates are `[effectiveFrom, effectiveTo)`, while amount bounds are inclusive. Missing rules on a fee-enabled transaction return `FEE_NOT_CONFIGURED`. Development seeds explicit zero-fee defaults for all fee-enabled transaction types/currencies; add higher-priority rules to charge a fee.

`agentOnly = null` matches either customer; `true` matches agents; `false` matches non-agents. Fixed, percentage, and combined fees support minimum/maximum clamps. Rewards use product-specific fixed or percentage settings and optional clamps. Rewards must remain below face value.

For an agent buying USD 20 with a 2% discount and no fee:

| Entry | Debit wallet | Credit wallet | Amount |
| --- | --- | --- | ---: |
| PRINCIPAL | Agent | ZETDC settlement | 19.60 |
| COMMISSION | Commission expense | ZETDC settlement | 0.40 |

The agent ends at USD 80.40 from USD 100; ZETDC receives USD 20. Reversal swaps both pairs and restores USD 100. Cashback posts USD 20 from agent to biller, followed by USD 0.40 from commission expense to agent. Cashback requires sufficient funds for the full purchase and fee before the reward is credited.

Commission reports are derived from ledger entries and show reversal status. Summaries exclude reversed earnings and group by currency so unlike currencies are never added together.

## Billers and provider behavior

ZETDC, USD/ZWG products and settlement wallets are seeded in development. The product's settlement wallet is currency-specific; the biller's wallet is its default. This extra product field is necessary because one biller can sell products in multiple currencies.

`BillerAdapter` defines validation, purchase, enquiry and reverse. The included adapter is a deterministic **mock**, enabled only by the development profile or explicit configuration:

| Customer reference | Mock result |
| --- | --- |
| `INVALID` | Validation fails |
| `FAIL` | FAILED; no debit |
| `PENDING` or `TIMEOUT` | PENDING; no debit until enquiry |
| Other nonblank values | SUCCESS |

`POST /api/bill-payments/{transactionReference}/enquire` resolves pending mock requests once. Pending requests persist their quoted posting instructions, so later fee/reward edits do not reprice them. Repeated enquiries cannot duplicate entries.

**Live-provider boundary:** a MySQL transaction cannot atomically commit a remote purchase. The supplied synchronous adapter is appropriate for this mock MVP only. Pending requests do not reserve funds. Before enabling a real provider, implement durable provider idempotency using the stable transaction reference, funding reservations, timeout/reversal reconciliation and crash recovery. The production profile disables mock purchases and reversals with `PROVIDER_NOT_CONFIGURED`; no live ZETDC connectivity is claimed.

## Remittances

Send deducts source principal and fee into source-currency settlement and fee wallets. The server obtains the exchange rate from `REMITTANCE_RATES`, for example `USD:BWP=13.500000,ZAR:BWP=0.750000`. Same-currency rate is one. Customers cannot supply an exchange rate. The computed payout amount is stored once.

Payout is staff-controlled, checks the recorded receiver ID, and credits the recorded receiver's wallet or an agent wallet. This endpoint represents an operator-attested payout; it does not verify identity documents with an external service. A payout row lock prevents two operators paying the same remittance. No payout wallet can cross currencies. Destination settlement must be prefunded; the dev seed does not grant it an overdraft. An audited adjustment may represent documented external settlement funding in this MVP.

A paid send cannot be reversed until the payout is reversed. Reversing a payout restores `AVAILABLE_FOR_PAYOUT`; reversing an unpaid send sets `REVERSED`. Failed attempts leave lifecycle and balances unchanged. External FX settlement and remittance-network connectivity are not implemented.

## Configuration and production profile

When no profile is explicitly selected, the application uses `dev`, including its local JWT secret, mock provider and development seed data. Set `SPRING_PROFILES_ACTIVE=prod` for production; this replaces the default profile and requires an externally supplied `JWT_SECRET`.

The Compose file is explicitly a local development environment. Production uses `application-prod.yml`, requires `DB_PASSWORD`, `JWT_SECRET` (32+ bytes), and separate `FLYWAY_USER` / `FLYWAY_PASSWORD` migration credentials. Supply a TLS-enabled MySQL `DB_URL`, runtime `DB_USER`, and initial `ADMIN_USERNAME` / `ADMIN_PASSWORD` if bootstrapping a new environment. Remove bootstrap credentials after provisioning. No development wallets, billers, fees or admin password are loaded in production.

Migration credentials must be allowed to create tables, foreign keys and triggers. With binary logging enabled, have the DBA install triggers with the necessary privilege. Compose and disposable test databases enable `log_bin_trust_function_creators` for local migrations; do not grant SUPER to the runtime application user. Restrict runtime ledger access to SELECT/INSERT and audit access to SELECT/INSERT in addition to the triggers.

Expose production through TLS with authentication rate limits and request size/time limits. Configure database backups, recovery checks, secret rotation and reconciliation monitoring before operating with real funds. Swagger is disabled in production. The application does not implement regulatory reporting, sanctions screening, KYC verification, maker-checker approval or live payment-network integrations.

History and statement endpoints cap response sizes with `offset`/`limit`, but currently aggregate a customer's ledger in memory. Add database-side pagination and date-window aggregation before serving large histories. This is an explicit MVP scale limit.

## Project guide

- [Endpoint list](docs/api.md)
- [ERD and transaction/sequence diagrams](docs/diagrams.md)
- [Sample ledger records](docs/sample-ledger.md)
- [Payments engineering review](docs/review.md)
- [Read-only reconciliation SQL](scripts/reconcile.sql)

The project uses the requested `com.poscloud.wallet` feature packages. Controllers expose DTO records, services implement business rules, and repositories persist entities. Extra technical tables are `refresh_tokens`, `transaction_requests` and `number_sequences`; none holds financial balances.

Compatibility was checked against the [Spring Boot 3.5 system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html) and [springdoc documentation](https://springdoc.org/v2/). The project targets Java 17 and pins its Maven dependency versions through the Spring Boot parent.

## React administration

The modern React administration application in `admin/` includes dashboard totals, customer and wallet operations, transactions/reversals, bills, remittances, commissions, users/access, configuration and a read-only audit trail. Browser sessions use HttpOnly refresh cookies and in-memory access tokens. New administration queries enforce staff/corporate scope on the server.

Restart the updated backend, then run `cd admin && npm ci && npm run dev` and open `http://localhost:5173`. Vite proxies API requests to `http://127.0.0.1:8011` by default, preserving the public Host and HTTPS scheme when accessed through a tunnel. The browser always calls the frontend origin and does not need cross-origin access. Set `ADMIN_ORIGIN` on the backend only for additional explicitly trusted origins. Docker Compose uses Nginx to proxy `/api` to `http://backend:8011`; the administration app is served at `http://localhost:8089`.

See [administration setup](admin/README.md), [API integration review](docs/admin-integration.md) and [production deployment](docs/admin-deployment.md). Production hosting requires the target server/domain and secrets; no live deployment has been performed.
