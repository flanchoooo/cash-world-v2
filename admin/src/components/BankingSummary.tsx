import { useQuery } from "@tanstack/react-query";
import { api } from "../lib/session";
import type { Row } from "../lib/operations";
import { DataTable } from "./Operations";
export function BankingSummary() {
  const q = useQuery({
    queryKey: ["dashboard"],
    queryFn: () =>
      api<{
        customers: number;
        wallets: number;
        transactions: number;
        pendingRemittances: number;
        balances: Row[];
      }>("/api/admin/workspace/dashboard"),
    refetchInterval: 60000,
  });
  if (q.isPending)
    return (
      <section className="records-panel records-empty">
        Loading operational totals…
      </section>
    );
  if (q.isError)
    return (
      <p role="alert" className="operation-error">
        Operational totals unavailable.{" "}
        <button onClick={() => void q.refetch()}>Retry</button>
      </p>
    );
  return (
    <section className="records-panel dashboard-panel">
      <div className="banking-metrics">
        {[
          ["Customers", q.data.customers],
          ["Wallets", q.data.wallets],
          ["Transaction requests", q.data.transactions],
          ["Awaiting payout", q.data.pendingRemittances],
        ].map(([label, value]) => (
          <section key={label}>
            <span>{label}</span>
            <strong>{value}</strong>
          </section>
        ))}
      </div>
      <div className="records-section-heading">
        <h2>Customer balances by currency</h2>
        <p>
          Current customer wallet balances. System and settlement accounts are
          excluded.
        </p>
      </div>
      <DataTable
        rows={q.data.balances}
        columns={[
          ["currency", "Currency"],
          ["walletCount", "Customer wallets"],
          ["balance", "Balance"],
        ]}
      />
    </section>
  );
}
