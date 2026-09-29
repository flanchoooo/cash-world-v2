import { defineConfig, loadEnv, type ProxyOptions } from "vite";
import { TLSSocket } from "node:tls";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const backendUrl = env.BACKEND_URL || "http://127.0.0.1:8011";
  const debugProxy =
    env.PROXY_DEBUG === "1" ||
    (mode === "development" && env.PROXY_DEBUG !== "0");

  const firstHeader = (value: string | string[] | undefined) =>
    (Array.isArray(value) ? value[0] : value)?.split(",")[0]?.trim();

  const apiProxy = (): ProxyOptions => ({
    target: backendUrl,
    changeOrigin: false,
    configure(proxy) {
      proxy.on("proxyReq", (proxyRequest, request) => {
        const host =
          firstHeader(request.headers["x-forwarded-host"]) ||
          firstHeader(request.headers.host);
        const forwardedProto = firstHeader(
          request.headers["x-forwarded-proto"],
        );
        let protocol =
          forwardedProto === "https"
            ? "https"
            : request.socket instanceof TLSSocket
              ? "https"
              : "http";
        const origin = firstHeader(request.headers.origin);

        // Tunnels that retain Host but omit X-Forwarded-Proto still need the public scheme.
        if (!forwardedProto && origin && host) {
          try {
            const browserOrigin = new URL(origin);
            if (browserOrigin.host.toLowerCase() === host.toLowerCase()) {
              protocol = browserOrigin.protocol === "https:" ? "https" : "http";
            }
          } catch {
            // Spring will reject a malformed Origin.
          }
        }

        if (host) {
          proxyRequest.setHeader("Host", host);
          proxyRequest.setHeader("X-Forwarded-Host", host);
        }
        proxyRequest.setHeader("X-Forwarded-Proto", protocol);

        if (debugProxy) {
          const headers = Object.fromEntries(
            Object.entries(request.headers).map(([name, value]) => [
              name,
              /authorization|cookie|token|secret|key/i.test(name)
                ? "[redacted]"
                : value,
            ]),
          );
          console.info("[api proxy] request", {
            method: request.method,
            path: request.url,
            target: backendUrl,
            headers,
            forwardedHost: host,
            forwardedProto: protocol,
          });
        }
      });
      proxy.on("proxyRes", (response, request) => {
        if (debugProxy) {
          console.info("[api proxy] response", {
            method: request.method,
            path: request.url,
            status: response.statusCode,
          });
        }
      });
      proxy.on("error", (error, request) => {
        console.error("[api proxy] error", {
          method: request.method,
          path: request.url,
          target: backendUrl,
          message: error.message,
        });
      });
    },
  });

  return {
    plugins: [react(), tailwindcss()],
    server: {
      port: 5173,
      strictPort: true,
      allowedHosts: true,
      proxy: {
        "/api": apiProxy(),
        "/actuator/health": apiProxy(),
      },
    },
    preview: {
      port: 5173,
      strictPort: true,
      allowedHosts: true,
      proxy: {
        "/api": apiProxy(),
        "/actuator/health": apiProxy(),
      },
    },
  };
});
