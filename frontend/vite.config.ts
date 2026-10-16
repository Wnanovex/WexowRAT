import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import path from "node:path";

export default defineConfig({
  base: "/app/",
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    port: 3000,
    host: "0.0.0.0",
    // Allow the sandbox preview gateway (and any external host) to reach the
    // dev server. Without this, Vite blocks the preview with
    // "This host is not allowed".
    allowedHosts: true,
    proxy: {
      "/api": {
        target: "http://localhost:3000",
        changeOrigin: true,
      },
      "/socket.io": {
        target: "http://localhost:3000",
        changeOrigin: true,
        ws: true,
      },
      "/Downloads": {
        target: "http://localhost:3000",
        changeOrigin: true,
      },
      "/output": {
        target: "http://localhost:3000",
        changeOrigin: true,
      },
      build: {
        outDir: "../server/public/app",
        emptyOutDir: true,
      },
    },
  },
});
