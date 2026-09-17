import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  return {
    plugins: [react(), tailwindcss()],
    server: {
      port: 5173,
      strictPort: true,
      proxy: {
        "/api": {
          target: env.BACKEND_URL || "http://localhost:8088",
          changeOrigin: false,
        },
        "/actuator/health": {
          target: env.BACKEND_URL || "http://localhost:8088",
        },
      },
    },
    preview: {
      port: 5173,
      strictPort: true,
      proxy: {
        "/api": {
          target: env.BACKEND_URL || "http://localhost:8088",
          changeOrigin: false,
        },
      },
    },
  };
});
