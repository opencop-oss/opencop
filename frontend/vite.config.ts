// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { execSync } from "child_process";
import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  console.log(`mode=${mode},NODE_ENV=${process.env.NODE_ENV}`);

  let buildSha = process.env.BUILD_GIT_SHA || "unknown";
  if (buildSha === "unknown") {
    try {
      buildSha = execSync("git rev-parse --short HEAD", {
        stdio: ["pipe", "pipe", "pipe"],
        encoding: "utf-8",
      }).trim();
    } catch {
      // git unavailable — fallback to "unknown"
    }
  }
  // Expose to import.meta.env via Vite's env loader; defining
  // `import.meta.env.VITE_BUILD_SHA` directly under `define` is unreliable
  // because Vite's own env replacement runs after define.
  process.env.VITE_BUILD_SHA = buildSha;

  const appVersion = process.env.APP_VERSION ?? "";
  delete process.env.VITE_APP_VERSION;
  if (/^\d+\.\d+\.\d+$/.test(appVersion)) {
    process.env.VITE_APP_VERSION = appVersion;
  }

  return {
    plugins: [
      react(),
      {
        name: "opencop-index-html-build-comment",
        transformIndexHtml(html) {
          const parts: string[] = [];
          if (process.env.VITE_APP_VERSION) {
            parts.push(`appVersion: ${process.env.VITE_APP_VERSION}`);
          }
          parts.push(`build: ${buildSha}`);

          return html.replace(
            "</head>",
            `  <!-- opencop-web ${parts.join(", ")} -->\n  </head>`,
          );
        },
      },
    ],
    define: {
      global: "globalThis",
    },
    resolve: {
      alias: {
        "~": new URL("./packages/app/src", import.meta.url).pathname,
        "@opencop/proto": new URL("./packages/proto/src/index.ts", import.meta.url).pathname,
        "@opencop/symbology": new URL("./packages/symbology/src/index.ts", import.meta.url).pathname,
        "@opencop/vec": new URL("./packages/vec/src/index.ts", import.meta.url).pathname,
        "@opencop/elements": new URL("./packages/elements/src/index.ts", import.meta.url).pathname,
        "@opencop/api": new URL("./packages/api/src/index.ts", import.meta.url).pathname,
        "@opencop/websocket": new URL("./packages/websocket/src/index.ts", import.meta.url).pathname,
        "@opencop/ui": new URL("./packages/ui/src/index.ts", import.meta.url).pathname,
        "@opencop/ingest": new URL("./packages/ingest/src/index.ts", import.meta.url).pathname,
        "@opencop/layers": new URL("./packages/layers/src/index.ts", import.meta.url).pathname,
        "@opencop/tools": new URL("./packages/tools/src/index.ts", import.meta.url).pathname,
        "@opencop/contributions/plugin": new URL("./packages/contributions/src/plugin/index.ts", import.meta.url).pathname,
        "@opencop/contributions/host": new URL("./packages/contributions/src/host/index.ts", import.meta.url).pathname,
        "@opencop/contributions/validation": new URL("./packages/contributions/src/validation/index.ts", import.meta.url).pathname,
        "@opencop/contributions/containment": new URL("./packages/contributions/src/containment/index.ts", import.meta.url).pathname,
        "@opencop/contributions": new URL("./packages/contributions/src/index.ts", import.meta.url)
          .pathname,
        "@opencop/utils": new URL("./packages/utils/src/index.ts", import.meta.url).pathname,
        "@opencop/plugin-sdk/testing": new URL("./packages/plugin-sdk/src/testing/index.ts", import.meta.url).pathname,
        "@opencop/plugin-sdk": new URL("./packages/plugin-sdk/src/index.ts", import.meta.url)
          .pathname,
        "@opencop/symbology-plugin": new URL(
          "./packages/symbology-plugin/src/index.ts",
          import.meta.url,
        ).pathname,
        "@opencop/scene": new URL("./packages/scene/src/index.ts", import.meta.url).pathname,
        "@opencop/textbox-plugin": new URL(
          "./packages/textbox-plugin/src/index.ts",
          import.meta.url,
        ).pathname,
      },
    },
    ...(mode === "development" &&
      (() => {
        // by default `VITE_*` env vars are not available here but we need them for the `target`s
        const env = loadEnv(mode, process.cwd(), "");

        // Default: route all proxied requests through one shared target.
        // Override: set VITE_DEV_PROXY_TARGET_CORE or _WS in .env.development.local
        //           to route those requests directly to locally running services.
        const defaultTarget = env.VITE_DEV_PROXY_TARGET;
        const coreTarget = env.VITE_DEV_PROXY_TARGET_CORE || defaultTarget;
        const wsTarget = env.VITE_DEV_PROXY_TARGET_WS || defaultTarget;

        // A direct core target receives the path without the shared /api prefix.
        const stripApiPrefix = coreTarget !== defaultTarget;

        return {
          server: {
            cors: false,
            // Listen on all addresses (dual-stack) so the dev server is reachable
            // over both IPv4 (127.0.0.1) and IPv6 (::1) loopback. Vite's default
            // binds IPv6-only here, which breaks IPv4-only clients (automation
            // browsers / curl that resolve `localhost` → 127.0.0.1).
            host: true,
            port: 3000,
            proxy: {
              "/api": {
                target: coreTarget,
                ...(stripApiPrefix && {
                  rewrite: (path: string) => path.replace(/^\/api/, ""),
                  // Preserve the removed route prefix so the upstream can build routed redirects.
                  configure: (proxy) => {
                    proxy.on("proxyReq", (proxyReq) => {
                      proxyReq.setHeader("X-Forwarded-Prefix", "/api");
                    });
                  },
                }),
              },
              "/ws": {
                target: wsTarget,
                ws: true,
              },
              "/auth": {
                target: defaultTarget,
                changeOrigin: true,
              },
            },
          },
        };
      })()),
  };
});
