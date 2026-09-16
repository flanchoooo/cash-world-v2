import { useEffect, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, type User } from "../lib/session";
import {
  display,
  execute,
  type Row,
  type Field,
  type Action,
} from "../lib/operations";
import { Dialog } from "./Dialog";
import {
  Search,
  CheckCircle2,
  AlertCircle,
  MessageCircle,
  FileText,
  Copy,
} from "lucide-react";

export function Details({ row }: { row: Row }) {
  return (
    <dl className="record-details">
      {Object.entries(row)
        .filter(([k]) => k !== "entries" && k !== "transactions")
        .map(([k, v]) => (
          <div key={k}>
            <dt>{k.replace(/([A-Z])/g, " $1")}</dt>
            <dd>
              {typeof v === "object" && v !== null ? (
                <pre>{JSON.stringify(v, null, 2)}</pre>
              ) : (
                display(v)
              )}
            </dd>
          </div>
        ))}
    </dl>
  );
}

type UploadValue = {
  fileName: string;
  contentType: string;
  data: string;
};

async function uploadValue(file: File, maxBytes: number): Promise<UploadValue> {
  if (file.size > maxBytes)
    throw new Error(
      `Choose a file smaller than ${Math.floor(maxBytes / 1024 / 1024)} MB.`,
    );
  const value = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () =>
      reject(new Error("The selected file could not be read."));
    reader.readAsDataURL(file);
  });
  return {
    fileName: file.name,
    contentType: file.type,
    data: value.slice(value.indexOf(",") + 1),
  };
}
export function DataTable({
  rows,
  columns,
  onSelect,
}: {
  rows: Row[];
  columns: [string, string][];
  onSelect?: (row: Row) => void;
}) {
  return (
    <div className="data-scroll">
      <table className="records-table">
        <thead>
          <tr>
            {columns.map(([k, l]) => (
              <th key={k}>{l}</th>
            ))}
            {onSelect && (
              <th>
                <span className="sr-only">Details</span>
              </th>
            )}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={String(r.id ?? r.transactionReference ?? i)}>
              {columns.map(([k]) => (
                <td key={k}>
                  {k === "status" ? (
                    <span
                      className={`record-status status-${display(r[k]).toLowerCase()}`}
                    >
                      {display(r[k]).replaceAll("_", " ")}
                    </span>
                  ) : (
                    display(r[k])
                  )}
                </td>
              ))}
              {onSelect && (
                <td>
                  <button className="text-button" onClick={() => onSelect(r)}>
                    View
                    <span className="sr-only">
                      {" "}
                      {display(r[columns[0][0]])}
                    </span>
                  </button>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
      {rows.length === 0 && (
        <div className="records-empty">
          <Search size={25} />
          <h3>No records found</h3>
          <p>Try another search or create your first record.</p>
        </div>
      )}
    </div>
  );
}
function Lookup({
  field,
  value,
  onChange,
}: {
  field: Field;
  value: string;
  onChange: (v: string) => void;
}) {
  const [search, setSearch] = useState("");
  const q = useQuery({
    queryKey: ["lookup", field.source, search],
    queryFn: () =>
      api<{ items: Row[] } | Row[]>(
        field.source!.includes("workspace/")
          ? `${field.source}?size=30&search=${encodeURIComponent(search)}`
          : field.source!,
      ),
  });
  const rows = Array.isArray(q.data) ? q.data : (q.data?.items ?? []);
  return (
    <div className="lookup">
      <input
        aria-label={`Search ${field.label}`}
        placeholder={`Search ${field.label.toLowerCase()}…`}
        value={search}
        onChange={(e) => setSearch(e.target.value)}
      />
      <select
        required={field.required}
        aria-label={field.label}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      >
        <option value="">
          {q.isPending ? "Loading…" : "Select an option"}
        </option>
        {value &&
          !rows.some((r) => String(r[field.valueKey ?? "id"]) === value) && (
            <option value={value}>{value}</option>
          )}
        {rows.map((r) => (
          <option
            key={String(r[field.valueKey ?? "id"])}
            value={String(r[field.valueKey ?? "id"])}
          >
            {field.optionLabel
              ? field.optionLabel(r)
              : [
                  r.customerNumber ?? r.walletNumber ?? r.code ?? r.username,
                  r.name ??
                    r.companyName ??
                    [r.firstName, r.lastName].filter(Boolean).join(" "),
                  r.currency,
                ]
                  .filter(Boolean)
                  .join(" · ")}
          </option>
        ))}
      </select>
      {q.isError && (
        <small role="alert">
          Lookup unavailable.{" "}
          <button type="button" onClick={() => void q.refetch()}>
            Retry
          </button>
        </small>
      )}
    </div>
  );
}
export function ActionDialog({
  action,
  user,
  onClose,
  onDone,
}: {
  action: Action;
  user: User;
  onClose: () => void;
  onDone: (row: Row) => void;
}) {
  const client = useQueryClient();
  const [values, setValues] = useState<Row>(action.initial ?? {});
  const [review, setReview] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState<Row | null>(null);
  const [quote, setQuote] = useState<Row | null>(null);
  const [shareStatus, setShareStatus] = useState("");
  const [receiptUrl, setReceiptUrl] = useState("");
  const [receiptPath, setReceiptPath] = useState("");
  const key = useRef<string>(crypto.randomUUID());
  const locked = useRef(false);
  const autoFillCache = useRef(new Set<string>());
  const [submitted, setSubmitted] = useState(false);
  const body = Object.fromEntries(
    action.fields.map((f) => {
      const v = values[f.name];
      return [
        f.name,
        v === "" || v === undefined || v === null
          ? null
          : f.type === "number"
            ? Number(v)
            : f.type === "datetime-local"
              ? new Date(String(v)).toISOString()
              : f.type === "select" && v === "true"
                ? true
                : f.type === "select" && v === "false"
                  ? false
                  : v,
      ];
    }),
  );
  async function submit() {
    if (locked.current) return;
    locked.current = true;
    setBusy(true);
    setError("");
    setSubmitted(true);
    let storageKey = "";
    try {
      if (action.financial) {
        const digest = await crypto.subtle.digest(
          "SHA-256",
          new TextEncoder().encode(JSON.stringify([action.path, body])),
        );
        storageKey = `poscloud-operation:${user.id}:${Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, "0")).join("")}`;
        const saved = sessionStorage.getItem(storageKey);
        if (saved) key.current = saved;
        else sessionStorage.setItem(storageKey, key.current);
      }
      const response = await execute(action, body, key.current);
      setResult(response);
      if (storageKey) sessionStorage.removeItem(storageKey);
      await client.invalidateQueries();
      onDone(response);
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "The request could not be completed.",
      );
    } finally {
      setBusy(false);
      locked.current = false;
    }
  }
  async function prepareReview() {
    setError("");
    if (!action.quotePath) {
      setReview(true);
      return;
    }
    setBusy(true);
    try {
      setQuote(
        await api<Row>(action.quotePath, {
          method: "POST",
          body: JSON.stringify(body),
        }),
      );
      setReview(true);
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "The quote could not be calculated.",
      );
    } finally {
      setBusy(false);
    }
  }
  useEffect(() => {
    if (!result || !action.receiptSharePath) return;
    let current = true;
    void api<{ receiptPath: string; receiptUrl: string }>(
      action.receiptSharePath(result),
      {
        method: "POST",
        body: JSON.stringify({}),
      },
    )
      .then((receipt) => {
        if (current) {
          setReceiptPath(receipt.receiptPath);
          setReceiptUrl(receipt.receiptUrl);
        }
      })
      .catch(() => {
        if (current)
          setShareStatus("The PDF receipt link could not be prepared.");
      });
    return () => {
      current = false;
    };
  }, [action, result]);

  function whatsappNumber(value: string) {
    const digits = value.replace(/\D/g, "");
    return digits.startsWith("0") ? `263${digits.slice(1)}` : digits;
  }

  function shareWhatsApp() {
    if (!result || !action.shareText || !receiptUrl) {
      setShareStatus("The PDF receipt link is still being prepared.");
      return;
    }
    const text = action.shareText(result, receiptUrl);
    const recipient = whatsappNumber(action.shareRecipient?.(result) ?? "");
    window.open(
      `https://wa.me/${recipient}?text=${encodeURIComponent(text)}`,
      "_blank",
      "noopener,noreferrer",
    );
    setShareStatus(
      "WhatsApp opened with the recipient and receipt link ready.",
    );
  }

  async function shareBotim() {
    if (!result || !action.shareText || !receiptUrl) {
      setShareStatus("The PDF receipt link is still being prepared.");
      return;
    }
    const text = action.shareText(result, receiptUrl);
    try {
      if (navigator.share) {
        await navigator.share({ title: "Remittance receipt", text });
        setShareStatus("Receipt shared. Select the recipient in BOTIM.");
      } else {
        await navigator.clipboard.writeText(text);
        window.open("https://botim.me/botim/", "_blank", "noopener,noreferrer");
        setShareStatus(
          "Message copied. Paste it into the recipient’s BOTIM chat.",
        );
      }
    } catch (e) {
      if (e instanceof DOMException && e.name === "AbortError") return;
      setShareStatus("Sharing is unavailable on this device.");
    }
  }

  async function copyReceiptLink() {
    if (!receiptUrl) return;
    await navigator.clipboard.writeText(receiptUrl);
    setShareStatus("PDF receipt link copied.");
  }
  async function applyAutoFill(field: string, value: string) {
    const rule = action.autoFill?.find((item) => item.field === field);
    const trimmed = value.trim();
    if (!rule || !trimmed) return;
    const cacheKey = `${field}:${trimmed}`;
    if (autoFillCache.current.has(cacheKey)) return;
    autoFillCache.current.add(cacheKey);
    try {
      const saved = await api<Row>(rule.path(trimmed));
      const fieldNames = new Set(action.fields.map((item) => item.name));
      setValues((current) => {
        const next = { ...current };
        Object.entries(saved).forEach(([name, savedValue]) => {
          if (
            fieldNames.has(name) &&
            savedValue !== null &&
            savedValue !== "" &&
            (next[name] === null ||
              next[name] === undefined ||
              next[name] === "")
          )
            next[name] = savedValue;
        });
        return next;
      });
    } catch {
      autoFillCache.current.delete(cacheKey);
      // No saved match is a normal outcome; the user can continue entering details.
    }
  }
  return (
    <Dialog
      open
      title={
        result
          ? result.status === "PENDING"
            ? "Awaiting provider result"
            : result.status === "FAILED"
              ? "Request failed"
              : "Operation completed"
          : action.title
      }
      onClose={onClose}
      fullPage={action.fullPage}
      busy={busy}
    >
      {result ? (
        <div className="receipt-page">
          <div className="operation-success receipt-success">
            {result.status === "FAILED" || result.status === "PENDING" ? (
              <AlertCircle />
            ) : (
              <CheckCircle2 />
            )}
            <p>
              {result.status === "PENDING"
                ? "Use provider enquiry to resolve this pending request. Do not submit a new purchase."
                : result.status === "FAILED"
                  ? "The backend reported a failed operation. Review the result before trying again."
                  : "The operation has been completed successfully."}
            </p>
          </div>
          {user.role === "SUPER_ADMIN" && Boolean(result.collectionCode) && (
            <div className="collection-code-card">
              <span>Customer collection code</span>
              <strong>{String(result.collectionCode)}</strong>
              <small>Visible to super administrators only.</small>
            </div>
          )}
          <section className="receipt-details-card">
            <div className="receipt-card-heading">
              <div>
                <span>Transaction receipt</span>
                <h3>Payment details</h3>
              </div>
              {receiptPath && (
                <a href={receiptPath} target="_blank" rel="noreferrer">
                  <FileText size={17} /> Open receipt
                </a>
              )}
            </div>
            <Details
              row={Object.fromEntries(
                Object.entries(result).filter(
                  ([key]) => key !== "collectionCode",
                ),
              )}
            />
          </section>
          {action.shareText && (
            <section className="receipt-share-card">
              <div>
                <span>Send receipt to sender</span>
                <p>
                  Send the sender a general transaction message with a secure
                  PDF receipt link.
                </p>
              </div>
              <div className="receipt-share-actions">
                <button
                  className="button whatsapp-button"
                  type="button"
                  disabled={!receiptUrl}
                  onClick={shareWhatsApp}
                >
                  <MessageCircle size={17} /> WhatsApp
                </button>
                <button
                  className="button secondary"
                  type="button"
                  disabled={!receiptUrl}
                  onClick={() => void shareBotim()}
                >
                  <MessageCircle size={17} /> BOTIM
                </button>
                <button
                  className="icon-button"
                  type="button"
                  aria-label="Copy PDF receipt link"
                  disabled={!receiptUrl}
                  onClick={() => void copyReceiptLink()}
                >
                  <Copy size={17} />
                </button>
              </div>
            </section>
          )}
          <div className="operation-footer receipt-footer">
            <button className="button primary" onClick={onClose}>
              Done
            </button>
          </div>
          {shareStatus && (
            <p className="operation-share-status">{shareStatus}</p>
          )}
        </div>
      ) : (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (review) void submit();
            else void prepareReview();
          }}
        >
          {action.note && <p className="operation-note">{action.note}</p>}
          {error && (
            <p role="alert" className="operation-error">
              {error}
            </p>
          )}
          {review ? (
            <div className="confirmation-page">
              <div className="confirmation-heading">
                <span>Final confirmation</span>
                <h3>Review the transaction</h3>
                <p>
                  Check the parties, currencies and totals before creating the
                  remittance.
                </p>
              </div>
              <section className="confirmation-card">
                <Details
                  row={{
                    ...Object.fromEntries(
                      action.fields
                        .filter((f) => f.type !== "password")
                        .map((f) => [
                          f.label,
                          f.type === "file"
                            ? (body[f.name] as UploadValue | null)?.fileName
                            : body[f.name],
                        ]),
                    ),
                  }}
                />
              </section>
              {quote && (
                <section className="fee-calculation-card">
                  <div>
                    <span>Money to send</span>
                    <strong>
                      {String(
                        quote.senderCurrency ?? body.sourceCurrency ?? "",
                      )}{" "}
                      {display(quote.sendAmount ?? body.amount)}
                    </strong>
                  </div>
                  <b aria-hidden="true">+</b>
                  <div>
                    <span>
                      {quote.feeOverridden ? "Override fee" : "Calculated fee"}
                    </span>
                    <strong>
                      {String(
                        quote.senderCurrency ?? body.sourceCurrency ?? "",
                      )}{" "}
                      {display(quote.feeAmount)}
                    </strong>
                    <small>
                      Equivalent: {String(quote.destinationCurrency ?? "")}{" "}
                      {display(quote.convertedFeeAmount)}
                    </small>
                  </div>
                  <b aria-hidden="true">=</b>
                  <div className="fee-total">
                    <span>Total to collect</span>
                    <strong>
                      {String(
                        quote.senderCurrency ?? body.sourceCurrency ?? "",
                      )}{" "}
                      {display(quote.totalToCollect)}
                    </strong>
                  </div>
                </section>
              )}
              {action.financial && (
                <p className="confirmation-notice">
                  Confirming creates the transaction and posts the calculated
                  fee.
                </p>
              )}
            </div>
          ) : (
            <div className="operation-fields">
              {action.fields.map((f) => (
                <label
                  key={f.name}
                  className={f.type === "file" ? "file-field" : undefined}
                >
                  <span>
                    {f.label}
                    {!f.required && <small>Optional</small>}
                  </span>
                  {f.type === "lookup" ? (
                    <Lookup
                      field={f}
                      value={String(values[f.name] ?? "")}
                      onChange={(v) => setValues({ ...values, [f.name]: v })}
                    />
                  ) : f.type === "select" ? (
                    <select
                      required={f.required}
                      disabled={f.disabled}
                      value={String(values[f.name] ?? "")}
                      onChange={(e) =>
                        setValues({ ...values, [f.name]: e.target.value })
                      }
                    >
                      <option value="">Select…</option>
                      {f.options?.map((o) => (
                        <option key={o} value={o}>
                          {(f.optionLabels?.[o] ?? o).replaceAll("_", " ")}
                        </option>
                      ))}
                    </select>
                  ) : f.type === "file" ? (
                    <input
                      required={f.required}
                      disabled={f.disabled || busy}
                      type="file"
                      accept={f.accept}
                      onChange={(e) => {
                        const file = e.target.files?.[0];
                        if (!file) {
                          setValues({ ...values, [f.name]: null });
                          return;
                        }
                        void uploadValue(file, f.maxBytes ?? 5 * 1024 * 1024)
                          .then((value) => {
                            setError("");
                            setValues({ ...values, [f.name]: value });
                          })
                          .catch((uploadError: unknown) => {
                            e.target.value = "";
                            setValues({ ...values, [f.name]: null });
                            setError(
                              uploadError instanceof Error
                                ? uploadError.message
                                : "The selected file could not be read.",
                            );
                          });
                      }}
                    />
                  ) : (
                    <input
                      required={f.required}
                      disabled={f.disabled}
                      type={
                        f.type === "money" || f.type === "decimal"
                          ? "text"
                          : (f.type ?? "text")
                      }
                      inputMode={
                        f.inputMode ??
                        (f.type === "money" || f.type === "decimal"
                          ? "decimal"
                          : undefined)
                      }
                      pattern={
                        f.pattern ??
                        (f.type === "money"
                          ? "[0-9]+(\\.[0-9]{1,4})?"
                          : f.type === "decimal"
                            ? "[0-9]+(\\.[0-9]{1,10})?"
                            : undefined)
                      }
                      maxLength={
                        f.maxLength ?? (f.type === "password" ? 72 : 500)
                      }
                      minLength={f.minLength ?? (f.type === "password" ? 8 : undefined)}
                      value={String(values[f.name] ?? "")}
                      onBlur={() =>
                        void applyAutoFill(f.name, String(values[f.name] ?? ""))
                      }
                      onChange={(e) =>
                        setValues({ ...values, [f.name]: e.target.value })
                      }
                      autoComplete={
                        f.type === "password" ? "new-password" : "off"
                      }
                    />
                  )}{" "}
                  {f.help && <small>{f.help}</small>}
                </label>
              ))}
            </div>
          )}
          <div className="operation-footer">
            {review && !submitted && (
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
                ? "Processing…"
                : review
                  ? submitted
                    ? "Retry same request"
                    : "Confirm"
                  : "Review"}
            </button>
          </div>
        </form>
      )}
    </Dialog>
  );
}
export function useDebounced(value: string) {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), 250);
    return () => clearTimeout(t);
  }, [value]);
  return v;
}
