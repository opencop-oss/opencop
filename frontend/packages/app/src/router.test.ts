// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * Guards the one route that must never be flag-gated.
 *
 * `/admin/runtime-features` is the only surface that can change a runtime feature's audience: the
 * endpoint has no delete verb and Swagger is not deployed in production. Wrapping the route in
 * `FeatureFlaggedRoute` would let an admin set that flag to `NONE` and permanently lose the surface
 * that could set it back.
 *
 * This reads the router as source rather than mounting it. Importing `~/router` pulls in `MapView`,
 * whose `maplibre-gl/dist/maplibre-gl.css` import does not resolve against the global maplibre test
 * stub — so the behavioural half of this invariant lives in `AdminRuntimeFeaturesView.test.tsx`,
 * which renders the page with every flag disabled. This half catches the wrapper.
 */
describe("router route protection", () => {
    it("leaves /admin/runtime-features role-gated and never flag-gated", () => {
        // Vitest runs with the cwd at the package root, and `import.meta.url` is not a file URL
        // in a transformed module.
        const source = readFileSync(resolve("packages/app/src/router.tsx"), "utf-8");
        const start = source.indexOf('path: "runtime-features"');
        expect(start).toBeGreaterThan(-1);

        // The route object this path belongs to, up to the closing brace of its `handle`.
        const route = source.slice(start, source.indexOf("},", source.indexOf("handle", start)));

        expect(route).toContain("element: <AdminRuntimeFeaturesView />");
        expect(route).not.toContain("FeatureFlagged");
    });
});
