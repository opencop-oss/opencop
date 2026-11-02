// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { defineConfig } from "vitest/config";
import { playwright } from "@vitest/browser-playwright";

export default defineConfig({
    optimizeDeps: {
        include: [
            "react/jsx-dev-runtime",
            "wgsl_reflect",
            "@luma.gl/core",
            "@luma.gl/engine",
            "@luma.gl/webgl",
            "@luma.gl/webgpu",
            "@luma.gl/shadertools",
            "@deck.gl/core",
            "@deck.gl/layers",
            "@deck.gl/test-utils",
            "@deck.gl/geo-layers",
            "@loaders.gl/wms",
            "@loaders.gl/loader-utils",
        ],
    },
    test: {
        globals: true,
        env: {
            VITE_KEYCLOAK_URL: "http://localhost:8070",
            VITE_OPENCOP_CORE_API_URL: "http://localhost/api",
            VITE_WEBSOCKET_URL: "ws://localhost:8083",
        },
        // Browser-mode tests use the `.browser.test.ts` suffix to keep them
        // out of the fast happy-dom suite. Add this suffix to any test that
        // needs a real DOM / canvas / WebGL context (i.e. anything using
        // `@deck.gl/test-utils`).
        include: ["packages/**/*.browser.test.ts"],
        exclude: ["**/node_modules/**", "**/dist/**", "**/e2e/**"],
        browser: {
            enabled: true,
            provider: playwright(),
            screenshotFailures: false,
            headless: true,
            instances: [{ browser: "chromium" }],
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
            "@opencop/websocket": new URL("./packages/websocket/src/index.ts", import.meta.url)
                .pathname,
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
            "@opencop/textbox-plugin": new URL(
                "./packages/textbox-plugin/src/index.ts",
                import.meta.url,
            ).pathname,
            "maplibre-gl-real": new URL(
                "./node_modules/maplibre-gl/dist/maplibre-gl.js",
                import.meta.url,
            ).pathname,
            "maplibre-gl": new URL(
                "./packages/app/src/features/map/maplibre-gl-mock.ts",
                import.meta.url,
            ).pathname,
        },
    },
});
