import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Search } from "lucide-react";
import { DataTable } from "../components/Operations";
import { api } from "../lib/session";
import type { Row } from "../lib/operations";

type Expense = Row & { id: string };
type Page = { items: Expense[]; total: number; page: number; size: number };
type Catalog = { currencies: { code: string; name: string }[] };

function today() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

export function Expenses() {
  const client = useQueryClient();
  const [search, setSearch] = useState("");
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState("");
  const list = useQuery({
    queryKey: ["expenses", search],
    queryFn: () => api<Page>(`/api/admin/expenses?${new URLSearchParams({ search })}`),
  });
  const catalog = useQuery({
    queryKey: ["catalog"],
    queryFn: () => api<Catalog>("/api/admin/workspace/catalog"),
  });
  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      api<Expense>("/api/admin/expenses", { method: "POST", body: JSON.stringify(body) }),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ["expenses"] });
      setShowForm(false);
    },
    onError: (cause) => setError(cause instanceof Error ? cause.message : "Expense could not be recorded."),
  });
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    const form = new FormData(event.currentTarget);
    const body = Object.fromEntries(form.entries());
    body.amount = String(body.amount);
    save.mutate(body);
  }

  return (
    <div>
      <div className="page-heading">
        <div className="breadcrumb">Administration / Expenses</div>
        <h1>Expenses</h1>
        <p>Record and review business expenses.</p>
      </div>
      <div className="page-actions">
        <button className="button primary" onClick={() => { setError(""); setShowForm((value) => !value); }}>
          <Plus size={16} /> Record expense
        </button>
      </div>
      {showForm && (
        <form className="expense-form" onSubmit={submit}>
          <h2>New expense</h2>
          <div className="operation-fields expense-fields">
            <label><span>Expense name *</span><input name="expenseName" required maxLength={200} /></label>
            <label><span>Amount *</span><input name="amount" type="number" min="0.0001" step="0.0001" required /></label>
            <label><span>Currency *</span><select name="currency" required defaultValue=""><option value="" disabled>Select currency</option>{(catalog.data?.currencies ?? []).map((currency) => <option key={currency.code} value={currency.code}>{currency.code} · {currency.name}</option>)}</select></label>
            <label><span>Requested by *</span><input name="requestedBy" required maxLength={150} /></label>
            <label><span>Expense date *</span><input name="expenseDate" type="date" required defaultValue={today()} /></label>
            <label><span>Category</span><input name="category" maxLength={100} placeholder="Travel, supplies, utilities…" /></label>
            <label><span>Vendor / payee</span><input name="vendor" maxLength={200} /></label>
            <label><span>Reference</span><input name="reference" maxLength={150} placeholder="Invoice or receipt number" /></label>
            <label className="expense-wide"><span>Business purpose</span><textarea name="purpose" maxLength={1000} rows={3} /></label>
            <label className="expense-wide"><span>Receipt reference or link</span><input name="receiptReference" maxLength={500} placeholder="Optional link or document reference" /></label>
          </div>
          {error && <div className="alert" role="alert">{error}</div>}
          <div className="page-actions"><button className="button primary" disabled={save.isPending || !catalog.data?.currencies.length}>{save.isPending ? "Saving…" : "Save expense"}</button><button className="button secondary" type="button" onClick={() => setShowForm(false)}>Cancel</button></div>
        </form>
      )}
      <div className="records-toolbar">
        <label className="filter-select"><Search size={16} /><input aria-label="Search expenses" placeholder="Search expenses" value={search} onChange={(event) => setSearch(event.target.value)} /></label>
        <span>{list.data?.total ?? 0} expenses</span>
      </div>
      {list.isError ? <div className="alert" role="alert">Expenses could not be loaded.</div> : list.isLoading ? <p>Loading expenses…</p> : (
        <DataTable rows={list.data?.items ?? []} columns={[["expenseDate", "Date"], ["expenseName", "Expense"], ["category", "Category"], ["amount", "Amount"], ["currency", "Currency"], ["requestedBy", "Requested by"], ["vendor", "Vendor"], ["status", "Status"]]} />
      )}
    </div>
  );
}
