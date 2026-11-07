import { resolve } from "node:path";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig, type Plugin } from "vite";

/** Dev only: serve admin/index.html for client-side routes under /admin/*. */
function adminSpaFallback(): Plugin {
  return {
    name: "admin-spa-fallback",
    configureServer(server) {
      server.middlewares.use((req, _res, next) => {
        const url = req.url ?? "";
        if (/^\/admin(\/[^.]*)?(\?.*)?$/.test(url)) req.url = "/admin/index.html";
        next();
      });
    },
  };
}

export default defineConfig({
  plugins: [react(), tailwindcss(), adminSpaFallback()],
  build: {
    rolldownOptions: {
      input: {
        main: resolve(import.meta.dirname, "index.html"),
        admin: resolve(import.meta.dirname, "admin/index.html"),
      },
    },
  },
  server: {
    host: "0.0.0.0",
    port: 5173,
    proxy: {
      "/api": "http://localhost:3000",
    },
  },
});
