import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "../lib/session";
import { csv, type Row, type Page } from "../lib/operations";
import { DataTable, Details, useDebounced } from "../components/Operations";
import { Dialog } from "../components/Dialog";
export function Commissions() {
  const [search, setSearch] = useState("");
  const term = useDebounced(search);
  const [agent, setAgent] = useState("");
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<Row | null>(null);
  const agents = useQuery({
    queryKey: ["agents", term],
    queryFn: () =>
      api<Page>(
        "/api/admin/workspace/customers?agents=true&size=30&search=" +
          encodeURIComponent(term),
      ),
  });
  const catalog = useQuery({
    queryKey: ["commission-currencies"],
    queryFn: () => api<Row[]>("/api/admin/currencies"),
  });
  const products = useQuery({
    queryKey: ["commission-products"],
    queryFn: () => api<Row[]>("/api/admin/biller-products"),
  });
  const report = useQuery({
    queryKey: ["commissions", agent, page],
    enabled: !!agent,
    queryFn: () =>
      api<Row[]>(
        `/api/agents/${agent}/commissions?offset=${page * 20}&limit=20`,
      ),
  });
  const summary = useQuery({
    queryKey: ["commission-summary", agent],
    enabled: !!agent,
    queryFn: () => api<Row[]>(`/api/agents/${agent}/commissions/summary`),
  });
  const currency = (id: unknown) =>
    catalog.data?.find((r) => r.id === id)?.code ?? id;
  const rows = (report.data ?? []).map((r) => ({
    ...r,
    currency: currency(r.currencyId),
    product: products.data?.find((p) => p.id === r.product)?.code ?? r.product,
  }));
  return (
    <>
      <div className="page-heading">
        <div className="breadcrumb">Payments / Agent commissions</div>
        <h1>Agent commissions</h1>
        <p>Review earned commissions and reversals, separated by currency.</p>
      </div>
      <section className="records-panel">
        <div className="records-toolbar">
          <input
            aria-label="Search agents"
            placeholder="Search agents…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <select
            aria-label="Select agent"
            value={agent}
            onChange={(e) => {
              setAgent(e.target.value);
              setPage(0);
            }}
          >
            <option value="">Select an agent</option>
            {agent &&
              !agents.data?.items.some((r) => r.customerNumber === agent) && (
                <option>{agent}</option>
              )}
            {agents.data?.items.map((r) => (
              <option key={String(r.id)} value={String(r.customerNumber)}>
                {String(r.customerNumber)} ·{" "}
                {String(r.companyName ?? [r.firstName, r.lastName].join(" "))}
              </option>
            ))}
          </select>
          <button
            className="button secondary"
            disabled={!rows.length}
            onClick={() => csv(rows, "commissions-page-" + (page + 1))}
          >
            Export page
          </button>
        </div>
        {agents.isError && (
          <p className="operation-error" role="alert">
            {agents.error.message}
          </p>
        )}
        {!agent ? (
          <p className="records-empty">Select an agent to view earnings.</p>
        ) : (
          <>
            {summary.isError && <p role="alert">{summary.error.message}</p>}
            <div className="banking-metrics">
              {summary.data?.map((r) => (
                <section key={String(r.currencyId)}>
                  <span>
                    {String(currency(r.currencyId))} · Earned commission
                  </span>
                  <strong>{String(r.totalCommission)}</strong>
                  <small>
                    {String(r.totalBillPayments)} successful bill payments ·
                    Face value {String(r.totalFaceValue)}
                  </small>
                </section>
              ))}
            </div>
            {report.isError ? (
              <p role="alert" className="operation-error">
                {report.error.message}
              </p>
            ) : report.isPending ? (
              <p className="records-empty">Loading commissions…</p>
            ) : (
              <DataTable
                rows={rows}
                columns={[
                  ["date", "Date"],
                  ["transactionReference", "Reference"],
                  ["product", "Product"],
                  ["currency", "Currency"],
                  ["faceValue", "Face value"],
                  ["commissionAmount", "Commission"],
                  ["rewardMode", "Reward"],
                  ["status", "Status"],
                ]}
                onSelect={setSelected}
              />
            )}
            <div className="records-pagination">
              <span>Page {page + 1}</span>
              <button disabled={!page} onClick={() => setPage(page - 1)}>
                Previous
              </button>
              <button
                disabled={rows.length < 20}
                onClick={() => setPage(page + 1)}
              >
                Next
              </button>
            </div>
          </>
        )}
      </section>
      <Dialog
        open={!!selected}
        drawer
        title="Commission details"
        onClose={() => setSelected(null)}
      >
        {selected && <Details row={selected} />}
      </Dialog>
    </>
  );
}
