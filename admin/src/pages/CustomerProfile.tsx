import {
  ArrowLeft,
  CircleAlert,
  KeyRound,
  LoaderCircle,
  PackageCheck,
  Plus,
  ShieldCheck,
  WalletCards,
} from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, type User } from "../lib/session";
import {
  choice,
  checkboxes,
  field,
  lookup,
  money,
  type Row,
  type Page,
  type Action,
} from "../lib/operations";
import { ActionDialog, DataTable, Details } from "../components/Operations";
import { Dialog } from "../components/Dialog";
import { canPerform } from "../lib/workspaces";

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

export function CustomerProfile({
  customerNumber,
  user,
}: {
  customerNumber: string;
  user: User;
}) {
  const navigate = useNavigate();
  const client = useQueryClient();
  const [action, setAction] = useState<Action | null>(null);
  const [selectedAllocation, setSelectedAllocation] = useState<Row | null>(
    null,
  );
  const canManageCustomer = canPerform(user, "CUSTOMERS_MANAGE");
  const customer = useQuery({
    queryKey: ["customer-profile", customerNumber],
    queryFn: () =>
      api<Row>(`/api/customers/${encodeURIComponent(customerNumber)}`),
  });
  const wallets = useQuery({
    queryKey: ["customer-profile-wallets", customerNumber],
    enabled: canPerform(user, "WALLETS_VIEW"),
    queryFn: () =>
      api<Page>(
        "/api/admin/workspace/wallets?search=" +
          encodeURIComponent(customerNumber) +
          "&page=0&size=100",
      ),
  });
  const transactions = useQuery({
    queryKey: ["customer-profile-transactions", customerNumber],
    enabled: canPerform(user, "TRANSACTIONS_VIEW"),
    queryFn: () =>
      api<Row[] | null>(
        `/api/customers/${encodeURIComponent(customerNumber)}/transactions?offset=0&limit=100`,
      ),
  });
  const credentials = useQuery({
    queryKey: ["customer-api-credentials", customerNumber],
    enabled: canManageCustomer,
    queryFn: () =>
      api<Row>(
        `/api/customers/${encodeURIComponent(customerNumber)}/api-credentials`,
      ),
  });
  const allocations = useQuery({
    queryKey: ["customer-product-allocations", customerNumber],
    enabled: canPerform(user, "CONFIGURATION_VIEW"),
    queryFn: () =>
      api<Row[]>(
        `/api/admin/customer-product-allocations/customer/${encodeURIComponent(customerNumber)}`,
      ),
  });
  const data = customer.data;
  const walletRows = wallets.data?.items ?? [];
  const activeWalletRows = walletRows.filter(
    (wallet) => wallet.status === "ACTIVE",
  );
  const walletOptions = activeWalletRows.map((wallet) =>
    String(wallet.walletNumber),
  );
  const walletLabels = Object.fromEntries(
    activeWalletRows.map((wallet) => [
      String(wallet.walletNumber),
      `${String(wallet.walletType ?? "Wallet")} · ${String(wallet.currency)}`,
    ]),
  );
  const firstWallet = walletOptions[0];
  const name = String(
    data?.companyName ||
      [data?.firstName, data?.lastName].filter(Boolean).join(" ") ||
      customerNumber,
  );
  const openEdit = () => {
    if (!data) return;
    setAction({
      title: "Edit customer",
      path: "/api/customers/" + customerNumber,
      method: "PUT",
      fields:
        data.customerType === "CORPORATE"
          ? corporateFields
          : customerFields.filter((f) => f.name !== "nationalId"),
      initial: data,
    });
  };
  const openStatus = () => {
    if (!data) return;
    setAction({
      title: data.status === "ACTIVE" ? "Block customer" : "Activate customer",
      path: `/api/customers/${customerNumber}/${data.status === "ACTIVE" ? "block" : "activate"}`,
      fields: [],
    });
  };
  const openAgent = () =>
    setAction({
      title: "Make agent",
      path: `/api/customers/${customerNumber}/make-agent`,
      fields: [
        choice("agentType", "Agent type", [
          "STANDARD",
          "SUPER_AGENT",
          "DISTRIBUTOR",
        ]),
      ],
    });
  const openCash = (kind: "deposit" | "withdraw") => {
    if (!walletOptions.length) return;
    setAction({
      title: kind === "deposit" ? "Deposit funds" : "Withdraw funds",
      path: `/api/wallets/${kind}`,
      financial: true,
      fields: [
        choice(
          "walletNumber",
          "Wallet currency",
          walletOptions,
          true,
          walletLabels,
        ),
        money(),
      ],
      initial: { walletNumber: firstWallet },
      note: "The transaction will be posted to the selected customer wallet after confirmation.",
    });
  };
  const openCredentials = () =>
    setAction({
      title: credentials.data?.configured
        ? "Update API credentials"
        : "Create API credentials",
      path: `/api/customers/${encodeURIComponent(customerNumber)}/api-credentials`,
      method: "PUT",
      fields: [
        field("username", "API username", { maxLength: 100 }),
        field("password", "New password", {
          type: "password",
          required: !credentials.data?.configured,
          minLength: 8,
          maxLength: 72,
          help: credentials.data?.configured
            ? "Leave blank to keep the current password. Changing it signs out existing API sessions."
            : "Use at least 8 characters.",
        }),
        field("mobilePin", "Four-digit mobile PIN", {
          type: "password",
          required: !credentials.data?.mobilePinConfigured,
          minLength: 4,
          maxLength: 4,
          pattern: "[0-9]{4}",
          inputMode: "numeric",
          title: "Enter exactly four digits.",
          help: credentials.data?.mobilePinConfigured
            ? "Leave blank to keep the current transaction PIN."
            : "Required to approve external sales and reversals.",
        }),
        choice("status", "API access status", ["ACTIVE", "BLOCKED"]),
      ],
      initial: {
        username: credentials.data?.username ?? customerNumber,
        status: credentials.data?.status ?? "ACTIVE",
      },
      note: "The password is never displayed after it is saved. Share it with the customer securely.",
    });
  const allocationFields = () => [
    {
      ...lookup(
        "commissionPlanId",
        "Predefined product plan",
        "/api/admin/product-commission-plans",
        "id",
        true,
        (row) =>
          `${String(row.productName)} · ${String(row.currency)} · ${String(row.arrangementName)} (${String(row.agentCommissionPercentage)}% agent / ${String(row.platformCommissionPercentage)}% platform)`,
      ),
    },
    choice("status", "Status", ["ACTIVE", "INACTIVE"]),
  ];
  const openAllocation = (row?: Row) => {
    setSelectedAllocation(null);
    setAction({
      title: row ? "Edit product allocation" : "Allocate product",
      path: row
        ? `/api/admin/customer-product-allocations/customer/${encodeURIComponent(customerNumber)}/${String(row.id)}`
        : `/api/admin/customer-product-allocations/customer/${encodeURIComponent(customerNumber)}/bulk`,
      method: row ? "PUT" : "POST",
      fields: row
        ? allocationFields()
        : [
            checkboxes(
              "commissionPlanIds",
              "Products",
              "/api/admin/product-commission-plans",
              (plan) =>
                `${String(plan.productName)} · ${String(plan.currency)} · ${String(plan.arrangementName)} (${String(plan.agentCommissionPercentage)}% agent / ${String(plan.platformCommissionPercentage)}% platform)`,
            ),
            choice("status", "Status", ["ACTIVE", "INACTIVE"]),
          ],
      initial: row ?? { commissionPlanIds: [], status: "ACTIVE" },
      note: "Commission values come from the predefined plan in Settings and cannot be entered manually here.",
    });
  };
  if (customer.isPending)
    return (
      <ProfileFrame onBack={() => navigate("/customers")}>
        <div className="profile-loading">
          <LoaderCircle className="spin" size={26} />
          <p>Loading customer profile…</p>
        </div>
      </ProfileFrame>
    );
  if (customer.isError || !data)
    return (
      <ProfileFrame onBack={() => navigate("/customers")}>
        <div className="profile-loading">
          <CircleAlert size={26} />
          <h2>Customer profile unavailable</h2>
          <p>{customer.error?.message ?? "Customer not found."}</p>
        </div>
      </ProfileFrame>
    );
  return (
    <ProfileFrame onBack={() => navigate("/customers")}>
      <div className="customer-profile-heading">
        <div>
          <div className="breadcrumb">
            <span>Workspace</span>
            <span>/</span>
            <span>Customers</span>
            <span>/</span>
            <strong>{customerNumber}</strong>
          </div>
          <div className="profile-title-row">
            <div className="profile-avatar">
              <span>{name.slice(0, 1).toUpperCase()}</span>
            </div>
            <div>
              <p className="eyebrow aqua">CUSTOMER PROFILE</p>
              <h1>{name}</h1>
              <p className="profile-subtitle">
                {customerNumber} ·{" "}
                {String(data.customerType).replaceAll("_", " ")}
              </p>
            </div>
          </div>
        </div>
        <span
          className={`record-status status-${String(data.status).toLowerCase()}`}
        >
          {String(data.status).replaceAll("_", " ")}
        </span>
      </div>
      {(canPerform(user, "WALLET_DEPOSIT") || canPerform(user, "WALLET_WITHDRAW")) && <section className="profile-operations-card">
        <div className="profile-card-heading">
          <div>
            <p className="eyebrow">WALLET OPERATIONS</p>
            <h2>Move money for this customer</h2>
            <p className="profile-muted">
              Select a wallet and review the transaction before it is posted.
            </p>
          </div>
          <WalletCards size={20} />
        </div>
        <div className="profile-operation-grid">
          {canPerform(user, "WALLET_DEPOSIT") && <button
            className="profile-operation-button"
            disabled={!walletOptions.length}
            onClick={() => openCash("deposit")}
          >
            <strong>Deposit</strong>
            <span>Add funds to a customer wallet</span>
          </button>}
          {canPerform(user, "WALLET_WITHDRAW") && <button
            className="profile-operation-button"
            disabled={!walletOptions.length}
            onClick={() => openCash("withdraw")}
          >
            <strong>Withdraw</strong>
            <span>Remove funds from a customer wallet</span>
          </button>}
        </div>
      </section>}
      <div className="customer-profile-grid">
        <section className="profile-card profile-identity-card">
          <div className="profile-card-heading">
            <div>
              <p className="eyebrow">IDENTITY & CONTACT</p>
              <h2>Customer details</h2>
            </div>
            <ShieldCheck size={20} />
          </div>
          <Details row={data} />
        </section>
        {canPerform(user, "WALLETS_VIEW") && <section className="profile-card profile-wallet-card">
          <div className="profile-card-heading">
            <div>
              <p className="eyebrow">ACCOUNT OVERVIEW</p>
              <h2>Wallet balances</h2>
            </div>
            <WalletCards size={20} />
          </div>
          {wallets.isPending ? (
            <p className="profile-muted">Loading balances…</p>
          ) : wallets.isError ? (
            <p className="operation-error">{wallets.error.message}</p>
          ) : walletRows.length ? (
            <div className="balance-cards">
              {walletRows.map((wallet) => (
                <div className="balance-card" key={String(wallet.id)}>
                  <div>
                    <span>{String(wallet.walletType ?? "Wallet")} · {String(wallet.currency)} balance</span>
                    <strong>{String(wallet.balance ?? "0.00")}</strong>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <p className="profile-muted">No wallets found for this customer.</p>
          )}
        </section>}
      </div>
      <div className="profile-management-grid">
        {canManageCustomer && <section className="profile-card profile-api-card">
          <div className="profile-card-heading">
            <div>
              <p className="eyebrow">API ACCESS</p>
              <h2>Customer credentials</h2>
              <p className="profile-muted">
                Credentials used by this customer to obtain an external API token.
              </p>
            </div>
            <KeyRound size={20} />
          </div>
          {credentials.isPending ? (
            <p className="profile-muted">Loading API credentials…</p>
          ) : credentials.isError ? (
            <p className="operation-error">{credentials.error.message}</p>
          ) : (
            <div className="api-credential-summary">
              <div>
                <span>Username</span>
                <strong>{String(credentials.data?.username)}</strong>
              </div>
              <span
                className={`record-status status-${String(credentials.data?.status).toLowerCase()}`}
              >
                {credentials.data?.configured
                  ? String(credentials.data.status)
                  : "NOT CONFIGURED"}
              </span>
            </div>
          )}
          {!credentials.isPending && !credentials.isError && (
            <p className="credential-pin-state">
              Mobile PIN: {credentials.data?.mobilePinConfigured ? "Configured" : "Not configured"}
            </p>
          )}
          <button
            className="button secondary"
            disabled={credentials.isPending || credentials.isError}
            onClick={openCredentials}
          >
            <KeyRound size={15} /> Update credentials
          </button>
        </section>}
        {canPerform(user, "CONFIGURATION_VIEW") && <section className="profile-card profile-products-card">
          <div className="profile-card-heading">
            <div>
              <p className="eyebrow">PRODUCT ACCESS</p>
              <h2>Products this customer can sell</h2>
              <p className="profile-muted">
                Assign commission plans that were predefined in Settings.
              </p>
            </div>
            <PackageCheck size={20} />
          </div>
          <div className="profile-inline-actions">
            <span className="profile-count">
              {allocations.data?.length ?? 0} allocated
            </span>
            {canPerform(user, "CONFIGURATION_MANAGE") && <button className="button primary" onClick={() => openAllocation()}>
              <Plus size={15} /> Allocate predefined plan
            </button>}
          </div>
          {allocations.isPending ? (
            <p className="profile-muted">Loading allocated products…</p>
          ) : allocations.isError ? (
            <p className="operation-error">{allocations.error.message}</p>
          ) : (
            <DataTable
              rows={allocations.data ?? []}
              columns={[
                ["productName", "Product"],
                ["currency", "Currency"],
                ["arrangementName", "Arrangement"],
                ["totalCommissionPercentage", "Total %"],
                ["agentCommissionPercentage", "Agent %"],
                ["platformCommissionPercentage", "Platform %"],
                ["status", "Status"],
              ]}
              onSelect={setSelectedAllocation}
            />
          )}
        </section>}
      </div>
      {canPerform(user, "TRANSACTIONS_VIEW") && <section className="profile-card profile-transactions-card">
        <div className="profile-card-heading">
          <div>
            <p className="eyebrow">ACTIVITY</p>
            <h2>Recent transactions</h2>
            <p className="profile-muted">
              Financial activity recorded against this customer’s wallets.
            </p>
          </div>
          <span className="profile-count">
            {transactions.data?.length ?? 0} records
          </span>
        </div>
        {transactions.isPending ? (
          <p className="profile-muted">Loading transactions…</p>
        ) : transactions.isError ? (
          <p className="operation-error">{transactions.error.message}</p>
        ) : (
          <DataTable
            rows={transactions.data ?? []}
            columns={[
              ["transactionType", "Type"],
              ["currency", "Currency"],
              ["faceValue", "Amount"],
              ["status", "Status"],
              ["createdAt", "Date"],
            ]}
          />
        )}
      </section>}
      {canManageCustomer && <section className="profile-actions-card">
        <div>
          <p className="eyebrow">CUSTOMER ACTIONS</p>
          <h2>Manage this customer</h2>
          <p>Update customer information or control account access.</p>
        </div>
        <div className="detail-actions">
          <button className="button secondary" onClick={openEdit}>
            Edit customer
          </button>
          <button className="button secondary" onClick={openStatus}>
            {data.status === "ACTIVE" ? "Block customer" : "Activate customer"}
          </button>
          {!data.isAgent && (
            <button className="button primary" onClick={openAgent}>
              Make agent
            </button>
          )}
        </div>
      </section>}
      {action && (
        <ActionDialog
          action={action}
          user={user}
          onClose={() => setAction(null)}
          onDone={async () => {
            await client.invalidateQueries({
              queryKey: ["customer-profile", customerNumber],
            });
            await client.invalidateQueries({
              queryKey: ["customer-profile-wallets", customerNumber],
            });
            await client.invalidateQueries({
              queryKey: ["customer-api-credentials", customerNumber],
            });
            await client.invalidateQueries({
              queryKey: ["customer-product-allocations", customerNumber],
            });
          }}
        />
      )}
      <Dialog
        open={!!selectedAllocation}
        drawer
        title="Product allocation"
        onClose={() => setSelectedAllocation(null)}
      >
        {selectedAllocation && (
          <>
            <Details
              row={Object.fromEntries(
                Object.entries(selectedAllocation).filter(([key]) =>
                  [
                    "productCode",
                    "productName",
                    "biller",
                    "currency",
                    "arrangementName",
                    "totalCommissionPercentage",
                    "agentCommissionPercentage",
                    "platformCommissionPercentage",
                    "status",
                  ].includes(key),
                ),
              )}
            />
            <div className="operation-footer">
              <button
                className="button danger"
                onClick={() => {
                  setAction({
                    title: "Delink product",
                    path: `/api/admin/customer-product-allocations/customer/${encodeURIComponent(customerNumber)}/${String(selectedAllocation.id)}`,
                    method: "DELETE",
                    fields: [],
                    note: "This removes the product from this customer only. The predefined plan and transaction history will not be deleted.",
                  });
                  setSelectedAllocation(null);
                }}
              >
                Delink product
              </button>
              <button
                className="button secondary"
                onClick={() => openAllocation(selectedAllocation)}
              >
                Edit allocation
              </button>
              <button
                className="button primary"
                onClick={() => {
                  setAction({
                    title:
                      selectedAllocation.status === "ACTIVE"
                        ? "Deactivate product allocation"
                        : "Activate product allocation",
                    path: `/api/admin/customer-product-allocations/${String(selectedAllocation.id)}/${selectedAllocation.status === "ACTIVE" ? "deactivate" : "activate"}`,
                    fields: [],
                  });
                  setSelectedAllocation(null);
                }}
              >
                {selectedAllocation.status === "ACTIVE"
                  ? "Deactivate"
                  : "Activate"}
              </button>
            </div>
          </>
        )}
      </Dialog>
    </ProfileFrame>
  );
}

function ProfileFrame({
  children,
  onBack,
}: {
  children: React.ReactNode;
  onBack: () => void;
}) {
  return (
    <div className="customer-profile-page">
      <div className="profile-page-toolbar">
        <button className="button secondary" onClick={onBack}>
          <ArrowLeft size={16} /> Back to customers
        </button>
        <span>Customer workspace</span>
      </div>
      {children}
    </div>
  );
}
