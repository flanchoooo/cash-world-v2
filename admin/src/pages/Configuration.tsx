import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api, type User } from "../lib/session";
import { configurations } from "../lib/configuration";
import { type Row, type Action, csv } from "../lib/operations";
import { ActionDialog, DataTable, Details } from "../components/Operations";
import { Dialog } from "../components/Dialog";
export function Configuration({ user }: { user: User }) {
  const [tab, setTab] = useState("currencies");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<Row | null>(null);
  const [action, setAction] = useState<Action | null>(null);
  const config = configurations.find((c) => c.id === tab)!;
  const path = "/api/admin/" + tab;
  const canWrite = !config.adminOnly || user.role === "SUPER_ADMIN";
  const q = useQuery({
    queryKey: ["configuration", tab],
    queryFn: () => api<Row[]>(path),
  });
  const currencies = useQuery({
    queryKey: ["configuration", "currencies"],
    queryFn: () => api<Row[]>("/api/admin/currencies"),
  });
  const types = useQuery({
    queryKey: ["configuration", "transaction-types"],
    queryFn: () => api<Row[]>("/api/admin/transaction-types"),
    enabled: tab === "fees",
  });
  const products = useQuery({
    queryKey: ["configuration", "biller-products"],
    queryFn: () => api<Row[]>("/api/admin/biller-products"),
    enabled: tab === "fees" || tab === "product-commission-plans",
  });
  const rows = (q.data ?? [])
    .map((r) => ({
      ...r,
      ...(r.currencyId
        ? {
            currency:
              currencies.data?.find((c) => c.id === r.currencyId)?.code ??
              r.currencyId,
          }
        : {}),
      ...(r.transactionTypeId
        ? {
            transactionType:
              types.data?.find((t) => t.id === r.transactionTypeId)?.code ??
              r.transactionTypeId,
          }
        : {}),
      ...(r.billerProductId
        ? {
            product:
              products.data?.find((p) => p.id === r.billerProductId)?.code ??
              r.billerProductId,
          }
        : {}),
    }))
    .filter((r) =>
      Object.values(r).some((v) =>
        String(v ?? "")
          .toLowerCase()
          .includes(search.toLowerCase()),
      ),
    );
  const visible = rows.slice(page * 20, page * 20 + 20);
  function edit(row?: Row) {
    const initial = { ...config.initial, ...row };
    for (const f of config.fields) {
      if (f.type === "datetime-local" && initial[f.name]) {
        const d = new Date(String(initial[f.name]));
        initial[f.name] = new Date(d.getTime() - d.getTimezoneOffset() * 60000)
          .toISOString()
          .slice(0, 16);
      }
    }
    setSelected(null);
    setAction({
      title: row ? "Edit " + config.name : "Create " + config.name,
      path: path + (row ? "/" + row.id : ""),
      method: row ? "PUT" : "POST",
      fields: config.fields,
      initial,
    });
  }
  return (
    <>
      <div className="page-heading">
        <div className="breadcrumb">Administration / Configuration</div>
        <h1>Configuration</h1>
        <p>
          Manage the currencies, pricing and settlement rules behind each
          operation.
        </p>
      </div>
      <div className="config-tabs">
        {configurations.map((c) => (
          <button
            key={c.id}
            className={tab === c.id ? "active" : ""}
            onClick={() => {
              setTab(c.id);
              setPage(0);
              setSearch("");
            }}
          >
            {c.name}
          </button>
        ))}
      </div>
      <section className="records-panel">
        <div className="records-toolbar">
          <input
            aria-label="Search configuration"
            placeholder="Search configuration…"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
          />
          <button className="button secondary" onClick={() => csv(rows, tab)}>
            Export filtered
          </button>
          {canWrite && (
            <button className="button primary" onClick={() => edit()}>
              Create {config.name.toLowerCase()}
            </button>
          )}
        </div>
        {q.isError ? (
          <p role="alert" className="operation-error">
            {q.error.message}{" "}
            <button onClick={() => void q.refetch()}>Retry</button>
          </p>
        ) : q.isPending ? (
          <p className="records-empty">Loading configuration…</p>
        ) : (
          <DataTable
            rows={visible}
            columns={config.columns}
            onSelect={setSelected}
          />
        )}
        <div className="records-pagination">
          <span>{rows.length} records</span>
          <button disabled={!page} onClick={() => setPage(page - 1)}>
            Previous
          </button>
          <span>{page + 1}</span>
          <button
            disabled={(page + 1) * 20 >= rows.length}
            onClick={() => setPage(page + 1)}
          >
            Next
          </button>
        </div>
      </section>
      <Dialog
        open={!!selected}
        drawer
        title={config.name + " details"}
        onClose={() => setSelected(null)}
      >
        {selected && (
          <>
            <Details row={selected} />
            {canWrite && (
              <div className="operation-footer">
                <button
                  className="button secondary"
                  onClick={() => edit(selected)}
                >
                  Edit
                </button>
                <button
                  className="button primary"
                  onClick={() => {
                    setAction({
                      title:
                        selected.status === "ACTIVE"
                          ? "Deactivate record"
                          : "Activate record",
                      path:
                        path +
                        "/" +
                        selected.id +
                        "/" +
                        (selected.status === "ACTIVE"
                          ? "deactivate"
                          : "activate"),
                      fields: [],
                      note: "This changes availability for future operations.",
                    });
                    setSelected(null);
                  }}
                >
                  {selected.status === "ACTIVE" ? "Deactivate" : "Activate"}
                </button>
              </div>
            )}
          </>
        )}
      </Dialog>
      {action && (
        <ActionDialog
          action={action}
          user={user}
          onClose={() => setAction(null)}
          onDone={() => {}}
        />
      )}
    </>
  );
}
