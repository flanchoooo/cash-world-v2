# Data model and transaction flows

The diagrams describe the implemented single application. Camel-case entity labels below map to the snake-case SQL tables. `transactionRequests` stores idempotency receipts and lifecycle coordination; it is not a financial ledger. Every movement is in `transactionsLedger`.

## Entity relationship diagram

```mermaid
erDiagram
    customers |o--o{ users : authenticates
    customers |o--o{ wallets : owns
    users ||--o{ refreshTokens : rotates
    users ||--o{ transactionRequests : submits
    currencies ||--o{ wallets : denominates
    transactionTypes ||--o{ transactionsLedger : classifies
    transactionRequests ||--o{ transactionsLedger : groups
    wallets ||--o{ transactionsLedger : debits
    wallets ||--o{ transactionsLedger : credits
    currencies ||--o{ transactionsLedger : denominates
    billers ||--o{ billerProducts : offers
    wallets ||--o{ billerProducts : settles
    currencies ||--o{ billerProducts : prices
    billerProducts |o--o{ fees : scopes
    transactionTypes ||--o{ fees : prices
    currencies ||--o{ fees : denominates
    transactionRequests ||--o| billPayments : describes
    billerProducts ||--o{ billPayments : fulfils
    billers ||--o{ billPayments : provides
    transactionRequests ||--o| remittances : sends
    customers |o--o{ remittances : sends
    customers |o--o{ remittances : receives
    currencies ||--o{ remittances : sourceCurrency
    currencies ||--o{ remittances : payoutCurrency
    users |o--o{ auditLogs : acts
    customers |o--o{ auditLogs : concerns
    customers {
        uuid id PK
        string customerNumber UK
        string customerType
        boolean isAgent
        string kycStatus
        string status
    }
    users {
        uuid id PK
        uuid customerId FK
        string username UK
        string passwordHash
        string role
        string status
    }
    wallets {
        uuid id PK
        string walletNumber UK
        uuid customerId FK
        uuid currencyId FK
        string walletType
        decimal balance
        boolean allowNegativeBalance
        bigint version
    }
    transactionsLedger {
        uuid id PK
        string transactionReference FK
        uuid debitWalletId FK
        uuid creditWalletId FK
        decimal amount
        uuid currencyId FK
        string entryType
        string originalTransactionReference FK
        bigint sequenceNumber UK
    }
    transactionRequests {
        uuid id PK
        string transactionReference UK
        uuid userId FK
        string idempotencyKey
        string requestHash
        string originalTransactionReference UK
        json resultData
    }
    remittances {
        uuid id PK
        string remittanceReference UK
        string transactionReference FK
        string payoutTransactionReference FK
        decimal exchangeRate
        decimal payoutAmount
        string status
    }
    billPayments {
        uuid id PK
        string transactionReference UK
        uuid billerProductId FK
        string providerReference
        json requestData
        json responseData
    }
```

## Transaction flow

```mermaid
flowchart TD
    request[Authenticated request] --> validate[Validate DTO and role]
    validate --> actorLock[Lock requesting user]
    actorLock --> receipt{Existing key?}
    receipt -->|Matching request| replay[Return saved result]
    receipt -->|Different request| conflict[Return conflict]
    receipt -->|New key| create[Create unique receipt]
    create --> terms[Calculate money and fees]
    terms --> locks[Lock wallets by UUID]
    locks --> checks[Check currencies and funds]
    checks --> ledger[Insert paired ledger rows]
    ledger --> balances[Update wallet balances]
    balances --> save[Save result and metadata]
    save --> commit[Commit together]
    checks -->|Invalid| rollback[Roll back request]
```

## Bill payment sequence

```mermaid
sequenceDiagram
    participant client as Client
    participant service as BillPaymentService
    participant fee as FeeService
    participant ledger as LedgerService
    participant provider as MockBillerAdapter
    participant db as MySQL
    client->>service: POST bill-payments with Idempotency-Key
    service->>db: Lock user and create receipt
    service->>fee: Calculate applicable fee
    fee-->>service: Fee in wallet currency
    service->>ledger: Lock and preflight all postings
    ledger->>db: SELECT wallets FOR UPDATE
    service->>provider: Validate and purchase with transaction reference
    alt Provider success
        provider-->>service: SUCCESS and provider reference
        service->>ledger: Post principal and fee
        ledger->>db: Insert ledger and update balances
        service->>db: Save provider metadata and receipt
        service-->>client: Commit and return SUCCESS
    else Pending or failed
        provider-->>service: PENDING or FAILED
        service->>db: Save snapshot and provider status
        service-->>client: Commit status without financial rows
    end
```

## Agent discount sequence

```mermaid
sequenceDiagram
    participant agent as Agent
    participant service as BillPaymentService
    participant reward as RewardService
    participant provider as MockBillerAdapter
    participant ledger as LedgerService
    agent->>service: Buy face value USD 20 from USD 100 wallet
    service->>reward: DISCOUNT at 2 percent
    reward-->>service: Commission USD 0.40
    service->>ledger: Preflight USD 19.60 debit and USD 0.40 expense
    service->>provider: Purchase USD 20 face value
    provider-->>service: SUCCESS
    service->>ledger: Agent to biller USD 19.60 PRINCIPAL
    service->>ledger: Commission expense to biller USD 0.40 COMMISSION
    service-->>agent: Commit one reference; balance USD 80.40
```

The two posting arrows describe entries in one atomic `postBatch` call. Cashback instead posts USD 20 from agent to biller, then USD 0.40 from commission expense to agent. Any fee is an additional entry in the same batch.

## Remittance send sequence

```mermaid
sequenceDiagram
    participant client as Sender
    participant service as RemittanceService
    participant rates as ExchangeRateService
    participant ledger as LedgerService
    participant db as MySQL
    client->>service: Send amount and destination currency
    service->>db: Lock user and check idempotency receipt
    service->>rates: Get configured currency pair rate
    rates-->>service: Trusted rate
    service->>service: Calculate payout and send fee
    service->>ledger: Sender to source settlement plus fee
    ledger->>db: Lock wallets; insert entries; update balances
    service->>db: Save remittance AVAILABLE_FOR_PAYOUT
    service-->>client: Commit and return remittance reference
```

Payout locks the remittance, checks receiver identity and the target wallet, and debits the destination-currency settlement wallet. Source and destination currencies never coexist in a ledger row. Cross-currency destination settlement requires separate prefunding.

## Reversal sequence

```mermaid
sequenceDiagram
    participant operator as Operator
    participant service as ReversalService
    participant provider as MockBillerAdapter
    participant ledger as LedgerService
    participant db as MySQL
    operator->>service: Reverse original reference with new key
    service->>db: Lock user and original request
    service->>db: Check unique reversal and remittance lifecycle
    service->>ledger: Preflight original amounts with wallets swapped
    opt Bill payment
        service->>provider: Reverse using new and original references
        provider-->>service: SUCCESS
    end
    service->>ledger: Post all compensating entries in one batch
    ledger->>db: Insert REVERSAL rows; update balances
    service->>db: Link original reference and append audit
    service-->>operator: Commit new reference
```

Posted original rows retain `SUCCESS`; the transaction query derives `REVERSED` from the unique compensating request. Reversing a reversal is prohibited.
