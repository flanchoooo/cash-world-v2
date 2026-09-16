import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Banknote,
  CalendarDays,
  Download,
  FileChartColumn,
  FileText,
  Hourglass,
  Landmark,
  LoaderCircle,
  ReceiptText,
} from "lucide-react";
import { DataTable } from "../components/Operations";
import { api, apiBlob } from "../lib/session";
import type { Row } from "../lib/operations";

type ReportType =
  | "RBZ"
  | "INCOME_BY_CURRENCY"
  | "PENDING_CASHOUT_BY_CURRENCY"
  | "CASHED_OUT_BY_CURRENCY"
  | "DAY_END";
type Period = "DAY" | "WEEK" | "MONTH";
type Report = {
  type: ReportType;
  title: string;
  period: Period;
  anchor: string;
  from: string;
  to: string;
  columns: { key: string; label: string }[];
  rows: Row[];
};

const reports: {
  type: ReportType;
  label: string;
  description: string;
  icon: typeof Landmark;
}[] = [
  {
    type: "RBZ",
    label: "RBZ report",
    description: "Detailed sender, recipient, FX and order audit data.",
    icon: Landmark,
  },
  {
    type: "INCOME_BY_CURRENCY",
    label: "Platform income",
    description: "Fees and collections grouped by sender currency.",
    icon: Banknote,
  },
  {
    type: "PENDING_CASHOUT_BY_CURRENCY",
    label: "Pending cash-outs",
    description: "Available payout orders grouped by payout currency.",
    icon: Hourglass,
  },
  {
    type: "CASHED_OUT_BY_CURRENCY",
    label: "Cashed-out orders",
    description: "Completed payout orders grouped by payout currency.",
    icon: ReceiptText,
  },
  {
    type: "DAY_END",
    label: "Day-end",
    description: "Teller send-money and cash-out totals by currency.",
    icon: FileChartColumn,
  },
];

function localDate() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
}

export function RemittanceReports() {
  const [type, setType] = useState<ReportType>("RBZ");
  const [period, setPeriod] = useState<Period>("DAY");
  const [anchor, setAnchor] = useState(localDate);
  const [downloading, setDownloading] = useState<"CSV" | "PDF" | null>(null);
  const [downloadError, setDownloadError] = useState("");
  const params = new URLSearchParams({ type, period, anchor });
  const query = useQuery({
    queryKey: ["remittance-report", type, period, anchor],
    queryFn: () => api<Report>(`/api/admin/remittance-reports?${params}`),
  });

  async function download(format: "CSV" | "PDF") {
    setDownloading(format);
    setDownloadError("");
    try {
      const exportParams = new URLSearchParams({
        type,
        period,
        anchor,
        format,
      });
      const blob = await apiBlob(
        `/api/admin/remittance-reports/export?${exportParams}`,
      );
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `${type.toLowerCase().replaceAll("_", "-")}-${anchor}.${format.toLowerCase()}`;
      link.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      setDownloadError(
        error instanceof Error
          ? error.message
          : "The report could not be downloaded.",
      );
    } finally {
      setDownloading(null);
    }
  }

  return (
    <div className="reports-page">
      <div className="page-heading reports-heading">
        <div className="breadcrumb">Payments / Remittance reports</div>
        <h1>Remittance reports</h1>
        <p>Regulatory, income, cash-out and teller reporting in one place.</p>
      </div>

      <div className="report-type-grid">
        {reports.map((report) => (
          <button
            key={report.type}
            className={`report-type-card ${type === report.type ? "active" : ""}`}
            onClick={() => setType(report.type)}
          >
            <report.icon size={20} />
            <span>
              <strong>{report.label}</strong>
              <small>{report.description}</small>
            </span>
          </button>
        ))}
      </div>

      <section className="report-panel">
        <div className="report-toolbar">
          <div className="report-title">
            <span className="report-title-icon">
              <FileChartColumn size={21} />
            </span>
            <div>
              <span>SELECTED REPORT</span>
              <h2>
                {query.data?.title ??
                  reports.find((r) => r.type === type)?.label}
              </h2>
            </div>
          </div>
          <div className="report-filters">
            <label>
              <span>Period</span>
              <select
                value={period}
                onChange={(e) => setPeriod(e.target.value as Period)}
              >
                <option value="DAY">Day</option>
                <option value="WEEK">Week</option>
                <option value="MONTH">Month</option>
              </select>
            </label>
            <label>
              <span>Reporting date</span>
              <div className="report-date-input">
                <CalendarDays size={16} />
                <input
                  type="date"
                  value={anchor}
                  onChange={(e) => setAnchor(e.target.value)}
                />
              </div>
            </label>
            <div className="report-downloads">
              <button
                className="button secondary"
                disabled={!!downloading || query.isLoading}
                onClick={() => void download("CSV")}
              >
                {downloading === "CSV" ? (
                  <LoaderCircle className="spin" size={16} />
                ) : (
                  <Download size={16} />
                )}
                CSV
              </button>
              <button
                className="button primary"
                disabled={!!downloading || query.isLoading}
                onClick={() => void download("PDF")}
              >
                {downloading === "PDF" ? (
                  <LoaderCircle className="spin" size={16} />
                ) : (
                  <FileText size={16} />
                )}
                PDF
              </button>
            </div>
          </div>
        </div>

        {query.data && (
          <div className="report-period-summary">
            <span>
              {query.data.rows.length} report row
              {query.data.rows.length === 1 ? "" : "s"}
            </span>
            <span>
              {new Date(query.data.from).toLocaleString()} —{" "}
              {new Date(query.data.to).toLocaleString()}
            </span>
          </div>
        )}
        {(query.error || downloadError) && (
          <p className="operation-error" role="alert">
            {downloadError ||
              (query.error instanceof Error
                ? query.error.message
                : "The report could not be loaded.")}
          </p>
        )}
        {query.isLoading ? (
          <div className="report-loading">
            <LoaderCircle className="spin" /> Loading report…
          </div>
        ) : (
          <DataTable
            rows={query.data?.rows ?? []}
            columns={(query.data?.columns ?? []).map((column) => [
              column.key,
              column.label,
            ])}
          />
        )}
      </section>
    </div>
  );
}
