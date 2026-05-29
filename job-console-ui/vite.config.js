import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api/scheduler": {
        target: "http://localhost:8087",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/scheduler/, "")
      },
      "/api/worker": {
        target: "http://localhost:8088",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/worker/, "")
      }
    }
  }
});
