# Cashword administration

React + TypeScript + Vite, Tailwind CSS, React Router, TanStack Query, React Hook Form and Zod. A responsive core banking administration workspace integrated with the Spring Boot backend.

## Run locally

Use Node 22.12+ and the updated Java 17 backend. Vite runs at **http://localhost:5173**, with `/api` proxied to **http://127.0.0.1:8011**. An HTTPS tunnel to Vite can use the same relative `/api` URLs.

```sh
cd admin
npm ci
npm run dev
```

Use your backend account. No password is embedded in React. Browser authentication accepts the same origin that serves the frontend. `ADMIN_ORIGIN` can also name additional trusted origins, separated by commas. `BACKEND_URL` is an optional server-side Vite proxy setting; the browser always calls relative `/api` URLs. Vite logs proxy request headers with credentials redacted and backend status in development. Set `PROXY_DEBUG=0` to silence those logs.

```sh
npm test
npm run build
```

## Workspaces

- Overview: live customer/account/request counts, remittances awaiting payout, and customer-wallet balances separated by currency. System and settlement balances are excluded from the customer balance totals. Corporate transaction counts reflect requests initiated by its users.
- Customers: paginated search, status/date filters, individual/corporate registration, edits, activation/blocking, and agent designation. National IDs are excluded from read DTOs; ordinary edits preserve the stored value.
- Wallets: paginated account search, creation, balances, activation/blocking, deposits, withdrawals, sends, audited adjustments and paginated statements with CSV page export. Statement running balances include preceding entries.
- Transactions: global staff search, date/status filters, details with ledger rows and full-transaction reversals.
- Bill payments: purchase, metadata list, provider status and enquiries. Development mock purchases are clearly labelled.
- Remittances: send, search, exchange-rate/payout details and staff payout with receiver-ID verification.
- Commissions: agent search, paginated ledger report and currency-separated summaries excluding reversed earnings.
- Users: create, assign roles and update status/access. Corporate administrators can manage only their own corporate users. Self access changes and removal of the final active super administrator are rejected.
- Configuration: currencies, transaction types, fee rules, billers, products and system wallets with create/edit/activate/deactivate operations. Restricted operations are enforced by the server.
- Audit: staff-only read-only search by actor/action/entity, date filters and CSV page exports. Raw identity snapshots and credentials are not exposed.

Lists use page sizes of 20. Date filters use the browser's local day boundaries (the selected end date is inclusive). Configuration searches/exports apply to the filtered configuration list; operational exports explicitly export the current page. This is not a full database backup or an all-history report.

## Permissions and sessions

SUPER_ADMIN can use all workspaces. OPERATIONS can use all except user administration, and cannot write currencies, transaction types or system-wallet configuration. CORPORATE_ADMIN has its own wallets, bill payments, remittances and team users. Retail roles cannot sign in to this platform. The backend scopes reads and checks mutations independently of the frontend.

The refresh token is an HttpOnly, SameSite=Strict cookie under `/api/auth/browser`, Secure in production. Access tokens live only in memory. Browser session POSTs require the frontend's Origin or an explicitly trusted Origin. No token is stored in local/session storage. Refresh is deduplicated within the tab and uses Web Locks across tabs where supported; browsers without Web Locks may require a new login if simultaneous rotations race.

Logout revokes its refresh token and increments the user's access-token version. Other devices' access tokens become invalid but their unrevoked refresh tokens may renew. Same-origin tabs receive a logout notification containing no credentials. Access changes also increment token version.

## Financial request handling

Amounts stay decimal strings in the browser and are validated/calculated as BigDecimal by the backend. Forms require review before submission. Fees and FX are applied by the existing backend services; the review is not a guaranteed fee/FX quote. The result includes the actual transaction reference and financial totals.

Every financial submission uses `Idempotency-Key`. On uncertain results, retry the unchanged request. The tab retains only a random key indexed by a hash of the actor, endpoint and payload in session storage, allowing the same request to be recovered after reopening/reloading. It stores no plaintext form payload or tokens. Successful completion clears this pending identifier, so a new deliberate operation can be submitted. Keep uncertain requests in the same tab; do not switch browsers and re-enter them as new operations. Reconcile transaction history before starting another request if the outcome is unclear.

All balance movements continue through LedgerService. Reversal creates compensating rows; the UI never edits balances or posted ledger rows directly.

## Deployment

From the repository root, run `docker compose up --build`. It starts the Spring backend and React frontend using `application-dev.yml`; no Spring variables are needed in `.env`. Open **http://localhost:8089**. The backend listens on **http://localhost:8011**. Optional `.env` values only change host ports. When running Vite outside Compose, `BACKEND_URL` defaults to `http://127.0.0.1:8011`.

This repository has one Compose file: `docker-compose.yml`. It runs the Spring backend and React frontend; the backend uses its configured database. Nginx serves the frontend and proxies `/api` to the backend, preserving the browser Origin, cookies and authorization headers. Production hosting needs a separate deployment environment, HTTPS domain, database and secrets.

The backend still uses a mock biller in development and has no live biller/remittance-network adapter. Production rejects unconfigured provider operations. Existing reconciliation, funding and compliance limitations in the root README remain applicable.
