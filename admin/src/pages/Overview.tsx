import { useMemo, useState, type ReactNode } from "react";
import {
  ArrowDownUp,
  ArrowRight,
  Check,
  ChevronLeft,
  ChevronRight,
  Clock3,
  Search,
  ShieldCheck,
  SlidersHorizontal,
  UserRound,
  LockKeyhole,
  CircleCheck,
  Network,
} from "lucide-react";
import type { Session } from "../lib/session";
import { workspaces, roleNames, type Workspace } from "../lib/workspaces";
import { Dialog } from "../components/Dialog";
export function Overview({
  session,
  remaining,
  connected,
  onProfile,
  summary,
}: {
  session: Session;
  remaining: string;
  connected: boolean;
  onProfile: () => void;
  summary?: ReactNode;
}) {
  const [search, setSearch] = useState("");
  const [group, setGroup] = useState("All areas");
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState(false);
  const [selected, setSelected] = useState<Workspace | null>(null);
  const modules = useMemo(() => {
    const data = workspaces.filter(
      (w) =>
        w.id !== "overview" &&
        w.roles.includes(session.user.role) &&
        (group === "All areas" || w.group === group) &&
        (w.name + " " + w.description)
          .toLowerCase()
          .includes(search.toLowerCase()),
    );
    return sort ? [...data].sort((a, b) => a.name.localeCompare(b.name)) : data;
  }, [search, group, sort, session.user.role]);
  const pages = Math.max(1, Math.ceil(modules.length / 5));
  const current = Math.min(page, pages - 1);
  const rows = modules.slice(current * 5, current * 5 + 5);
  return (
    <>
      <div className="page-heading">
        <div className="breadcrumb">
          Workspace <span>/</span> Overview
        </div>
        <div className="heading-row">
          <div>
            <h1>Administration overview</h1>
            <p>Your workspace, access and operational areas at a glance.</p>
          </div>
          <button className="button secondary" onClick={onProfile}>
            <UserRound size={16} /> My profile
          </button>
        </div>
      </div>
      <section className="welcome-banner">
        <div>
          <span className="eyebrow">YOUR OPERATIONS, CONNECTED</span>
          <h2>Welcome back, {session.user.username}.</h2>
          <p>You’re signed in to the Poscloud administration workspace.</p>
        </div>
        <div className="welcome-seal" aria-hidden="true">
          <ShieldCheck size={38} />
        </div>
        <span className="welcome-status">
          <span className="status-dot" /> Authenticated workspace
        </span>
      </section>
      {summary}
      <div className="summary-grid">
        <section className="summary-card">
          <div className="summary-label">
            Access level <ShieldCheck size={17} />
          </div>
          <strong>{roleNames[session.user.role]}</strong>
          <span>Permissions assigned to your account</span>
        </section>
        <section className="summary-card">
          <div className="summary-label">
            Session time remaining <Clock3 size={17} />
          </div>
          <strong className="tabular">{remaining}</strong>
          <span>Renews securely while you work</span>
        </section>
        <section className="summary-card">
          <div className="summary-label">
            Account connection <Network size={17} />
          </div>
          <strong className="connection-value">
            <span className={`status-dot ${connected ? "" : "amber"}`} />
            {connected ? "Connected" : "Reconnecting"}
          </strong>
          <span>
            {connected
              ? "Your account is verified by Poscloud"
              : "Checking your account connection"}
          </span>
        </section>
      </div>
      <div className="workspace-columns">
        <section className="panel directory">
          <div className="panel-heading">
            <div>
              <h2>
                Workspace directory{" "}
                <span className="count">
                  {
                    workspaces.filter(
                      (w) =>
                        w.id !== "overview" &&
                        w.roles.includes(session.user.role),
                    ).length
                  }
                </span>
              </h2>
              <p>Operational areas available to your role.</p>
            </div>
            <span className="badge neutral">Operations workspace</span>
          </div>
          <div className="table-toolbar">
            <div className="search-field">
              <Search size={17} />
              <input
                aria-label="Search workspaces"
                placeholder="Search workspaces…"
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value);
                  setPage(0);
                }}
              />
            </div>
            <label className="filter-select">
              <SlidersHorizontal size={15} />
              <select
                aria-label="Filter by area"
                value={group}
                onChange={(e) => {
                  setGroup(e.target.value);
                  setPage(0);
                }}
              >
                <option>All areas</option>
                <option>Core banking</option>
                <option>Payments</option>
                <option>Administration</option>
              </select>
            </label>
          </div>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>
                    <button
                      className="sort-button"
                      onClick={() => {
                        setSort(!sort);
                        setPage(0);
                      }}
                    >
                      Workspace <ArrowDownUp size={12} />
                    </button>
                  </th>
                  <th>Area</th>
                  <th>Availability</th>
                  <th>
                    <span className="sr-only">Details</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {rows.map((w) => (
                  <tr key={w.id}>
                    <td>
                      <div className="workspace-cell">
                        <span className="module-icon">
                          <w.icon size={18} />
                        </span>
                        <div>
                          <button
                            className="text-button"
                            onClick={() => setSelected(w)}
                          >
                            {w.name}
                          </button>
                          <small>{w.description}</small>
                        </div>
                      </div>
                    </td>
                    <td>
                      <span className="table-area">{w.group}</span>
                    </td>
                    <td>
                      <span className="badge planned">Available</span>
                    </td>
                    <td>
                      <button
                        className="icon-button"
                        aria-label={`View ${w.name} details`}
                        onClick={() => setSelected(w)}
                      >
                        <ArrowRight size={16} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {rows.length === 0 && (
              <div className="empty-state">
                <Search size={22} />
                <h3>No workspaces found</h3>
                <p>Try another name or change the area filter.</p>
                <button
                  className="button secondary"
                  onClick={() => {
                    setSearch("");
                    setGroup("All areas");
                  }}
                >
                  Clear filters
                </button>
              </div>
            )}
          </div>
          <div className="table-footer">
            <span>
              {modules.length
                ? `${current * 5 + 1}–${Math.min((current + 1) * 5, modules.length)} of ${modules.length} workspaces`
                : "0 workspaces"}
            </span>
            <div>
              <button
                className="icon-button"
                aria-label="Previous page"
                disabled={current === 0}
                onClick={() => setPage(current - 1)}
              >
                <ChevronLeft size={17} />
              </button>
              <span>
                {current + 1} / {pages}
              </span>
              <button
                className="icon-button"
                aria-label="Next page"
                disabled={current === pages - 1}
                onClick={() => setPage(current + 1)}
              >
                <ChevronRight size={17} />
              </button>
            </div>
          </div>
        </section>
        <aside className="overview-aside">
          <section className="panel access-panel">
            <span className="access-icon">
              <ShieldCheck size={21} />
            </span>
            <h2>A secure foundation</h2>
            <p>
              Your account determines which areas and actions you can access.
            </p>
            <ul>
              <li>
                <CircleCheck size={16} />
                <span>Verified account access</span>
              </li>
              <li>
                <CircleCheck size={16} />
                <span>Role-aware navigation</span>
              </li>
              <li>
                <CircleCheck size={16} />
                <span>Protected session renewal</span>
              </li>
            </ul>
            <button className="text-button green-link" onClick={onProfile}>
              Review your access <ArrowRight size={15} />
            </button>
          </section>
          <section className="release-note">
            <span className="eyebrow">RELEASE 01</span>
            <h3>Your daily operations.</h3>
            <p>
              Manage customers, accounts and payments through the connected
              workspaces. Every operation follows your assigned permissions.
            </p>
            <div>
              <span className="mini-check">
                <Check size={12} />
              </span>{" "}
              Backend connected
            </div>
          </section>
        </aside>
      </div>
      <Dialog
        open={!!selected}
        drawer
        title={selected?.name ?? "Workspace details"}
        onClose={() => setSelected(null)}
      >
        {selected && (
          <>
            <div className="drawer-feature">
              <selected.icon size={27} />
            </div>
            <span className="badge planned">Available workspace</span>
            <h3>{selected.name}</h3>
            <p className="muted">{selected.description}</p>
            <dl className="detail-list">
              <div>
                <dt>Area</dt>
                <dd>{selected.group}</dd>
              </div>
              <div>
                <dt>Your role</dt>
                <dd>{roleNames[session.user.role]}</dd>
              </div>
              <div>
                <dt>Workspace access</dt>
                <dd>Permitted</dd>
              </div>
              <div>
                <dt>Availability</dt>
                <dd>Available</dd>
              </div>
            </dl>
            <div className="info-note">
              <LockKeyhole size={18} />
              <p>
                Open this workspace from navigation to view records and perform
                permitted operations.
              </p>
            </div>
            <button
              className="button secondary full-width"
              onClick={() => setSelected(null)}
            >
              Back to overview
            </button>
          </>
        )}
      </Dialog>
    </>
  );
}
