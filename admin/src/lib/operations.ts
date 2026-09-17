import { api } from "./session";
export type Row = Record<string, unknown>;
export type Page = { items: Row[]; total: number; page: number; size: number };
export type Field = {
  name: string;
  label: string;
  type?:
    | "text"
    | "password"
    | "email"
    | "money"
    | "decimal"
    | "number"
    | "datetime-local"
    | "select"
    | "lookup"
    | "checkboxes"
    | "file";
  required?: boolean;
  options?: string[];
  optionLabels?: Record<string, string>;
  optionLabel?: (row: Row) => string;
  source?: string;
  valueKey?: string;
  help?: string;
  disabled?: boolean;
  accept?: string;
  maxBytes?: number;
  pattern?: string;
  inputMode?: "decimal" | "numeric" | "text";
  maxLength?: number;
  minLength?: number;
  min?: number | string;
  max?: number | string;
  step?: number | string;
  title?: string;
};
export type Action = {
  title: string;
  path: string;
  method?: string;
  fields: Field[];
  initial?: Row;
  financial?: boolean;
  fullPage?: boolean;
  note?: string;
  quotePath?: string;
  shareText?: (row: Row, receiptUrl?: string) => string;
  shareRecipient?: (row: Row) => string;
  receiptSharePath?: (row: Row) => string;
  autoFill?: {
    field: string;
    path: (value: string) => string;
  }[];
};
export const field = (
  name: string,
  label: string,
  extra: Partial<Field> = {},
): Field => ({ name, label, required: true, ...extra });
export const choice = (
  name: string,
  label: string,
  options: string[],
  required = true,
  optionLabels?: Record<string, string>,
) => field(name, label, { type: "select", options, required, optionLabels });
export const lookup = (
  name: string,
  label: string,
  source: string,
  valueKey = "id",
  required = true,
  optionLabel?: (row: Row) => string,
) =>
  field(name, label, {
    type: "lookup",
    source,
    valueKey,
    required,
    optionLabel,
  });
export const checkboxes = (
  name: string,
  label: string,
  source: string,
  optionLabel?: (row: Row) => string,
) => field(name, label, { type: "checkboxes", source, optionLabel });
export const money = (
  name = "amount",
  label = "Amount",
  required = true,
  extra: Partial<Field> = {},
) => field(name, label, { type: "money", required, ...extra });
export const display = (v: unknown): string =>
  v === null || v === undefined || v === ""
    ? "—"
    : typeof v === "boolean"
      ? v
        ? "Yes"
        : "No"
      : String(v);
export function csv(rows: Row[], name: string) {
  if (!rows.length) return;
  const keys = Object.keys(rows[0]).filter(
    (k) => !rows.some((r) => typeof r[k] === "object" && r[k] !== null),
  );
  const quote = (v: unknown) => {
    let s = v == null ? "" : String(v);
    if (/^[=+@\-\t\r]/.test(s)) s = "'" + s;
    return '"' + s.replaceAll('"', '""') + '"';
  };
  const text = [
    keys.map(quote).join(","),
    ...rows.map((r) => keys.map((k) => quote(r[k])).join(",")),
  ].join("\r\n");
  const url = URL.createObjectURL(
    new Blob(["\ufeff" + text], { type: "text/csv;charset=utf-8" }),
  );
  const a = document.createElement("a");
  a.href = url;
  a.download = name + ".csv";
  a.click();
  URL.revokeObjectURL(url);
}
export async function execute(action: Action, body: Row, key: string) {
  return api<Row>(action.path, {
    method: action.method ?? "POST",
    headers: action.financial ? { "Idempotency-Key": key } : {},
    body: JSON.stringify(body),
  });
}
