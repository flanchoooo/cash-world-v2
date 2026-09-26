export type Role =
  | "SUPER_ADMIN"
  | "OPERATIONS"
  | "CORPORATE_ADMIN"
  | "CORPORATE_USER"
  | "CUSTOMER"
  | "AGENT";
export type Permission =
  | "OVERVIEW_VIEW" | "CUSTOMERS_VIEW" | "CUSTOMERS_MANAGE" | "WALLETS_VIEW" | "WALLET_MANAGE"
  | "WALLET_DEPOSIT" | "WALLET_WITHDRAW" | "WALLET_SEND" | "WALLET_ADJUST" | "TRANSACTIONS_VIEW"
  | "TRANSACTION_REVERSE" | "REMITTANCES_VIEW" | "REMITTANCE_SEND"
  | "REMITTANCE_CASHOUT" | "REMITTANCE_REPORTS_VIEW" | "CREDIT_SALES_VIEW" | "CREDIT_SALES_MANAGE"
  | "COMMISSIONS_VIEW" | "USERS_MANAGE" | "CONFIGURATION_VIEW"
  | "CONFIGURATION_MANAGE" | "AUDIT_VIEW" | "EXPENSES_MANAGE";
export interface User {
  id: string;
  customerId: string | null;
  username: string;
  role: Role;
  status: "ACTIVE" | "BLOCKED" | "DISABLED";
  permissionsCustomized?: boolean;
  permissions?: Permission[];
}
export interface Session {
  accessToken: string;
  expiresIn: number;
  user: User;
  expiresAt: number;
}
type State = {
  phase: "loading" | "authenticated" | "anonymous" | "unavailable";
  session: Session | null;
  message: string | null;
};
let state: State = { phase: "loading", session: null, message: null };
const listeners = new Set<() => void>();
let revision = 0;
let refreshFlight: Promise<Session | null> | undefined;
const channel =
  typeof BroadcastChannel !== "undefined"
    ? new BroadcastChannel("cashword-session-events")
    : null;
export const subscribe = (listener: () => void) => {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
};
export const getSession = () => state;
function update(next: State) {
  state = next;
  listeners.forEach((listener) => listener());
}
function accept(data: Omit<Session, "expiresAt">) {
  const session = { ...data, expiresAt: Date.now() + data.expiresIn * 1000 };
  update({ phase: "authenticated", session, message: null });
  return session;
}
export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
  ) {
    super(message);
  }
}
const operationErrors: Record<string, string> = {
  INSUFFICIENT_FUNDS: "The debit wallet does not have enough available funds.",
  WALLET_BLOCKED:
    "This wallet is blocked or closed. Activate it before posting.",
  CUSTOMER_BLOCKED: "This customer is not active.",
  INVALID_CURRENCY: "Choose an active currency matching the selected wallet.",
  INVALID_AMOUNT:
    "Enter a positive amount within the currency's supported decimal places.",
  INVALID_REQUEST: "Check the required fields, formats and amount limits.",
  FEE_NOT_CONFIGURED:
    "No applicable fee rule is configured for this operation.",
  FX_RATE_NOT_CONFIGURED:
    "No exchange rate is configured for this currency pair.",
  TRANSACTION_ALREADY_REVERSED: "This transaction has already been reversed.",
  TRANSACTION_NOT_REVERSIBLE: "This transaction cannot be reversed.",
  REMITTANCE_ALREADY_PAID: "This remittance has already been paid out.",
  REMITTANCE_CODE_MISMATCH: "The six-digit collection code is incorrect.",
  REMITTANCE_MOBILE_MISMATCH:
    "The recipient mobile does not match this remittance.",
  INVALID_PROOF_OF_PAYMENT:
    "Upload a PDF, JPG, PNG or WebP proof of payment smaller than 5 MB.",
  PROOF_OF_PAYMENT_NOT_FOUND: "No proof of payment is attached.",
  PROVIDER_ERROR: "The biller could not complete this request.",
  PROVIDER_TIMEOUT:
    "The provider result is uncertain. Use enquiry to check its status.",
  CANNOT_CHANGE_OWN_ACCESS:
    "Use another administrator to change your account access.",
  LAST_ADMINISTRATOR: "At least one active super administrator must remain.",
  CONFLICT:
    "A record conflicts with this request. For a financial operation, retry the same request.",
  PRODUCT_NOT_ALLOCATED: "This product is not allocated to your account.",
  PRODUCT_ALREADY_ALLOCATED:
    "This product is already allocated to the customer.",
  INVALID_COMMISSION_SPLIT:
    "Agent commission plus platform commission must equal the total commission.",
  COLLECTING_AGENT_REQUIRED:
    "Enter the collecting agent's name, mobile number and national ID for a credit sale.",
  USERNAME_ALREADY_EXISTS: "That API username is already in use.",
  PASSWORD_REQUIRED: "Enter a password with at least 8 characters.",
  INVALID_USERNAME: "Enter a valid API username.",
  INVALID_PASSWORD: "Use a password between 8 and 72 bytes.",
  MOBILE_PIN_REQUIRED: "Set a four-digit mobile PIN for transaction approval.",
  MOBILE_PIN_NOT_CONFIGURED:
    "A four-digit mobile PIN must be configured before making transactions.",
  INVALID_MOBILE_PIN: "The four-digit mobile PIN is incorrect.",
  PRODUCT_PLAN_NOT_FOUND: "Select a predefined product plan from Settings.",
  PRODUCT_PLAN_INACTIVE: "The selected product plan is inactive.",
};
async function responseData(response: Response) {
  const data = await response.json().catch(() => null);
  if (!response.ok)
    throw new ApiError(
      response.status,
      data?.code || (response.status >= 500 ? "BACKEND_UNAVAILABLE" : "SERVICE_ERROR"),
      response.status === 401
        ? "The username or password is incorrect, or the account is blocked."
        : response.status === 403
          ? "Your account does not have permission for this action."
          : response.status >= 500 && !data
            ? "The backend is starting or unavailable. Please try again shortly."
          : (operationErrors[data?.code] ??
            (response.status < 500 && /^[A-Z_]+$/.test(data?.code ?? "")
              ? `Request rejected: ${data.code.toLowerCase().replaceAll("_", " ")}.`
              : "The service could not complete your request. Please try again.")),
    );
  return data;
}
async function browserPost(action: string, body?: unknown) {
  let response: Response;
  try {
    response = await fetch(`/api/auth/browser/${action}`, {
      method: "POST",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body ?? {}),
      signal: AbortSignal.timeout(15000),
    });
  } catch {
    throw new ApiError(
      0,
      "CONNECTION_ERROR",
      "We could not reach Cashword. Check your connection and try again.",
    );
  }
  return responseData(response);
}
export function restoreSession(): Promise<Session | null> {
  if (refreshFlight) return refreshFlight;
  const currentRevision = revision;
  const wasAuthenticated = state.phase === "authenticated";
  const work = async () => {
    try {
      const data = await browserPost("refresh");
      if (revision !== currentRevision) return null;
      return accept(data);
    } catch (error) {
      if (revision !== currentRevision) return null;
      if (error instanceof ApiError && [401, 403].includes(error.status)) {
        update({
          phase: "anonymous",
          session: null,
          message: wasAuthenticated
            ? "Your session has ended. Please sign in again."
            : null,
        });
        return null;
      }
      // A network outage must not be mistaken for revoked credentials.
      update({
        ...state,
        phase: state.session ? "authenticated" : "unavailable",
        message:
          error instanceof Error ? error.message : "Connection unavailable.",
      });
      throw error;
    }
  };
  const attempt = (async () =>
    typeof navigator !== "undefined" && navigator.locks
      ? await navigator.locks.request("cashword-refresh", work)
      : await work())();
  refreshFlight = attempt.finally(() => {
    refreshFlight = undefined;
  });
  return refreshFlight;
}
export async function signIn(username: string, password: string) {
  revision++;
  const work = async () => {
    const data = await browserPost("login", { username, password });
    return accept(data);
  };
  if (navigator.locks)
    return await navigator.locks.request("cashword-refresh", work);
  await refreshFlight?.catch(() => null);
  return work();
}
export async function signOut() {
  // Serialize with rotations in this and other tabs so logout revokes the current cookie.
  const work = async () => {
    await browserPost("logout");
    revision++;
    update({ phase: "anonymous", session: null, message: null });
    channel?.postMessage("signed-out");
  };
  if (navigator.locks) await navigator.locks.request("cashword-refresh", work);
  else {
    await refreshFlight?.catch(() => null);
    await work();
  }
}
if (channel)
  channel.onmessage = (event) => {
    if (event.data === "signed-out") {
      revision++;
      update({
        phase: "anonymous",
        session: null,
        message: "You signed out in another tab.",
      });
    }
  };
export async function api<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  let session = state.session;
  if (session && session.expiresAt - Date.now() < 30000)
    session = await restoreSession();
  if (!session)
    throw new ApiError(401, "UNAUTHORIZED", "Please sign in again.");
  const send = async (token: string) => {
    try {
      return await fetch(path, {
        ...options,
        credentials: "same-origin",
        headers: {
          ...(options.body ? { "Content-Type": "application/json" } : {}),
          ...options.headers,
          Authorization: `Bearer ${token}`,
        },
        signal: options.signal ?? AbortSignal.timeout(30000),
      });
    } catch {
      throw new ApiError(
        0,
        "CONNECTION_ERROR",
        "We could not reach Cashword. Please try again shortly.",
      );
    }
  };
  let response = await send(session.accessToken);
  if (response.status === 401) {
    const refreshed = await restoreSession();
    if (!refreshed)
      throw new ApiError(401, "UNAUTHORIZED", "Please sign in again.");
    response = await send(refreshed.accessToken);
  }
  return responseData(response) as Promise<T>;
}

export async function apiBlob(path: string): Promise<Blob> {
  let session = state.session;
  if (session && session.expiresAt - Date.now() < 30000)
    session = await restoreSession();
  if (!session)
    throw new ApiError(401, "UNAUTHORIZED", "Please sign in again.");
  const send = async (token: string) => {
    try {
      return await fetch(path, {
        credentials: "same-origin",
        headers: { Authorization: `Bearer ${token}` },
        signal: AbortSignal.timeout(30000),
      });
    } catch {
      throw new ApiError(
        0,
        "CONNECTION_ERROR",
        "We could not reach Cashword. Please try again shortly.",
      );
    }
  };
  let response = await send(session.accessToken);
  if (response.status === 401) {
    const refreshed = await restoreSession();
    if (!refreshed)
      throw new ApiError(401, "UNAUTHORIZED", "Please sign in again.");
    response = await send(refreshed.accessToken);
  }
  if (!response.ok) await responseData(response);
  return response.blob();
}
