import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import {
  Link,
  Navigate,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft,
  ArrowRight,
  ChevronRight,
  CircleAlert,
  Command,
  LoaderCircle,
  LogOut,
  Menu,
  Search,
  ShieldCheck,
  X,
} from "lucide-react";
import {
  api,
  getSession,
  restoreSession,
  signOut,
  subscribe,
  type Session,
  type User,
} from "./lib/session";
import { adminRoles, canAccess, roleNames, workspaces } from "./lib/workspaces";
import { Brand } from "./components/Brand";
import { Dialog } from "./components/Dialog";
import { Login } from "./pages/Login";
import { Banking } from "./pages/Banking";
import { Configuration } from "./pages/Configuration";
import { Commissions } from "./pages/Commissions";
import { BankingSummary } from "./components/BankingSummary";
import { Overview } from "./pages/Overview";
import { CustomerProfile } from "./pages/CustomerProfile";
import { CashOut } from "./pages/CashOut";
import { RemittanceReports } from "./pages/RemittanceReports";
import { CreditSales } from "./pages/CreditSales";
import { Expenses } from "./pages/Expenses";
import { ExpenseSettings } from "./pages/ExpenseSettings";

function Gate({ children }: { children: React.ReactNode }) {
  const state = useSyncExternalStore(subscribe, getSession);
  if (state.phase === "loading" || state.phase === "unavailable")
    return (
      <div className="boot-screen">
        <Brand large />
        <div className="boot-card">
          {state.phase === "loading" ? (
            <>
              <LoaderCircle className="spin" size={24} />
              <h1>Opening your workspace</h1>
              <p>Signing you in…</p>
            </>
          ) : (
            <>
              <CircleAlert size={26} />
              <h1>Connection unavailable</h1>
              <p>{state.message}</p>
              <button
                className="button primary"
                onClick={() => {
                  void restoreSession().catch(() => undefined);
                }}
              >
                Try again <ArrowRight size={16} />
              </button>
            </>
          )}
        </div>
      </div>
    );
  return <>{children}</>;
}

function Shell({ session }: { session: Session }) {
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [mobile, setMobile] = useState(false);
  const sidebarRef = useRef<HTMLElement>(null);
  const menuRef = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (!mobile) return;
    const sidebar = sidebarRef.current;
    const controls = () =>
      Array.from(
        sidebar?.querySelectorAll<HTMLElement>("button, a[href]") ?? [],
      ).filter((el) => el.getClientRects().length > 0);
    controls()[0]?.focus();
    const handler = (event: KeyboardEvent) => {
      if (event.key === "Escape") setMobile(false);
      if (event.key === "Tab") {
        const items = controls();
        const first = items[0];
        const last = items[items.length - 1];
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault();
          last?.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first?.focus();
        }
      }
    };
    window.addEventListener("keydown", handler);
    return () => {
      window.removeEventListener("keydown", handler);
      menuRef.current?.focus();
    };
  }, [mobile]);
  const [profile, setProfile] = useState(false);
  const [logout, setLogout] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [command, setCommand] = useState(false);
  const [search, setSearch] = useState("");
  const [now, setNow] = useState(Date.now());
  const identity = useQuery({
    queryKey: ["current-user", session.user.id],
    queryFn: ({ signal }) => api<User>("/api/auth/me", { signal }),
    refetchInterval: 60000,
    retry: 1,
    staleTime: 30000,
  });
  const currentSession = { ...session, user: identity.data ?? session.user };
  const user = currentSession.user;
  const allowed = workspaces.filter((w) => canAccess(w.id, user));
  const mobileTabs = ["overview", "wallets", "remittances", "cash-out"]
    .map((id) => allowed.find((w) => w.id === id))
    .filter((w): w is (typeof workspaces)[number] => !!w);
  const activeId = location.pathname.split("/")[1] || "overview";
  const customerNumber =
    activeId === "customers" ? location.pathname.split("/")[2] : undefined;
  const active = workspaces.find((w) => w.id === activeId);
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  useEffect(() => {
    const timer = window.setTimeout(
      () => {
        void restoreSession().catch(() => undefined);
      },
      Math.max(1000, session.expiresAt - Date.now() - 30000),
    );
    return () => clearTimeout(timer);
  }, [session.expiresAt]);
  useEffect(() => {
    setMobile(false);
    setCommand(false);
  }, [location.pathname]);
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === "k") {
        e.preventDefault();
        setMobile(false);
        setCommand((value) => !value);
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, []);
  const seconds = Math.max(0, Math.floor((session.expiresAt - now) / 1000));
  const remaining = `${Math.floor(seconds / 60)
    .toString()
    .padStart(2, "0")}:${(seconds % 60).toString().padStart(2, "0")}`;
  const requestLogout = () => {
    setProfile(false);
    setError("");
    setLogout(true);
  };
  return (
    <div className="app-layout">
      <a className="skip-link" href="#main-content">
        Skip to content
      </a>
      {mobile && (
        <button
          className="mobile-backdrop"
          aria-label="Close navigation"
          onClick={() => setMobile(false)}
        />
      )}
      <aside
        ref={sidebarRef}
        id="main-navigation"
        className={`sidebar ${mobile ? "is-open" : ""}`}
        aria-label="Main navigation"
      >
        <div className="sidebar-brand">
          <Brand />
          <button
            className="mobile-close icon-button"
            aria-label="Close navigation"
            onClick={() => setMobile(false)}
          >
            <X size={20} />
          </button>
        </div>
        <div className="workspace-switch">
          <span className="workspace-avatar">C</span>
          <div>
            <strong>Cashword workspace</strong>
            <small>Administration</small>
          </div>
          <ShieldCheck size={16} />
        </div>
        <nav>
          {["Workspace", "Core banking", "Payments", "Administration"].map(
            (group) => {
              const items = allowed.filter((w) => w.group === group);
              return (
                items.length > 0 && (
                  <div className="nav-group" key={group}>
                    <span className="nav-label">{group}</span>
                    {items.map((w) => (
                      <Link
                        key={w.id}
                        className={`nav-item ${activeId === w.id ? "active" : ""}`}
                        to={w.id === "overview" ? "/" : `/${w.id}`}
                        aria-current={activeId === w.id ? "page" : undefined}
                      >
                        <w.icon size={18} />
                        <span>{w.name}</span>
                        {w.stage > 4 && (
                          <span
                            className="nav-upcoming"
                            title="Upcoming workspace"
                            aria-label="Upcoming workspace"
                          />
                        )}
                      </Link>
                    ))}
                  </div>
                )
              );
            },
          )}
        </nav>
        <div className="sidebar-bottom">
          <button
            className="sidebar-profile"
            onClick={() => {
              setMobile(false);
              setProfile(true);
            }}
          >
            <span className="avatar">
              {user.username.slice(0, 2).toUpperCase()}
            </span>
            <span>
              <strong>{user.username}</strong>
              <small>{roleNames[user.role]}</small>
            </span>
            <ChevronRight size={16} />
          </button>
        </div>
      </aside>
      <div className="main-layout" inert={mobile}>
        <header className="topbar">
          <div className="topbar-left">
            <button
              ref={menuRef}
              aria-controls="main-navigation"
              aria-expanded={mobile}
              className="icon-button mobile-menu"
              aria-label="Open navigation"
              onClick={() => setMobile(true)}
            >
              <Menu size={22} />
            </button>
            <span className="topbar-title">Administration</span>
            <span className="mobile-header-title">
              <small>Cashword</small>
              <strong>{active?.name ?? "Workspace"}</strong>
            </span>
            <span className="topbar-divider" />
            <span className="topbar-context">
              {active?.name ?? "Workspace"}
            </span>
          </div>
          <div className="topbar-actions">
            <button
              className="command-trigger"
              aria-label="Find a workspace"
              onClick={() => {
                setSearch("");
                setCommand(true);
              }}
            >
              <Search size={16} />
              <span>Find a workspace</span>
              <kbd>
                <Command size={11} /> K
              </kbd>
            </button>
            <button
              className="mobile-profile"
              aria-label="My profile"
              onClick={() => setProfile(true)}
            >
              {user.username.slice(0, 1).toUpperCase()}
            </button>
            <button
              className="icon-button"
              aria-label="Sign out"
              onClick={requestLogout}
            >
              <LogOut size={18} />
            </button>
          </div>
        </header>
        <main id="main-content" className="main-content" tabIndex={-1}>
          {identity.isError && (
            <div className="alert connection-alert" role="status">
              <CircleAlert size={18} />
              <span>
                Account connection interrupted. We’ll check again shortly.
              </span>
              <button
                className="text-button"
                onClick={() => {
                  void identity.refetch();
                }}
              >
                Retry
              </button>
            </div>
          )}
          {!adminRoles.includes(user.role) ||
          (active && !canAccess(activeId, user)) ? (
            <EmptyPage
              title="This workspace is restricted"
              description="Your account does not have permission to open this area. Contact your administrator if your access needs to change."
              restricted
            />
          ) : activeId === "overview" ? (
            <>
              <Overview
                summary={<BankingSummary />}
                session={currentSession}
                remaining={remaining}
                connected={!!identity.data && !identity.isError}
                onProfile={() => setProfile(true)}
              />
            </>
          ) : active ? (
            activeId === "customers" && customerNumber ? (
              <CustomerProfile customerNumber={customerNumber} user={user} />
            ) : activeId === "configuration" ? (
              <Configuration user={user} />
            ) : activeId === "commissions" ? (
              <Commissions />
            ) : activeId === "cash-out" ? (
              <CashOut />
            ) : activeId === "remittance-reports" ? (
              <RemittanceReports />
            ) : activeId === "credit-sales" ? (
              <CreditSales user={user} />
            ) : activeId === "expenses" ? (
              <Expenses />
            ) : activeId === "expense-settings" ? (
              <ExpenseSettings />
            ) : (
              <Banking key={activeId} resource={activeId} user={user} />
            )
          ) : (
            <EmptyPage
              title="Page not found"
              description="This page is not part of your administration workspace."
            />
          )}
        </main>
        <nav className="mobile-tabbar" aria-label="Primary navigation">
          {mobileTabs.map((w) => (
            <Link
              key={w.id}
              to={w.id === "overview" ? "/" : `/${w.id}`}
              className={`mobile-tab ${activeId === w.id ? "active" : ""}`}
              aria-current={activeId === w.id ? "page" : undefined}
            >
              <w.icon size={21} aria-hidden="true" />
              <span>{w.name}</span>
            </Link>
          ))}
          <button
            className={`mobile-tab ${!mobileTabs.some((w) => w.id === activeId) ? "active" : ""}`}
            aria-label="More workspaces"
            aria-expanded={mobile}
            aria-controls="main-navigation"
            onClick={() => setMobile(true)}
          >
            <Menu size={21} aria-hidden="true" />
            <span>More</span>
          </button>
        </nav>
        <footer className="app-footer">
          <span>© {new Date().getFullYear()} Cashword</span>
          <span>
            <ShieldCheck size={12} /> Access controlled · Administration v1.0
          </span>
        </footer>
      </div>
      <Dialog
        open={profile}
        drawer
        title="My profile"
        onClose={() => setProfile(false)}
      >
        <div className="profile-intro">
          <span className="avatar big">
            {user.username.slice(0, 2).toUpperCase()}
          </span>
          <h3>{user.username}</h3>
          <span className="badge success">
            {user.status === "ACTIVE"
              ? "Active account"
              : user.status.toLowerCase()}
          </span>
        </div>
        <dl className="detail-list">
          <div>
            <dt>Access level</dt>
            <dd>{roleNames[user.role]}</dd>
          </div>
          <div>
            <dt>Username</dt>
            <dd>{user.username}</dd>
          </div>
          <div>
            <dt>User ID</dt>
            <dd className="mono">{user.id}</dd>
          </div>
          {user.customerId && (
            <div>
              <dt>Customer ID</dt>
              <dd className="mono">{user.customerId}</dd>
            </div>
          )}
          <div>
            <dt>Session remaining</dt>
            <dd className="tabular">{remaining}</dd>
          </div>
        </dl>
        <div className="info-note">
          <ShieldCheck size={18} />
          <p>
            Access is assigned by your organisation. Contact your administrator
            to request a change.
          </p>
        </div>
        <button className="button secondary full-width" onClick={requestLogout}>
          <LogOut size={16} /> Sign out
        </button>
      </Dialog>
      <Dialog
        open={logout}
        title="Sign out of Cashword?"
        onClose={() => setLogout(false)}
        busy={busy}
      >
        <p className="muted">You’ll be signed out on this browser.</p>
        {error && (
          <div className="alert" role="alert">
            {error}
          </div>
        )}
        <div className="dialog-actions">
          <button
            className="button secondary"
            disabled={busy}
            onClick={() => setLogout(false)}
          >
            Stay signed in
          </button>
          <button
            className="button primary"
            disabled={busy}
            onClick={async () => {
              setBusy(true);
              try {
                await signOut();
                queryClient.clear();
                navigate("/login", { replace: true });
              } catch (e) {
                setError(
                  e instanceof Error ? e.message : "Unable to sign out.",
                );
              } finally {
                setBusy(false);
              }
            }}
          >
            {busy ? (
              <LoaderCircle className="spin" size={16} />
            ) : (
              <LogOut size={16} />
            )}{" "}
            Sign out
          </button>
        </div>
      </Dialog>
      <Dialog
        open={command}
        title="Find a workspace"
        onClose={() => setCommand(false)}
      >
        <div className="search-field command-input">
          <Search size={18} />
          <input
            autoFocus
            aria-label="Find a workspace"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Type a workspace name…"
          />
        </div>
        <div className="command-results">
          {allowed
            .filter((w) => w.name.toLowerCase().includes(search.toLowerCase()))
            .map((w) => (
              <button
                key={w.id}
                onClick={() => {
                  navigate(w.id === "overview" ? "/" : `/${w.id}`);
                  setCommand(false);
                }}
              >
                <w.icon size={18} />
                <span>{w.name}</span>
                {w.stage > 4 && <small>Upcoming</small>}
                <ArrowRight size={15} />
              </button>
            ))}
          {!allowed.some((w) =>
            w.name.toLowerCase().includes(search.toLowerCase()),
          ) && <p className="muted">No matching workspaces.</p>}
        </div>
      </Dialog>
    </div>
  );
}
function EmptyPage({
  title,
  description,
  restricted = false,
}: {
  title: string;
  description: string;
  restricted?: boolean;
}) {
  return (
    <section className="coming-panel">
      <span className="coming-icon">
        {restricted ? <ShieldCheck size={30} /> : <Search size={30} />}
      </span>
      <h1>{title}</h1>
      <p>{description}</p>
      <Link className="button secondary" to="/">
        <ArrowLeft size={16} /> Back to overview
      </Link>
    </section>
  );
}
export default function App() {
  const state = useSyncExternalStore(subscribe, getSession);
  const cache = useQueryClient();
  const location = useLocation();
  useEffect(() => {
    if (state.phase === "anonymous") cache.clear();
  }, [state.phase, cache]);
  return (
    <Gate>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route
          path="/*"
          element={
            state.session ? (
              <Shell session={state.session} />
            ) : (
              <Navigate
                to="/login"
                replace
                state={{
                  from: location.pathname + location.search + location.hash,
                }}
              />
            )
          }
        />
      </Routes>
    </Gate>
  );
}
