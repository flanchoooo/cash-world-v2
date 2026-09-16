import { describe, it, expect } from "vitest";
import { adminRoles, canAccess } from "./workspaces";
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
});
