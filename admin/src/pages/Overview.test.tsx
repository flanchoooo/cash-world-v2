import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, it, vi } from "vitest";
import { Overview } from "./Overview";
import type { Session } from "../lib/session";
const session: Session = {
  accessToken: "test",
  expiresAt: Date.now() + 900000,
  expiresIn: 900,
  user: {
    id: "123",
    username: "operator",
    customerId: null,
    role: "SUPER_ADMIN",
    status: "ACTIVE",
  },
};
it("filters actual workspace metadata without inventing financial records", async () => {
  const user = userEvent.setup();
  render(
    <Overview
      session={session}
      remaining="14:59"
      connected
      onProfile={vi.fn()}
    />,
  );
  expect(screen.getByText("Welcome back, operator.")).toBeInTheDocument();
  await user.type(screen.getByLabelText("Search workspaces"), "Remittance");
  const table = screen.getByRole("table");
  expect(within(table).getByText("Remittances")).toBeInTheDocument();
  expect(within(table).queryByText("Customers")).not.toBeInTheDocument();
  expect(screen.getByText("1–1 of 1 workspaces")).toBeInTheDocument();
  await user.clear(screen.getByLabelText("Search workspaces"));
  await user.selectOptions(screen.getByLabelText("Filter by area"), "Payments");
  expect(screen.getByText("1–3 of 3 workspaces")).toBeInTheDocument();
});
it("supports pagination and accessible workspace detail panels", async () => {
  const user = userEvent.setup();
  render(
    <Overview
      session={session}
      remaining="14:59"
      connected
      onProfile={vi.fn()}
    />,
  );
  await user.click(screen.getByRole("button", { name: "Next page" }));
  expect(screen.getByText("6–8 of 8 workspaces")).toBeInTheDocument();
  await user.click(
    screen.getByRole("button", { name: "View Configuration details" }),
  );
  expect(
    screen.getByRole("dialog", { name: "Configuration" }),
  ).toBeInTheDocument();
  expect(
    within(screen.getByRole("dialog", { name: "Configuration" })).getByText(
      "Available",
    ),
  ).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Close dialog" }));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
});
it("keeps restricted modules out of a corporate workspace", () => {
  render(
    <Overview
      session={{
        ...session,
        user: { ...session.user, role: "CORPORATE_ADMIN" },
      }}
      remaining="14:59"
      connected
      onProfile={vi.fn()}
    />,
  );
  expect(
    screen.queryByRole("button", { name: "Customers" }),
  ).not.toBeInTheDocument();
  expect(
    screen.getByRole("button", { name: "Users & access" }),
  ).toBeInTheDocument();
});
