import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { BadgeDollarSign, CheckCircle2, Download } from "lucide-react";
import { DataTable, Details } from "../components/Operations";
import { Dialog } from "../components/Dialog";
import { api } from "../lib/session";
import { csv, type Row } from "../lib/operations";
import type { User } from "../lib/session";
import { canPerform } from "../lib/workspaces";

export function CreditSales({ user }: { user: User }) {
  const client = useQueryClient();
  const [selected, setSelected] = useState<Row | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const query = useQuery({
    queryKey: ["credit-sales"],
    queryFn: () => api<Row[]>("/api/admin/credit-sales"),
  });
  const rows = query.data ?? [];
  async function markCollected() {
    if (!selected) return;
    setBusy(true);
    setError("");
    try {
      await api(`/api/admin/credit-sales/${selected.id}/mark-collected`, {
        method: "POST",
        body: JSON.stringify({}),
      });
      setSelected(null);
      await client.invalidateQueries({ queryKey: ["credit-sales"] });
    } catch (e) {
      setError(
        e instanceof Error
          ? e.message
          : "The credit sale could not be updated.",
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="credit-sales-page">
      <div className="page-heading">
        <div className="breadcrumb">Payments / Credit sales</div>
        <h1>Credit sales</h1>
        <p>
          See who owes each seller, what was purchased, and the amount
          outstanding.
        </p>
      </div>
      <div className="banking-metrics">
        <section>
          <BadgeDollarSign />
          <span>Outstanding orders</span>
          <strong>
            {rows.filter((r) => r.creditStatus === "OUTSTANDING").length}
          </strong>
          <small>Awaiting collection</small>
        </section>
        <section>
          <CheckCircle2 />
          <span>Collected orders</span>
          <strong>
            {rows.filter((r) => r.creditStatus === "COLLECTED").length}
          </strong>
          <small>Marked as received</small>
        </section>
      </div>
      <section className="records-panel">
        <div className="records-toolbar">
          <div />
          <button
            className="button secondary"
            disabled={!rows.length}
            onClick={() => csv(rows, "credit-sales")}
          >
            <Download size={16} /> Export CSV
          </button>
        </div>
        {query.isError ? (
          <p className="operation-error">{query.error.message}</p>
        ) : (
          <DataTable
            rows={rows}
            onSelect={setSelected}
            columns={[
              ["seller", "Seller"],
              ["product", "Product"],
              ["currency", "Currency"],
              ["amountDue", "Amount due"],
              ["collectingAgentName", "Collecting agent"],
              ["collectingAgentMobile", "Mobile"],
              ["creditStatus", "Status"],
              ["createdAt", "Date"],
            ]}
          />
        )}
      </section>
      <Dialog
        open={!!selected}
        drawer
        title="Credit sale details"
        onClose={() => setSelected(null)}
        busy={busy}
      >
        {selected && (
          <>
            <Details row={selected} />
            {error && <p className="operation-error">{error}</p>}
            {selected.creditStatus === "OUTSTANDING" && canPerform(user, "CREDIT_SALES_MANAGE") && (
              <div className="operation-footer">
                <button
                  className="button primary"
                  onClick={() => void markCollected()}
                >
                  Mark collected
                </button>
              </div>
            )}
          </>
        )}
      </Dialog>
    </div>
  );
}
