import { describe, it, expect } from "vitest";
import { adminRoles, canAccess, canPerform } from "./workspaces";
import type { Permission } from "./session";
describe("administration role boundaries", () => {
  it("does not admit retail roles", () => {
    expect(adminRoles).not.toContain("CUSTOMER");
    expect(adminRoles).not.toContain("AGENT");
    expect(canAccess("overview", "CORPORATE_USER")).toBe(false);
  });
  it("limits corporate administrators to their workspaces", () => {
    expect(canAccess("users", "CORPORATE_ADMIN")).toBe(true);
    expect(canAccess("remittances", "CORPORATE_ADMIN")).toBe(true);
    expect(canAccess("wallets", "CORPORATE_ADMIN")).toBe(false);
    expect(canAccess("customers", "CORPORATE_ADMIN")).toBe(false);
    expect(canAccess("configuration", "CORPORATE_ADMIN")).toBe(false);
  });
  it("restricts user management for operations", () => {
    expect(canAccess("transactions", "OPERATIONS")).toBe(true);
    expect(canAccess("users", "OPERATIONS")).toBe(false);
    expect(canAccess("users", "SUPER_ADMIN")).toBe(true);
    expect(canAccess("missing", "SUPER_ADMIN")).toBe(false);
  });
  it("limits customized operators to their assigned workspaces and actions", () => {
    const user = {
      id: "operator-1",
      customerId: null,
      username: "cashier",
      role: "OPERATIONS" as const,
      status: "ACTIVE" as const,
      permissionsCustomized: true,
      permissions: ["WALLET_SEND", "REMITTANCE_CASHOUT"] as Permission[],
    };
    expect(canAccess("wallets", user)).toBe(true);
    expect(canAccess("remittances", user)).toBe(true);
    expect(canAccess("transactions", user)).toBe(false);
    expect(canAccess("configuration", user)).toBe(false);
    expect(canPerform(user, "WALLET_SEND")).toBe(true);
    expect(canPerform(user, "WALLET_DEPOSIT")).toBe(false);
    expect(canPerform(user, "REMITTANCE_CASHOUT")).toBe(true);
    expect(canPerform(user, "REMITTANCE_SEND")).toBe(false);
  });
});
