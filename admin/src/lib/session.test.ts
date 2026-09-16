import { beforeEach, afterEach, describe, it, expect, vi } from "vitest";
const data = {
  accessToken: "access-one",
  expiresIn: 900,
  user: {
    id: "user-one",
    customerId: null,
    username: "admin",
    role: "SUPER_ADMIN",
    status: "ACTIVE",
  },
};
const response = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
beforeEach(() => {
  vi.resetModules();
  vi.stubGlobal("BroadcastChannel", undefined);
});
afterEach(() => vi.unstubAllGlobals());
describe("in-memory session and request client", () => {
  it("stores neither access nor refresh tokens in browser storage", async () => {
    const setLocal = vi.spyOn(Storage.prototype, "setItem");
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response(data)));
    const auth = await import("./session");
    await auth.signIn("admin", "password");
    expect(auth.getSession().session?.accessToken).toBe("access-one");
    expect(setLocal).not.toHaveBeenCalled();
    expect(fetch).toHaveBeenCalledWith(
      "/api/auth/browser/login",
      expect.objectContaining({ credentials: "same-origin", method: "POST" }),
    );
  });
  it("shares one refresh across simultaneous callers", async () => {
    const fetcher = vi.fn().mockImplementation(async () => response(data));
    vi.stubGlobal("fetch", fetcher);
    const auth = await import("./session");
    await Promise.all([
      auth.restoreSession(),
      auth.restoreSession(),
      auth.restoreSession(),
    ]);
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(auth.getSession().phase).toBe("authenticated");
  });
  it("retries an expired bearer request once after refreshing", async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(response(data))
      .mockResolvedValueOnce(response({ code: "UNAUTHORIZED" }, 401))
      .mockResolvedValueOnce(response({ ...data, accessToken: "access-two" }))
      .mockResolvedValueOnce(response(data.user));
    vi.stubGlobal("fetch", fetcher);
    const auth = await import("./session");
    await auth.signIn("admin", "password");
    expect(await auth.api("/api/auth/me")).toEqual(data.user);
    expect(fetcher).toHaveBeenLastCalledWith(
      "/api/auth/me",
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer access-two",
        }),
      }),
    );
    expect(fetcher).toHaveBeenCalledTimes(4);
  });
  it("does not log a user out merely because refresh cannot reach the server", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(response(data))
        .mockRejectedValueOnce(new TypeError("offline")),
    );
    const auth = await import("./session");
    await auth.signIn("admin", "password");
    await expect(auth.restoreSession()).rejects.toThrow("could not reach");
    expect(auth.getSession().phase).toBe("authenticated");
  });
  it("clears an expired session when refresh is rejected", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(response(data))
        .mockResolvedValueOnce(response({}, 401)),
    );
    const auth = await import("./session");
    await auth.signIn("admin", "password");
    await auth.restoreSession();
    expect(auth.getSession().phase).toBe("anonymous");
    expect(auth.getSession().session).toBeNull();
  });
  it("clears memory only after confirmed sign-out", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(response(data))
        .mockResolvedValueOnce(response(null)),
    );
    const auth = await import("./session");
    await auth.signIn("admin", "password");
    await auth.signOut();
    expect(auth.getSession().session).toBeNull();
  });
});
