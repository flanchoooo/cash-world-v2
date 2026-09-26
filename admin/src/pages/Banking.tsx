import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { SlidersHorizontal } from "lucide-react";
import { api, type User } from "../lib/session";
import {
  field,
  choice,
  checkboxes,
  lookup,
  money,
  csv,
  type Row,
  type Page,
  type Action,
} from "../lib/operations";
import {
  ActionDialog,
  DataTable,
  Details,
  useDebounced,
} from "../components/Operations";
import { Dialog } from "../components/Dialog";
import { canPerform, workspaces } from "../lib/workspaces";
const customerFields = [
  field("firstName", "First name", { maxLength: 100 }),
  field("lastName", "Last name", { maxLength: 100 }),
  field("nationalId", "National ID", { required: false, maxLength: 100 }),
  field("mobileNumber", "Mobile number", { maxLength: 40 }),
  field("email", "Email", { type: "email", required: false, maxLength: 254 }),
  field("address", "Address", { required: false, maxLength: 500 }),
];
const corporateFields = [
  field("companyName", "Company name", { maxLength: 200 }),
  field("registrationNumber", "Registration number", { maxLength: 100 }),
  ...customerFields.slice(3),
];
const walletLookup = (name = "walletNumber", label = "Wallet") =>
  lookup(name, label, "/api/admin/workspace/wallets", "walletNumber");
const columns: Record<string, [string, string][]> = {
  customers: [
    ["customerNumber", "Customer"],
    ["customerType", "Type"],
    ["name", "Name"],
    ["mobileNumber", "Mobile"],
    ["status", "Status"],
  ],
  wallets: [
    ["walletNumber", "Wallet"],
    ["name", "Name"],
    ["currency", "Currency"],
    ["balance", "Balance"],
    ["status", "Status"],
  ],
  transactions: [
    ["transactionReference", "Reference"],
    ["transactionType", "Type"],
    ["performedBy", "Performed by"],
    ["senderCurrency", "Sender currency"],
    ["senderAmount", "Sender amount"],
    ["senderFee", "Sender fee"],
    ["totalCollected", "Total collected"],
    ["exchangeRate", "Exchange rate"],
    ["recipientCurrency", "Recipient currency"],
    ["recipientAmount", "Recipient amount"],
    ["convertedFee", "Converted fee"],
    ["status", "Status"],
  ],
  "bill-payments": [
    ["transactionReference", "Reference"],
    ["productCode", "Product"],
    ["customerReference", "Customer reference"],
    ["status", "Provider status"],
  ],
  remittances: [
    ["receiverName", "Receiver"],
    ["sourceCurrency", "Send currency"],
    ["sendAmount", "Send amount"],
    ["feeAmount", "Fee"],
    ["exchangeRate", "Exchange rate"],
    ["destinationCurrency", "Payout currency"],
    ["payoutAmount", "Payout"],
    ["createdBy", "Created by"],
    ["cashedOutBy", "Cashed out by"],
    ["status", "Status"],
  ],
  users: [
    ["username", "Username"],
    ["customerNumber", "Customer"],
    ["role", "Role"],
    ["mobilePinConfigured", "Mobile PIN"],
    ["status", "Status"],
  ],
  audit: [
    ["createdAt", "Date"],
    ["username", "User"],
    ["action", "Action"],
    ["entityType", "Entity"],
    ["entityId", "Record"],
  ],
};
const statuses: Record<string, string[]> = {
  customers: ["ACTIVE", "BLOCKED", "SUSPENDED", "CLOSED"],
  wallets: ["ACTIVE", "BLOCKED", "CLOSED"],
  users: ["ACTIVE", "BLOCKED", "DISABLED"],
  transactions: ["SUCCESS", "PENDING", "FAILED", "REVERSED"],
  "bill-payments": ["SUCCESS", "PENDING", "FAILED"],
  remittances: [
    "AVAILABLE_FOR_PAYOUT",
    "PAID",
    "PENDING",
    "FAILED",
    "REVERSED",
  ],
};
export function Banking({ resource, user }: { resource: string; user: User }) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [search, setSearch] = useState("");
  const term = useDebounced(search);
  const [status, setStatus] = useState("");
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<Row | null>(null);
  const [action, setAction] = useState<Action | null>(null);
  const [statement, setStatement] = useState<string | null>(null);
  const [customerOnboarding, setCustomerOnboarding] = useState<boolean | null>(
    null,
  );
  const staff = user.role !== "CORPORATE_ADMIN";
  const ws = workspaces.find((w) => w.id === resource)!;
  const params = new URLSearchParams({
    search: term,
    status,
    page: String(page),
    size: "20",
  });
  if (from) params.set("from", new Date(from + "T00:00:00").toISOString());
  if (to) {
    const end = new Date(to + "T00:00:00");
    end.setDate(end.getDate() + 1);
    params.set("to", end.toISOString());
  }
  const q = useQuery({
    queryKey: ["records", resource, params.toString()],
    queryFn: () => api<Page>("/api/admin/workspace/" + resource + "?" + params),
  });
  const catalog = useQuery({
    queryKey: ["catalog"],
    queryFn: () =>
      api<{ currencies: Row[]; products: Row[]; mockProvider: boolean }>(
        "/api/admin/workspace/catalog",
      ),
  });
  const rows = (q.data?.items ?? []).map((r) =>
    resource === "customers"
      ? {
          ...r,
          name:
            r.companyName ||
            [r.firstName, r.lastName].filter(Boolean).join(" "),
        }
      : r,
  );
  const currencies = (catalog.data?.currencies ?? []).map((r) =>
    String(r.code),
  );
  const detail = useQuery({
    queryKey: ["detail", resource, selected?.id],
    enabled: !!selected && ["transactions", "remittances"].includes(resource),
    queryFn: () =>
      api<Row>(
        resource === "transactions"
          ? "/api/transactions/" + selected!.transactionReference
          : "/api/remittances/" + selected!.remittanceReference,
      ),
  });
  const userAccess = useQuery({
    queryKey: ["user-access", selected?.id],
    enabled: resource === "users" && !!selected,
    queryFn: () => api<Row>(`/api/auth/users/${selected!.id}`),
  });
  const start = (a: Action) => {
    setSelected(null);
    setAction(a);
  };
  function cash(kind: string, row?: Row) {
    start({
      title:
        kind === "send-money"
          ? "Send money"
          : kind === "deposit"
            ? "Deposit funds"
            : "Withdraw funds",
      path: "/api/wallets/" + kind,
      financial: true,
      fields:
        kind === "send-money"
          ? [
              walletLookup("sourceWalletNumber", "Source wallet"),
              field("destinationWalletNumber", "Destination wallet number"),
              money(),
            ]
          : [walletLookup(), money()],
      initial: row
        ? {
            walletNumber: row.walletNumber,
            sourceWalletNumber: row.walletNumber,
          }
        : {},
    });
  }
  function createCustomer(corporate: boolean) {
    setCustomerOnboarding(corporate);
  }
  function createWallet() {
    start({
      title: "Create customer wallet",
      path: "/api/wallets",
      fields: [
        ...(staff
          ? [lookup("customerId", "Customer", "/api/admin/workspace/customers")]
          : [field("customerId", "Corporate customer", { disabled: true })]),
        lookup("walletTypeId", "Wallet type", "/api/admin/wallet-types"),
        lookup("currencyId", "Currency", "/api/admin/currencies"),
        field("name", "Wallet name"),
      ],
      initial: staff ? {} : { customerId: user.customerId },
    });
  }
  function createUser() {
    start({
      title: "Create user",
      path: "/api/auth/register",
      fields: [
        field("username", "Username", { maxLength: 100 }),
        field("password", "Temporary password", {
          type: "password",
          minLength: 8,
          maxLength: 72,
          help: "Use 8 to 72 characters and share through your approved secure channel.",
        }),
        ...(staff
          ? [
              lookup(
                "customerId",
                "Customer",
                "/api/admin/workspace/customers",
                "id",
                false,
              ),
              choice("role", "Role", [
                "SUPER_ADMIN",
                "OPERATIONS",
                "CUSTOMER",
                "CORPORATE_ADMIN",
                "CORPORATE_USER",
                "AGENT",
              ]),
              checkboxes("permissions", "Operations permissions (only used for Operations role)", "/api/auth/permissions", (option) => `${String(option.area)} · ${String(option.name)}`, "code"),
            ]
          : [
              field("customerId", "Corporate customer", { disabled: true }),
              choice("role", "Role", ["CORPORATE_USER"]),
            ]),
        field("mobileNumber", "Mobile", { required: false, maxLength: 40 }),
        field("email", "Email", {
          type: "email",
          required: false,
          maxLength: 254,
        }),
        field("mobilePin", "Mobile PIN", {
          type: "password",
          required: false,
          minLength: 4,
          maxLength: 4,
          pattern: "[0-9]{4}",
          inputMode: "numeric",
          title: "Enter exactly four digits.",
          help: "Optional four-digit PIN for customer and agent transaction approval.",
        }),
      ],
      initial: staff
        ? { permissions: [] }
        : { customerId: user.customerId, role: "CORPORATE_USER" },
    });
  }
  function bill() {
    start({
      title: "Purchase bill payment",
      path: "/api/bill-payments",
      financial: true,
      note: catalog.data?.mockProvider
        ? "Development mock provider is enabled. This does not purchase a live bill."
        : "Provider availability is enforced by the backend.",
      fields: [
        walletLookup(),
        choice(
          "productCode",
          "Product",
          (catalog.data?.products ?? []).map((r) => String(r.code)),
        ),
        field("customerReference", "Customer / meter reference", {
          maxLength: 100,
        }),
        money(),
      ],
    });
  }
  function remit() {
    start({
      title: "Send remittance",
      path: "/api/remittances/send",
      quotePath: "/api/remittances/quote",
      financial: true,
      fullPage: true,
      note: "Enter the cash remittance details. Leave the fee override blank to use the automatically calculated fee.",
      fields: [
        choice("sourceCurrency", "Sender currency", currencies),
        choice("destinationCurrency", "Destination currency", currencies),
        money("amount", "Amount to send"),
        money("feeAmount", "Fee override", false),
        field("senderMobile", "Sender mobile", {
          maxLength: 40,
          help: "Saved sender details will fill automatically.",
        }),
        field("senderName", "Sender name", { maxLength: 200 }),
        field("senderIdNumber", "Sender national ID", { maxLength: 100 }),
        field("receiverMobile", "Recipient mobile", {
          maxLength: 40,
          help: "Saved recipient details will fill automatically.",
        }),
        field("receiverName", "Recipient name", { maxLength: 200 }),
        field("reasonForSending", "Reason for sending", { maxLength: 500 }),
        choice("sourceOfFunds", "Source of funds", [
          "SALARY",
          "BUSINESS_INCOME",
          "SAVINGS",
          "SALE_OF_ASSET",
          "GIFT",
          "OTHER",
        ]),
        field("receiverIdNumber", "Recipient national ID", {
          maxLength: 100,
        }),
        field("proofOfPayment", "Proof of payment", {
          type: "file",
          required: false,
          accept: "application/pdf,image/jpeg,image/png,image/webp",
          maxBytes: 5 * 1024 * 1024,
          help: "Optional PDF or image, up to 5 MB.",
        }),
      ],
      autoFill: [
        {
          field: "senderMobile",
          path: (value) =>
            `/api/remittances/saved-details?party=SENDER&mobile=${encodeURIComponent(value)}`,
        },
        {
          field: "receiverMobile",
          path: (value) =>
            `/api/remittances/saved-details?party=RECIPIENT&mobile=${encodeURIComponent(value)}`,
        },
      ],
      receiptSharePath: (row) =>
        `/api/remittances/${encodeURIComponent(String(row.remittanceReference))}/receipt-share`,
      shareRecipient: (row) => String(row.senderMobile ?? ""),
      shareText: (row, receiptUrl) =>
        [
          `Hello ${row.senderName}, your remittance to ${row.receiverName} has been recorded successfully.`,
          `Amount sent: ${row.sendAmount} ${row.senderCurrency}. Fee: ${row.feeAmount} ${row.senderCurrency}. Total paid: ${row.totalCollected} ${row.senderCurrency}.`,
          row.senderCurrency !== row.destinationCurrency
            ? `Converted fee equivalent: ${row.destinationFeeAmount} ${row.destinationCurrency}. Exchange rate: ${row.exchangeRate}.`
            : null,
          `The recipient will receive ${row.payoutAmount} ${row.destinationCurrency}.`,
          `Transaction reference: ${row.remittanceReference}.`,
          user.role === "SUPER_ADMIN" && row.collectionCode
            ? `Cash-out code: ${row.collectionCode}.`
            : null,
          receiptUrl ? `View your PDF receipt: ${receiptUrl}` : null,
        ]
          .filter(Boolean)
          .join("\n\n"),
    });
  }
  return (
    <>
      <div className="page-heading">
        <div className="breadcrumb">Workspace / {ws.name}</div>
        <h1>{ws.name}</h1>
        <p>{ws.description}</p>
      </div>
      {resource === "bill-payments" && catalog.data?.mockProvider && (
        <div className="operation-note">
          Mock provider · Development purchases do not reach a live biller.
        </div>
      )}
      <div className="page-actions">
        {resource === "customers" && canPerform(user, "CUSTOMERS_MANAGE") && (
          <>
            <button
              className="button primary"
              onClick={() => createCustomer(false)}
            >
              New individual
            </button>
            <button
              className="button secondary"
              onClick={() => createCustomer(true)}
            >
              New corporate
            </button>
          </>
        )}
        {resource === "wallets" && (
          <>
            {canPerform(user, "WALLET_MANAGE") && (
            <button
              className="button primary"
              onClick={createWallet}
              disabled={!catalog.data}
            >
              Create wallet
            </button>
            )}
            {canPerform(user, "WALLET_DEPOSIT") && (
              <button
                className="button secondary"
                onClick={() => cash("deposit")}
              >
                Deposit
              </button>
            )}
            {canPerform(user, "WALLET_WITHDRAW") && <button
              className="button secondary"
              onClick={() => cash("withdraw")}
            >
              Withdraw
            </button>}
            {canPerform(user, "WALLET_SEND") && <button
              className="button secondary"
              onClick={() => cash("send-money")}
            >
              Send money
            </button>}
          </>
        )}
        {resource === "users" && (user.role === "SUPER_ADMIN" || user.role === "CORPORATE_ADMIN") && (
          <button className="button primary" onClick={createUser}>
            Create user
          </button>
        )}
        {resource === "bill-payments" && (
          <button
            className="button primary"
            onClick={bill}
            disabled={!catalog.data}
          >
            Purchase bill
          </button>
        )}
        {resource === "remittances" && canPerform(user, "REMITTANCE_SEND") && (
          <button
            className="button primary"
            onClick={remit}
            disabled={!catalog.data}
          >
            Send remittance
          </button>
        )}
      </div>
      <section className="records-panel">
        <div className="records-toolbar banking-toolbar">
          <input
            aria-label="Search records"
            placeholder="Search name or reference…"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
          />
          <button
            className="mobile-filter-toggle"
            aria-expanded={filtersOpen}
            onClick={() => setFiltersOpen((open) => !open)}
          >
            <SlidersHorizontal size={17} />
            Filters{status || from || to ? " · Active" : ""}
          </button>
          <div className={`records-extra-filters ${filtersOpen ? "is-open" : ""}`}>
          {statuses[resource] && (
            <select
              aria-label="Filter status"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value);
                setPage(0);
              }}
            >
              <option value="">All statuses</option>
              {statuses[resource].map((s) => (
                <option key={s}>{s}</option>
              ))}
            </select>
          )}
          <label>
            From
            <input
              aria-label="From date"
              type="date"
              value={from}
              onChange={(e) => {
                setFrom(e.target.value);
                setPage(0);
              }}
            />
          </label>
          <label>
            To
            <input
              aria-label="To date"
              type="date"
              value={to}
              onChange={(e) => {
                setTo(e.target.value);
                setPage(0);
              }}
            />
          </label>
          <button className="button secondary" onClick={() => void q.refetch()}>
            Refresh
          </button>
          <button
            className="button secondary"
            disabled={!rows.length}
            onClick={() => csv(rows, resource + "-page-" + (page + 1))}
          >
            Export page
          </button>
          </div>
        </div>
        {q.isError ? (
          <div className="operation-error" role="alert">
            {q.error.message}{" "}
            <button onClick={() => void q.refetch()}>Retry</button>
          </div>
        ) : q.isPending ? (
          <p className="records-empty">Loading records…</p>
        ) : (
          <DataTable
            rows={rows}
            columns={columns[resource]}
            onSelect={(row) =>
              resource === "customers"
                ? navigate(`/customers/${row.customerNumber}`)
                : setSelected(row)
            }
          />
        )}
        <div className="records-pagination">
          <span>
            {q.data?.total ?? 0} records · Page {page + 1}
          </span>
          <button disabled={!page} onClick={() => setPage(page - 1)}>
            Previous
          </button>
          <button
            disabled={(page + 1) * 20 >= (q.data?.total ?? 0)}
            onClick={() => setPage(page + 1)}
          >
            Next
          </button>
        </div>
      </section>
      <Dialog
        open={!!selected}
        drawer
        title={ws.name + " details"}
        onClose={() => setSelected(null)}
      >
        {selected && (
          <>
            <Details
              row={Object.fromEntries(
                Object.entries({ ...selected, ...detail.data }).filter(
                  ([key]) => key !== "id",
                ),
              )}
            />
            {detail.isError && <p role="alert">{detail.error.message}</p>}
            {Array.isArray(detail.data?.entries) && (
              <>
                <h3>Ledger entries</h3>
                <DataTable
                  rows={detail.data.entries as Row[]}
                  columns={[
                    ["entryType", "Type"],
                    ["debitWalletId", "Debit wallet"],
                    ["creditWalletId", "Credit wallet"],
                    ["amount", "Amount"],
                  ]}
                />
              </>
            )}
            {resource === "remittances" &&
              Array.isArray(detail.data?.transactions) && (
                <>
                  <h3>Transactions</h3>
                  <DataTable
                    rows={detail.data.transactions as Row[]}
                    columns={[
                      ["transactionReference", "Reference"],
                      ["transactionType", "Type"],
                      ["currency", "Currency"],
                      ["faceValue", "Amount"],
                      ["feeAmount", "Fee"],
                      ["status", "Status"],
                      ["createdAt", "Date"],
                    ]}
                  />
                </>
              )}
            <div className="detail-actions">
              {resource === "wallets" && (
                <>
                  <button
                    className="button secondary"
                    onClick={() => {
                      setStatement(String(selected.walletNumber));
                      setSelected(null);
                    }}
                  >
                    Wallet statement
                  </button>
                  {canPerform(user, "WALLET_SEND") && <button
                    className="button secondary"
                    onClick={() => cash("send-money", selected)}
                  >
                    Send money
                  </button>}
                  {canPerform(user, "WALLET_MANAGE") && (
                    <>
                      <button
                        className="button secondary"
                        onClick={() =>
                          start({
                            title:
                              selected.status === "ACTIVE"
                                ? "Block wallet"
                                : "Activate wallet",
                            path:
                              "/api/wallets/" +
                              selected.walletNumber +
                              "/" +
                              (selected.status === "ACTIVE"
                                ? "block"
                                : "activate"),
                            fields: [],
                          })
                        }
                      >
                        {selected.status === "ACTIVE" ? "Block" : "Activate"}
                      </button>
                    </>
                  )}
                  {canPerform(user, "WALLET_ADJUST") && (
                    <button
                      className="button secondary"
                      onClick={() =>
                        start({
                          title: "Account adjustment",
                          path: "/api/admin/wallets/adjust",
                          financial: true,
                          fields: [
                            field("wallet", "Wallet", { disabled: true }),
                            money(),
                            choice("direction", "Direction", ["CREDIT", "DEBIT"]),
                            field("reason", "Reason", { maxLength: 500 }),
                          ],
                          initial: { wallet: selected.walletNumber },
                        })
                      }
                    >
                      Adjust balance
                    </button>
                  )}
                </>
              )}
              {resource === "transactions" && selected.status === "SUCCESS" && canPerform(user, "TRANSACTION_REVERSE") && (
                <button
                  className="button secondary"
                  onClick={() =>
                    start({
                      title: "Reverse transaction",
                      path:
                        "/api/transactions/" +
                        selected.transactionReference +
                        "/reverse",
                      financial: true,
                      fields: [field("reason", "Reason", { maxLength: 500 })],
                      note: "The backend checks reversibility and creates compensating ledger entries for the complete transaction.",
                    })
                  }
                >
                  Reverse transaction
                </button>
              )}
              {resource === "bill-payments" && (
                <button
                  className="button secondary"
                  onClick={() =>
                    start({
                      title: "Enquire with provider",
                      path:
                        "/api/bill-payments/" +
                        selected.transactionReference +
                        "/enquire",
                      fields: [],
                    })
                  }
                >
                  Provider enquiry
                </button>
              )}
              {resource === "users" &&
                selected.id !== user.id &&
                (user.role === "SUPER_ADMIN" || (user.role === "CORPORATE_ADMIN" && selected.role === "CORPORATE_USER")) && (
                  <button
                    className="button secondary"
                    disabled={user.role === "SUPER_ADMIN" && userAccess.isLoading}
                    onClick={() =>
                      start({
                        title: "Update user access",
                        path: "/api/auth/users/" + selected.id,
                        method: "PUT",
                        fields: [
                          choice(
                            "role",
                            "Role",
                            staff
                              ? [
                                  "SUPER_ADMIN",
                                  "OPERATIONS",
                                  "CUSTOMER",
                                  "CORPORATE_ADMIN",
                                  "CORPORATE_USER",
                                  "AGENT",
                                ]
                              : ["CORPORATE_USER"],
                          ),
                          choice("status", "Status", [
                            "ACTIVE",
                            "BLOCKED",
                            "DISABLED",
                          ]),
                          ...(user.role === "SUPER_ADMIN"
                            ? [checkboxes("permissions", "Permissions", "/api/auth/permissions", (option) => `${String(option.area)} · ${String(option.name)}`, "code")]
                            : []),
                        ],
                        initial: { ...selected, ...(userAccess.data ?? {}), permissions: userAccess.data?.permissions ?? [] },
                        note: selected.role === "OPERATIONS"
                          ? "Choose the pages and actions this user can access. Super administrators always have full access."
                          : "Changes invalidate current access tokens. You cannot change your own access here.",
                      })
                    }
                  >
                    {selected.role === "OPERATIONS" && user.role === "SUPER_ADMIN" ? "Role & permissions" : "Update access"}
                  </button>
                )}
              {resource === "users" &&
                selected.id !== user.id &&
                ["CUSTOMER", "AGENT", "CORPORATE_USER"].includes(
                  String(selected.role),
                ) &&
                (staff || selected.role === "CORPORATE_USER") && (
                  <button
                    className="button secondary"
                    onClick={() =>
                      start({
                        title: "Reset mobile PIN",
                        path: "/api/auth/users/" + selected.id + "/mobile-pin",
                        method: "PUT",
                        fields: [
                          field("mobilePin", "New mobile PIN", {
                            type: "password",
                            required: true,
                            minLength: 4,
                            maxLength: 4,
                            pattern: "[0-9]{4}",
                            inputMode: "numeric",
                            title: "Enter exactly four digits.",
                            help: "Required to approve customer or agent transactions.",
                          }),
                        ],
                        note: "Enter a new four-digit transaction PIN for this user. Current access tokens will be invalidated.",
                      })
                    }
                  >
                    Reset mobile PIN
                  </button>
                )}
            </div>
          </>
        )}
      </Dialog>
      {action && (
        <ActionDialog
          user={user}
          action={action}
          onClose={() => setAction(null)}
          onDone={() => {}}
        />
      )}
      {customerOnboarding !== null && (
        <CustomerOnboarding
          corporate={customerOnboarding}
          onClose={() => setCustomerOnboarding(null)}
          onDone={async () => {
            await queryClient.invalidateQueries({
              queryKey: ["records", "customers"],
            });
          }}
        />
      )}
      {statement && (
        <Statement wallet={statement} onClose={() => setStatement(null)} />
      )}
    </>
  );
}

function CustomerOnboarding({
  corporate,
  onClose,
  onDone,
}: {
  corporate: boolean;
  onClose: () => void;
  onDone: () => Promise<void>;
}) {
  const fields = corporate ? corporateFields : customerFields;
  const [values, setValues] = useState<Row>({});
  const [review, setReview] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState<{
    customer: Row;
    wallets: Row[];
  } | null>(null);
  const update = (name: string, value: string) =>
    setValues((current) => ({ ...current, [name]: value }));
  async function submit() {
    setBusy(true);
    setError("");
    try {
      const customer = await api<Row>(
        "/api/customers/" + (corporate ? "corporates" : "individuals"),
        { method: "POST", body: JSON.stringify(values) },
      );
      const walletPage = await api<{ items: Row[] }>(
        "/api/admin/workspace/wallets?search=" +
          encodeURIComponent(String(customer.customerNumber)) +
          "&page=0&size=100",
      );
      const wallets = walletPage.items;
      setResult({ customer, wallets });
      await onDone();
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "The customer could not be created.",
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <Dialog
      open
      title={
        result
          ? "Customer created"
          : corporate
            ? "Register corporate customer"
            : "Register individual customer"
      }
      onClose={onClose}
      busy={busy}
    >
      {result ? (
        <>
          <p className="operation-success">
            Customer {String(result.customer.customerNumber)} was created with
            Airtime, Wallet, and Bill Payment wallets in USD and ZWG.
          </p>
          <Details row={result.customer} />
          <h3>Wallets</h3>
          <DataTable
            rows={result.wallets}
            columns={[
              ["walletType", "Wallet type"],
              ["currency", "Currency"],
              ["walletNumber", "Wallet"],
              ["balance", "Opening balance"],
            ]}
          />
          <button className="button primary" onClick={onClose}>
            Done
          </button>
        </>
      ) : (
        <form
          onSubmit={(event) => {
            event.preventDefault();
            if (review) void submit();
            else setReview(true);
          }}
        >
          {error && (
            <p role="alert" className="operation-error">
              {error}
            </p>
          )}
          {review ? (
            <>
              <p>
                Confirm the customer details and wallet setup before creating.
              </p>
              <Details row={{ ...values, wallets: "Configured wallet types and active currencies" }} />
            </>
          ) : (
            <>
              <div className="operation-fields">
                {fields.map((fieldDefinition) => (
                  <label key={fieldDefinition.name}>
                    <span>
                      {fieldDefinition.label}
                      {!fieldDefinition.required && <small>Optional</small>}
                    </span>
                    <input
                      required={fieldDefinition.required}
                      type={fieldDefinition.type ?? "text"}
                      value={String(values[fieldDefinition.name] ?? "")}
                      onChange={(event) =>
                        update(fieldDefinition.name, event.target.value)
                      }
                    />
                  </label>
                ))}
              </div>
              <p className="operation-note">
                Six wallets will be created automatically: Airtime, Wallet, and Bill Payment in USD and ZWG.
              </p>
            </>
          )}
          <div className="operation-footer">
            {review && (
              <button
                type="button"
                className="button secondary"
                onClick={() => setReview(false)}
              >
                Edit details
              </button>
            )}
            <button
              type="button"
              className="button secondary"
              disabled={busy}
              onClick={onClose}
            >
              Close
            </button>
            <button className="button primary" disabled={busy}>
              {busy
                ? "Creating…"
                : review
                  ? "Confirm and create"
                  : "Review details"}
            </button>
          </div>
        </form>
      )}
    </Dialog>
  );
}

function Statement({
  wallet,
  onClose,
}: {
  wallet: string;
  onClose: () => void;
}) {
  const [page, setPage] = useState(0);
  const q = useQuery({
    queryKey: ["statement", wallet, page],
    queryFn: () =>
      api<Row[]>(
        `/api/wallets/${encodeURIComponent(wallet)}/statement?offset=${page * 50}&limit=50`,
      ),
  });
  return (
    <Dialog open drawer title="Wallet statement" onClose={onClose}>
      <p className="operation-note">
        {wallet} · Entries in ledger order. Running balances include earlier
        pages.
      </p>
      {q.isError ? (
        <p role="alert">{q.error.message}</p>
      ) : q.isPending ? (
        <p>Loading…</p>
      ) : (
        <DataTable
          rows={q.data ?? []}
          columns={[
            ["date", "Date"],
            ["transactionReference", "Reference"],
            ["debit", "Debit"],
            ["credit", "Credit"],
            ["runningBalance", "Balance"],
          ]}
        />
      )}
      <div className="records-pagination">
        <button disabled={!page} onClick={() => setPage(page - 1)}>
          Previous
        </button>
        <span>Page {page + 1}</span>
        <button
          disabled={(q.data?.length ?? 0) < 50}
          onClick={() => setPage(page + 1)}
        >
          Next
        </button>
        <button
          disabled={!q.data?.length}
          onClick={() =>
            csv(q.data ?? [], "statement-" + wallet + "-" + (page + 1))
          }
        >
          Export page
        </button>
      </div>
    </Dialog>
  );
}
