import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Search } from "lucide-react";
import { DataTable, Details } from "../components/Operations";
import { Dialog } from "../components/Dialog";
import { api, apiBlob } from "../lib/session";
import type { Row } from "../lib/operations";

type Expense = Row & { id: string };
type Page = { items: Expense[]; total: number; page: number; size: number };
type Catalog = { currencies: { code: string; name: string }[] };
type Setting = { id: string; name: string; status: "ACTIVE" | "INACTIVE" };
type Upload = { fileName: string; contentType: string; data: string };
type Attachment = { id: string; fileName: string; contentType: string; fileSize: number; createdAt: string };

function today() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

async function upload(file: File): Promise<Upload> {
  if (file.size > 5 * 1024 * 1024) throw new Error("Choose receipt files smaller than 5 MB.");
  const value = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(new Error("The receipt file could not be read."));
    reader.readAsDataURL(file);
  });
  return {
    fileName: file.name,
    contentType: file.type || "application/octet-stream",
    data: value.slice(value.indexOf(",") + 1),
  };
}

export function Expenses() {
  const client = useQueryClient();
  const [search, setSearch] = useState("");
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState<Expense | null>(null);
  const list = useQuery({
    queryKey: ["expenses", search],
    queryFn: () => api<Page>(`/api/admin/expenses?${new URLSearchParams({ search })}`),
  });
  const catalog = useQuery({
    queryKey: ["catalog"],
    queryFn: () => api<Catalog>("/api/admin/workspace/catalog"),
  });
  const categories = useQuery({
    queryKey: ["expense-settings", "categories", "active"],
    queryFn: () => api<Setting[]>("/api/admin/expense-settings/categories?active=true"),
  });
  const employees = useQuery({
    queryKey: ["expense-settings", "employees", "active"],
    queryFn: () => api<Setting[]>("/api/admin/expense-settings/employees?active=true"),
  });
  const vendors = useQuery({
    queryKey: ["expense-settings", "vendors", "active"],
    queryFn: () => api<Setting[]>("/api/admin/expense-settings/vendors?active=true"),
  });
  const attachments = useQuery({
    queryKey: ["expense-attachments", selected?.id],
    queryFn: () => api<Attachment[]>(`/api/admin/expenses/${selected!.id}/attachments`),
    enabled: !!selected,
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
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    const current = event.currentTarget;
    const form = new FormData(current);
    const body: Record<string, unknown> = Object.fromEntries(form.entries());
    body.amount = String(body.amount);
    delete body.attachments;
    for (const key of ["categoryId", "requestedByEmployeeId", "vendorId"]) {
      if (!body[key]) delete body[key];
    }
    const files = Array.from(current.querySelector<HTMLInputElement>('input[name="attachments"]')?.files ?? []);
    try {
      body.attachments = await Promise.all(files.map(upload));
      save.mutate(body);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Receipt files could not be prepared.");
    }
  }
  async function download(attachment: Attachment) {
    if (!selected) return;
    const blob = await apiBlob(`/api/admin/expenses/${selected.id}/attachments/${attachment.id}`);
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = attachment.fileName;
    link.click();
    URL.revokeObjectURL(url);
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
            <label><span>Requested by *</span><select name="requestedByEmployeeId" required defaultValue=""><option value="" disabled>Select employee</option>{(employees.data ?? []).map((employee) => <option key={employee.id} value={employee.id}>{employee.name}</option>)}</select></label>
            <label><span>Expense date *</span><input name="expenseDate" type="date" required defaultValue={today()} /></label>
            <label><span>Category *</span><select name="categoryId" required defaultValue=""><option value="" disabled>Select category</option>{(categories.data ?? []).map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label>
            <label><span>Vendor / payee</span><select name="vendorId" defaultValue=""><option value="">No vendor</option>{(vendors.data ?? []).map((vendor) => <option key={vendor.id} value={vendor.id}>{vendor.name}</option>)}</select></label>
            <label><span>Reference</span><input name="reference" maxLength={150} placeholder="Invoice or receipt number" /></label>
            <label className="expense-wide"><span>Business purpose</span><textarea name="purpose" maxLength={1000} rows={3} /></label>
            <label className="expense-wide"><span>Receipt reference or link</span><input name="receiptReference" maxLength={500} placeholder="Optional link or document reference" /></label>
            <label className="expense-wide"><span>Receipt files</span><input name="attachments" type="file" multiple accept="application/pdf,image/jpeg,image/png,image/webp" /></label>
          </div>
          {error && <div className="alert" role="alert">{error}</div>}
          <div className="page-actions"><button className="button primary" disabled={save.isPending || !catalog.data?.currencies.length || !categories.data?.length || !employees.data?.length}>{save.isPending ? "Saving…" : "Save expense"}</button><button className="button secondary" type="button" onClick={() => setShowForm(false)}>Cancel</button></div>
        </form>
      )}
      <div className="records-toolbar">
        <label className="filter-select"><Search size={16} /><input aria-label="Search expenses" placeholder="Search expenses" value={search} onChange={(event) => setSearch(event.target.value)} /></label>
        <span>{list.data?.total ?? 0} expenses</span>
      </div>
      {list.isError ? <div className="alert" role="alert">Expenses could not be loaded.</div> : list.isLoading ? <p>Loading expenses…</p> : (
        <DataTable rows={list.data?.items ?? []} columns={[["expenseDate", "Date"], ["expenseName", "Expense"], ["category", "Category"], ["amount", "Amount"], ["currency", "Currency"], ["requestedBy", "Requested by"], ["vendor", "Vendor"], ["attachmentCount", "Files"], ["status", "Status"]]} onSelect={(row) => setSelected(row as Expense)} />
      )}
      <Dialog open={!!selected} drawer title="Expense details" onClose={() => setSelected(null)}>
        {selected && (
          <>
            <Details row={selected} />
            {selected.attachmentCount ? (
              <div className="expense-attachments">
                <h3>Receipt files</h3>
                {attachments.isLoading ? <p>Loading files…</p> : (attachments.data ?? []).map((attachment) => (
                  <button className="text-button" key={attachment.id} onClick={() => void download(attachment)}>
                    {attachment.fileName}
                  </button>
                ))}
              </div>
            ) : null}
          </>
        )}
      </Dialog>
    </div>
  );
}
