import { type FormEvent, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  BriefcaseBusiness,
  Check,
  CircleOff,
  Edit2,
  FolderKanban,
  Plus,
  Search,
  Store,
  Trash2,
  type LucideIcon,
  UserRound,
  X,
} from "lucide-react";
import { Dialog } from "../components/Dialog";
import { api } from "../lib/session";

type Kind = "categories" | "vendors" | "employees";
type Setting = {
  id: string;
  kind: Kind;
  name: string;
  employeeNumber?: string | null;
  contactName?: string | null;
  mobileNumber?: string | null;
  email?: string | null;
  status: "ACTIVE" | "INACTIVE";
};

type Tab = {
  id: Kind;
  name: string;
  singular: string;
  icon: LucideIcon;
};

const tabs: Tab[] = [
  {
    id: "categories",
    name: "Categories",
    singular: "category",
    icon: FolderKanban,
  },
  {
    id: "vendors",
    name: "Vendors",
    singular: "vendor",
    icon: Store,
  },
  {
    id: "employees",
    name: "Employees",
    singular: "employee",
    icon: UserRound,
  },
];

function blank(value: FormDataEntryValue | null) {
  const text = String(value ?? "").trim();
  return text ? text : null;
}

function initials(name: string) {
  return name
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();
}

export function ExpenseSettings() {
  const client = useQueryClient();
  const [kind, setKind] = useState<Kind>("categories");
  const [editing, setEditing] = useState<Setting | null>(null);
  const [deleting, setDeleting] = useState<Setting | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [search, setSearch] = useState("");
  const [error, setError] = useState("");
  const q = useQuery({
    queryKey: ["expense-settings", kind],
    queryFn: () => api<Setting[]>(`/api/admin/expense-settings/${kind}`),
  });
  const currentTab = tabs.find((tab) => tab.id === kind)!;
  const rows = useMemo(() => {
    const needle = search.trim().toLowerCase();
    return (q.data ?? []).filter((row) =>
      [
        row.name,
        row.employeeNumber,
        row.contactName,
        row.mobileNumber,
        row.email,
      ]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(needle)),
    );
  }, [q.data, search]);
  const activeCount = (q.data ?? []).filter(
    (row) => row.status === "ACTIVE",
  ).length;

  const closeEditor = () => {
    setEditing(null);
    setEditorOpen(false);
    setError("");
  };
  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      api<Setting>(
        `/api/admin/expense-settings/${kind}${editing ? `/${editing.id}` : ""}`,
        {
          method: editing ? "PUT" : "POST",
          body: JSON.stringify(body),
        },
      ),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ["expense-settings"] });
      closeEditor();
    },
    onError: (cause) =>
      setError(
        cause instanceof Error ? cause.message : "Setting could not be saved.",
      ),
  });
  const remove = useMutation({
    mutationFn: (row: Setting) =>
      api<void>(`/api/admin/expense-settings/${kind}/${row.id}`, {
        method: "DELETE",
      }),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ["expense-settings"] });
      setDeleting(null);
    },
    onError: (cause) =>
      setError(
        cause instanceof Error
          ? cause.message
          : "Setting could not be deleted.",
      ),
  });
  const status = useMutation({
    mutationFn: (row: Setting) =>
      api<Setting>(
        `/api/admin/expense-settings/${kind}/${row.id}/${row.status === "ACTIVE" ? "deactivate" : "activate"}`,
        {
          method: "POST",
          body: JSON.stringify({}),
        },
      ),
    onSuccess: async () =>
      client.invalidateQueries({ queryKey: ["expense-settings"] }),
    onError: (cause) =>
      setError(
        cause instanceof Error ? cause.message : "Status could not be changed.",
      ),
  });

  function selectKind(next: Kind) {
    setKind(next);
    setSearch("");
    closeEditor();
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    const form = new FormData(event.currentTarget);
    save.mutate({
      name: String(form.get("name") ?? "").trim(),
      employeeNumber: blank(form.get("employeeNumber")),
      contactName: blank(form.get("contactName")),
      mobileNumber: blank(form.get("mobileNumber")),
      email: blank(form.get("email")),
      status: String(form.get("status") ?? "ACTIVE"),
    });
  }

  return (
    <div className="expense-settings-page">
      <div className="page-heading">
        <div className="breadcrumb">Administration / Expense settings</div>
        <h1>Expense settings</h1>
        <p>Categories, vendors and employees.</p>
      </div>

      <section
        className={`expense-settings-workspace ${editorOpen ? "editor-is-open" : ""}`}
      >
        <nav
          className="expense-settings-nav"
          aria-label="Expense setting types"
        >
          <div className="expense-settings-nav-title">
            <BriefcaseBusiness size={17} />
            <span>Expense setup</span>
          </div>
          <div className="expense-settings-tabs">
            {tabs.map((tab) => {
              const Icon = tab.icon;
              return (
                <button
                  key={tab.id}
                  className={kind === tab.id ? "active" : ""}
                  onClick={() => selectKind(tab.id)}
                  aria-current={kind === tab.id ? "page" : undefined}
                >
                  <Icon size={17} />
                  <span>
                    <strong>{tab.name}</strong>
                  </span>
                </button>
              );
            })}
          </div>
        </nav>

        <div className="expense-settings-content">
          <header className="expense-settings-header">
            <div>
              <h2>{currentTab.name}</h2>
              <p>
                {activeCount} active · {(q.data ?? []).length} total
              </p>
            </div>
            <button
              className="button primary"
              onClick={() => {
                setEditing(null);
                setEditorOpen(true);
                setError("");
              }}
            >
              <Plus size={16} /> Add {currentTab.singular}
            </button>
          </header>

          <div className="expense-settings-toolbar">
            <label>
              <Search size={16} />
              <input
                aria-label={`Search ${currentTab.name.toLowerCase()}`}
                placeholder={`Search ${currentTab.name.toLowerCase()}…`}
                value={search}
                onChange={(event) => setSearch(event.target.value)}
              />
            </label>
          </div>

          {error && !editorOpen && (
            <div className="alert expense-settings-alert" role="alert">
              {error}
            </div>
          )}
          {q.isError ? (
            <div className="expense-settings-empty" role="alert">
              <CircleOff size={24} />
              <h3>Could not load {currentTab.name.toLowerCase()}</h3>
              <button className="text-button" onClick={() => void q.refetch()}>
                Try again
              </button>
            </div>
          ) : q.isLoading ? (
            <p className="expense-settings-loading">
              Loading {currentTab.name.toLowerCase()}…
            </p>
          ) : rows.length === 0 ? (
            <div className="expense-settings-empty">
              <currentTab.icon size={26} />
              <h3>
                {search
                  ? "No matching records"
                  : `No ${currentTab.name.toLowerCase()} yet`}
              </h3>
              <p>
                {search
                  ? "Try a different search."
                  : `Add a ${currentTab.singular} to make it available on expenses.`}
              </p>
            </div>
          ) : (
            <div className="expense-settings-list">
              {rows.map((row) => (
                <article key={row.id} className="expense-setting-row">
                  <div className="expense-setting-avatar" aria-hidden="true">
                    {initials(row.name)}
                  </div>
                  <div className="expense-setting-summary">
                    <div className="expense-setting-name">
                      <strong>{row.name}</strong>
                      <span
                        className={`record-status status-${row.status.toLowerCase()}`}
                      >
                        {row.status}
                      </span>
                    </div>
                    <p>
                      {kind === "categories" && "Expense category"}
                      {kind === "vendors" &&
                        ([row.contactName, row.mobileNumber, row.email]
                          .filter(Boolean)
                          .join(" · ") ||
                          "No contact details")}
                      {kind === "employees" &&
                        ([row.employeeNumber, row.mobileNumber, row.email]
                          .filter(Boolean)
                          .join(" · ") ||
                          "No employee details")}
                    </p>
                  </div>
                  <div className="expense-setting-row-actions">
                    <button
                      className="icon-button"
                      title={`Edit ${row.name}`}
                      aria-label={`Edit ${row.name}`}
                      onClick={() => {
                        setEditing(row);
                        setEditorOpen(true);
                        setError("");
                      }}
                    >
                      <Edit2 size={16} />
                    </button>
                    <button
                      className="icon-button"
                      title={`${row.status === "ACTIVE" ? "Deactivate" : "Activate"} ${row.name}`}
                      aria-label={`${row.status === "ACTIVE" ? "Deactivate" : "Activate"} ${row.name}`}
                      disabled={status.isPending}
                      onClick={() => status.mutate(row)}
                    >
                      {row.status === "ACTIVE" ? (
                        <CircleOff size={16} />
                      ) : (
                        <Check size={16} />
                      )}
                    </button>
                    <button
                      className="icon-button danger"
                      title={`Delete ${row.name}`}
                      aria-label={`Delete ${row.name}`}
                      disabled={remove.isPending}
                      onClick={() => {
                        setDeleting(row);
                        setError("");
                      }}
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </article>
              ))}
            </div>
          )}
        </div>

        {editorOpen && (
          <aside className="expense-settings-editor">
            <div className="expense-settings-editor-heading">
              <div>
                <span>{editing ? "Edit record" : "New record"}</span>
                <h2>{editing ? editing.name : `Add ${currentTab.singular}`}</h2>
              </div>
              <button
                className="icon-button"
                type="button"
                onClick={closeEditor}
                title="Close editor"
                aria-label="Close editor"
              >
                <X size={18} />
              </button>
            </div>
            <form key={`${kind}-${editing?.id ?? "new"}`} onSubmit={submit}>
              <div className="expense-settings-fields">
                <label>
                  <span>Name *</span>
                  <input
                    name="name"
                    required
                    autoFocus
                    maxLength={
                      kind === "categories"
                        ? 100
                        : kind === "employees"
                          ? 150
                          : 200
                    }
                    defaultValue={editing?.name ?? ""}
                  />
                </label>
                {kind === "employees" && (
                  <label>
                    <span>Employee number</span>
                    <input
                      name="employeeNumber"
                      maxLength={50}
                      defaultValue={editing?.employeeNumber ?? ""}
                    />
                  </label>
                )}
                {kind === "vendors" && (
                  <label>
                    <span>Contact person</span>
                    <input
                      name="contactName"
                      maxLength={150}
                      defaultValue={editing?.contactName ?? ""}
                    />
                  </label>
                )}
                {kind !== "categories" && (
                  <label>
                    <span>Mobile</span>
                    <input
                      name="mobileNumber"
                      type="tel"
                      maxLength={40}
                      defaultValue={editing?.mobileNumber ?? ""}
                    />
                  </label>
                )}
                {kind !== "categories" && (
                  <label>
                    <span>Email</span>
                    <input
                      name="email"
                      type="email"
                      maxLength={254}
                      defaultValue={editing?.email ?? ""}
                    />
                  </label>
                )}
                <label>
                  <span>Status *</span>
                  <select
                    name="status"
                    required
                    defaultValue={editing?.status ?? "ACTIVE"}
                  >
                    <option value="ACTIVE">Active</option>
                    <option value="INACTIVE">Inactive</option>
                  </select>
                </label>
              </div>
              {error && (
                <div className="alert" role="alert">
                  {error}
                </div>
              )}
              <div className="expense-settings-editor-actions">
                <button
                  className="button secondary"
                  type="button"
                  onClick={closeEditor}
                >
                  Cancel
                </button>
                <button className="button primary" disabled={save.isPending}>
                  <Check size={16} />{" "}
                  {save.isPending
                    ? "Saving…"
                    : editing
                      ? "Save changes"
                      : `Add ${currentTab.singular}`}
                </button>
              </div>
            </form>
          </aside>
        )}
      </section>
      <Dialog
        open={!!deleting}
        title={`Delete ${currentTab.singular}`}
        busy={remove.isPending}
        onClose={() => {
          setDeleting(null);
          setError("");
        }}
      >
        <p className="expense-settings-delete-copy">
          Delete <strong>{deleting?.name}</strong>? This record will no longer
          be available when creating expenses. This cannot be undone.
        </p>
        {error && (
          <div className="alert" role="alert">
            {error}
          </div>
        )}
        <div className="dialog-actions">
          <button
            className="button secondary"
            disabled={remove.isPending}
            onClick={() => setDeleting(null)}
          >
            Cancel
          </button>
          <button
            className="button danger"
            disabled={remove.isPending || !deleting}
            onClick={() => deleting && remove.mutate(deleting)}
          >
            <Trash2 size={16} /> {remove.isPending ? "Deleting…" : "Delete"}
          </button>
        </div>
      </Dialog>
    </div>
  );
}
