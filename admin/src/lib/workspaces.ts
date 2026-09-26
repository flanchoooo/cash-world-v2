import {
  LayoutDashboard,
  Users,
  ArrowLeftRight,
  Globe2,
  Settings2,
  ShieldCheck,
  BadgePercent,
  Landmark,
  HandCoins,
  FileChartColumn,
  BadgeDollarSign,
  Receipt,
} from "lucide-react";
import type { Permission, Role, User } from "./session";
export const roleNames: Record<Role, string> = {
  SUPER_ADMIN: "Super administrator",
  OPERATIONS: "Operations officer",
  CORPORATE_ADMIN: "Corporate administrator",
  CORPORATE_USER: "Corporate user",
  CUSTOMER: "Customer",
  AGENT: "Agent",
};
export const adminRoles: Role[] = [
  "SUPER_ADMIN",
  "OPERATIONS",
  "CORPORATE_ADMIN",
];
export const staffRoles: Role[] = ["SUPER_ADMIN", "OPERATIONS"];
export const workspaces = [
  {
    id: "overview",
    name: "Overview",
    icon: LayoutDashboard,
    group: "Workspace",
    roles: adminRoles,
    description: "Your administration workspace, access and session details.",
    stage: 4,
    permission: "OVERVIEW_VIEW",
  },
  {
    id: "customers",
    name: "Customers",
    icon: Users,
    group: "Core banking",
    roles: staffRoles,
    description: "Manage individual and corporate customer relationships.",
    stage: 4,
    permission: "CUSTOMERS_VIEW",
  },
  {
    id: "transactions",
    name: "Transactions",
    icon: ArrowLeftRight,
    group: "Core banking",
    roles: staffRoles,
    description: "Review money movements, adjustments and reversals.",
    stage: 4,
    permission: "TRANSACTIONS_VIEW",
  },
  {
    id: "wallets",
    name: "Wallets",
    icon: Landmark,
    group: "Core banking",
    roles: staffRoles,
    description: "View customer wallets and manage wallet operations.",
    stage: 4,
    permission: "WALLETS_VIEW",
  },
  {
    id: "remittances",
    name: "Remittances",
    icon: Globe2,
    group: "Payments",
    roles: adminRoles,
    description: "Manage remittance sends and payout operations.",
    stage: 4,
    permission: "REMITTANCES_VIEW",
  },
  {
    id: "cash-out",
    name: "Cash out",
    icon: HandCoins,
    group: "Payments",
    roles: staffRoles,
    description: "Pay out a remittance using its collection code.",
    stage: 4,
    permission: "REMITTANCE_CASHOUT",
  },
  {
    id: "remittance-reports",
    name: "Remittance reports",
    icon: FileChartColumn,
    group: "Payments",
    roles: staffRoles,
    description: "Download regulatory, income, cash-out and teller reports.",
    stage: 4,
    permission: "REMITTANCE_REPORTS_VIEW",
  },
  {
    id: "credit-sales",
    name: "Credit sales",
    icon: BadgeDollarSign,
    group: "Payments",
    roles: staffRoles,
    description: "Track collecting agents and outstanding credit sales.",
    stage: 4,
    permission: "CREDIT_SALES_VIEW",
  },
  {
    id: "commissions",
    name: "Agent commissions",
    icon: BadgePercent,
    group: "Payments",
    roles: staffRoles,
    description: "Review agent earnings and commission summaries.",
    stage: 4,
    permission: "COMMISSIONS_VIEW",
  },
  {
    id: "users",
    name: "Users & access",
    icon: ShieldCheck,
    group: "Administration",
    roles: ["SUPER_ADMIN", "CORPORATE_ADMIN"] as Role[],
    description: "Manage team members and their assigned access.",
    stage: 4,
    permission: "USERS_MANAGE",
  },
  {
    id: "configuration",
    name: "Configuration",
    icon: Settings2,
    group: "Administration",
    roles: staffRoles,
    description: "Manage currencies, fees, billers and products.",
    stage: 4,
    permission: "CONFIGURATION_VIEW",
  },
  {
    id: "audit",
    name: "Audit trail",
    icon: Landmark,
    group: "Administration",
    roles: staffRoles,
    description: "Review administrative actions and their history.",
    stage: 4,
    permission: "AUDIT_VIEW",
  },
  {
    id: "expenses",
    name: "Expenses",
    icon: Receipt,
    group: "Administration",
    roles: staffRoles,
    description: "Record and review business expenses.",
    stage: 4,
    permission: "EXPENSES_MANAGE",
  },
] as const;
export type Workspace = (typeof workspaces)[number];
export function canAccess(id: string, roleOrUser: Role | User) {
  const workspace = workspaces.find((w) => w.id === id);
  if (!workspace) return false;
  const user = typeof roleOrUser === "string" ? null : roleOrUser;
  const role = user?.role ?? (roleOrUser as Role);
  if (user?.role === "SUPER_ADMIN") return true;
  if (user?.permissionsCustomized) {
    const grants = user.permissions ?? [];
    if (id === "users" && user.role !== "CORPORATE_ADMIN") return false;
    if (id === "wallets") return ["WALLETS_VIEW", "WALLET_MANAGE", "WALLET_DEPOSIT", "WALLET_WITHDRAW", "WALLET_SEND", "WALLET_ADJUST"].some((p) => grants.includes(p as Permission));
    if (id === "customers") return grants.includes("CUSTOMERS_VIEW") || grants.includes("CUSTOMERS_MANAGE");
    if (id === "remittances") return ["REMITTANCES_VIEW", "REMITTANCE_SEND", "REMITTANCE_CASHOUT"].some((p) => grants.includes(p as Permission));
    if (id === "configuration") return grants.includes("CONFIGURATION_VIEW") || grants.includes("CONFIGURATION_MANAGE");
    return !!workspace.permission && grants.includes(workspace.permission);
  }
  return workspace.roles.includes(role);
}

export function canPerform(user: User, permission: Permission) {
  if (user.role === "SUPER_ADMIN") return true;
  if (user.permissionsCustomized) return user.permissions?.includes(permission) ?? false;
  if (user.role === "OPERATIONS") return true;
  return user.role === "CORPORATE_ADMIN" && permission === "REMITTANCE_SEND";
}
