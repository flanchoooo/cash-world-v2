import { webcrypto } from "node:crypto";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ActionDialog } from "./Operations";
import { api } from "../lib/session";
import type { User } from "../lib/session";
vi.mock("../lib/session", () => ({ api: vi.fn() }));
const user: User = {
  id: "operator-id",
  username: "operator",
  role: "SUPER_ADMIN",
  status: "ACTIVE",
  customerId: null,
};
beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  Object.defineProperty(globalThis.crypto, "subtle", {
    configurable: true,
    value: webcrypto.subtle,
  });
});
describe("financial operation review", () => {
  it("requires review and keeps decimal input as a string", async () => {
    vi.mocked(api).mockResolvedValue({
      transactionReference: "TX-1",
      status: "SUCCESS",
    });
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ActionDialog
          user={user}
          action={{
            title: "Deposit",
            path: "/api/wallets/deposit",
            fields: [
              {
                name: "amount",
                label: "Amount",
                type: "money",
                required: true,
              },
            ],
            financial: false,
          }}
          onClose={vi.fn()}
          onDone={vi.fn()}
        />
      </QueryClientProvider>,
    );
    const u = userEvent.setup();
    await u.type(screen.getByLabelText("Amount"), "900719925474.12");
    await u.click(screen.getByRole("button", { name: "Review" }));
    expect(api).not.toHaveBeenCalled();
    expect(screen.getByText("900719925474.12")).toBeInTheDocument();
    await u.click(screen.getByRole("button", { name: "Confirm" }));
    expect(api).toHaveBeenCalledWith(
      "/api/wallets/deposit",
      expect.objectContaining({ body: '{"amount":"900719925474.12"}' }),
    );
    expect(await screen.findByText("TX-1")).toBeInTheDocument();
  });
  it("retains the same payload when retrying an uncertain result", async () => {
    vi.mocked(api)
      .mockRejectedValueOnce(new Error("Connection lost"))
      .mockResolvedValue({ status: "SUCCESS" });
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ActionDialog
          user={user}
          action={{
            title: "Operation",
            path: "/api/test",
            fields: [],
            financial: true,
          }}
          onClose={vi.fn()}
          onDone={vi.fn()}
        />
      </QueryClientProvider>,
    );
    const u = userEvent.setup();
    await u.click(screen.getByRole("button", { name: "Review" }));
    await u.click(screen.getByRole("button", { name: "Confirm" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Connection lost",
    );
    await u.click(screen.getByRole("button", { name: "Retry same request" }));
    expect(vi.mocked(api).mock.calls[1]).toEqual(vi.mocked(api).mock.calls[0]);
  });

  it("shows the calculated quote before confirming a remittance", async () => {
    vi.mocked(api)
      .mockResolvedValueOnce({ feeAmount: "2.00", totalToCollect: "22.00" })
      .mockResolvedValueOnce({
        status: "AVAILABLE_FOR_PAYOUT",
        remittanceReference: "REM-1",
        collectionCode: "123456",
      });
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ActionDialog
          user={user}
          action={{
            title: "Send remittance",
            path: "/api/remittances/send",
            quotePath: "/api/remittances/quote",
            fields: [
              {
                name: "amount",
                label: "Amount",
                type: "money",
                required: true,
              },
            ],
            financial: true,
          }}
          onClose={vi.fn()}
          onDone={vi.fn()}
        />
      </QueryClientProvider>,
    );
    const u = userEvent.setup();
    await u.type(screen.getByLabelText("Amount"), "20");
    await u.click(screen.getByRole("button", { name: "Review" }));
    expect(await screen.findByText("22.00")).toBeInTheDocument();
    expect(api).toHaveBeenNthCalledWith(
      1,
      "/api/remittances/quote",
      expect.objectContaining({ body: '{"amount":"20"}' }),
    );
    await u.click(screen.getByRole("button", { name: "Confirm" }));
    expect(await screen.findByText("123456")).toBeInTheDocument();
  });
});

it("preserves an empty optional effective date when editing a rule", async () => {
  vi.mocked(api).mockResolvedValue({ status: "ACTIVE" });
  render(
    <QueryClientProvider client={new QueryClient()}>
      <ActionDialog
        user={user}
        action={{
          title: "Edit rule",
          path: "/api/test",
          fields: [
            {
              name: "effectiveTo",
              label: "Effective to",
              type: "datetime-local",
              required: false,
            },
          ],
          initial: { effectiveTo: null },
        }}
        onClose={vi.fn()}
        onDone={vi.fn()}
      />
    </QueryClientProvider>,
  );
  const u = userEvent.setup();
  await u.click(screen.getByRole("button", { name: "Review" }));
  await u.click(screen.getByRole("button", { name: "Confirm" }));
  expect(api).toHaveBeenCalledWith(
    "/api/test",
    expect.objectContaining({ body: '{"effectiveTo":null}' }),
  );
});
