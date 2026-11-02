// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { defineConfig } from "vitest/config";

export default defineConfig({
    test: {
        environment: "happy-dom",
        globals: true,
        setupFiles: ["./packages/app/src/test/setup.ts"],
        // Multi-step userEvent wizard flows (e.g. AddDataSourceDialog) run ~1.5s locally but
        // reach ~5s on the shared CI runner, so vitest's 5s default turns them into flakes —
        // and a timed-out test keeps driving the DOM, cascading into the tests after it.
        // A contended runner can stretch far past that: sub-2s tests such as
        // IntegrationConnectionFlow have hit 20s in consecutive test:unit runs.
        // Wide enough to absorb that, still narrow enough to catch a real hang.
        testTimeout: 60000,
        // scripts/*.test.mjs are node:test files (run via `node --test`, e.g.
        // junit-wrap.test.mjs) — not vitest suites; keep them out of `npm test`.
        exclude: [
            "**/node_modules/**",
            "**/dist/**",
            "**/e2e/**",
            "**/*.browser.test.ts",
            "scripts/**",
        ],
        env: {
            VITE_KEYCLOAK_URL: "http://localhost:8070",
            VITE_OPENCOP_CORE_API_URL: "http://localhost/api",
            VITE_WEBSOCKET_URL: "ws://localhost:8083",
        },
    },
    resolve: {
        alias: {
            "~": new URL("./packages/app/src", import.meta.url).pathname,
            "@opencop/proto": new URL("./packages/proto/src/index.ts", import.meta.url).pathname,
            "@opencop/scene": new URL("./packages/scene/src/index.ts", import.meta.url).pathname,
            "@opencop/symbology": new URL("./packages/symbology/src/index.ts", import.meta.url)
                .pathname,
            "@opencop/vec": new URL("./packages/vec/src/index.ts", import.meta.url).pathname,
            "@opencop/elements": new URL("./packages/elements/src/index.ts", import.meta.url)
                .pathname,
            "@opencop/api": new URL("./packages/api/src/index.ts", import.meta.url).pathname,
            // Stub the websocket client so the real provider chain mounts without
            // opening a live socket (see packages/app/src/test/websocket-mock.ts).
            // This alias is global: any test in `packages/websocket` MUST import
            // `./OCMPClient` relatively (not via the `@opencop/websocket` barrel) or
            // it will get the no-op stub instead of the real class.
            "@opencop/websocket": new URL(
                "./packages/app/src/test/websocket-mock.ts",
                import.meta.url,
            ).pathname,
            "@opencop/ui": new URL("./packages/ui/src/index.ts", import.meta.url).pathname,
            "@opencop/ingest": new URL("./packages/ingest/src/index.ts", import.meta.url).pathname,
            "@opencop/layers": new URL("./packages/layers/src/index.ts", import.meta.url).pathname,
            "@opencop/tools": new URL("./packages/tools/src/index.ts", import.meta.url).pathname,
            "@opencop/contributions/plugin": new URL(
                "./packages/contributions/src/plugin/index.ts",
                import.meta.url,
            ).pathname,
            "@opencop/contributions/host": new URL(
                "./packages/contributions/src/host/index.ts",
                import.meta.url,
            ).pathname,
            "@opencop/contributions/validation": new URL(
                "./packages/contributions/src/validation/index.ts",
                import.meta.url,
            ).pathname,
            "@opencop/contributions/containment": new URL(
                "./packages/contributions/src/containment/index.ts",
                import.meta.url,
            ).pathname,
            "@opencop/contributions": new URL(
                "./packages/contributions/src/index.ts",
                import.meta.url,
            ).pathname,
            "@opencop/utils": new URL("./packages/utils/src/index.ts", import.meta.url).pathname,
            "@opencop/plugin-sdk/testing": new URL(
                "./packages/plugin-sdk/src/testing/index.ts",
                import.meta.url,
            ).pathname,
            "@opencop/plugin-sdk": new URL("./packages/plugin-sdk/src/index.ts", import.meta.url)
                .pathname,
            "@opencop/symbology-plugin": new URL(
                "./packages/symbology-plugin/src/index.ts",
                import.meta.url,
            ).pathname,
            "@opencop/textbox-plugin": new URL(
                "./packages/textbox-plugin/src/index.ts",
                import.meta.url,
            ).pathname,
            "maplibre-gl": new URL(
                "./packages/app/src/features/map/maplibre-gl-mock.ts",
                import.meta.url,
            ).pathname,
            // happy-dom has no layout, so the real virtualizer measures a zero-height
            // scroll element and renders nothing. Stub it globally so any test that
            // mounts a VirtualList/ResourceTable sees rows without its own vi.mock
            // (see packages/app/src/test/react-virtual-mock.ts). Deliberately absent
            // from vitest.browser.config.ts — browser mode has real layout.
            "@tanstack/react-virtual": new URL(
                "./packages/app/src/test/react-virtual-mock.ts",
                import.meta.url,
            ).pathname,
        },
    },
});
