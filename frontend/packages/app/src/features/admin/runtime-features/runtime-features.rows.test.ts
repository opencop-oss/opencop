// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { RuntimeFeature } from "@opencop/api";
import { describe, expect, it } from "vitest";
import { buildRuntimeFeatureRows, computeFlagDrift } from "./runtime-features.rows";

function feature(overrides: Partial<RuntimeFeature> & { name: string }): RuntimeFeature {
    return {
        consumerClass: "FRONTEND_ONLY",
        audience: { mode: "NONE" },
        revision: 0,
        updatedAt: "2026-08-05T00:00:00Z",
        ...overrides,
    };
}

function statusOf(rows: ReturnType<typeof buildRuntimeFeatureRows>, name: string) {
    return rows.find((row) => row.name === name)?.status;
}

describe("buildRuntimeFeatureRows", () => {
    it("separates a switched-on declared flag from a switched-off one", () => {
        // A green "Active" chip on a flag whose audience is NONE would claim the feature is
        // reaching someone when it reaches nobody.
        const rows = buildRuntimeFeatureRows([
            feature({ name: "themes-page", audience: { mode: "ALL" } }),
            feature({ name: "rtsp", audience: { mode: "NONE" } }),
        ]);

        expect(statusOf(rows, "themes-page")).toBe("active");
        expect(statusOf(rows, "rtsp")).toBe("inactive");
    });

    it("counts a user-group audience as active, since it reaches someone", () => {
        const rows = buildRuntimeFeatureRows([
            feature({
                name: "themes-page",
                audience: {
                    mode: "USER_GROUPS",
                    userGroupIds: ["11111111-1111-4111-8111-111111111111"],
                },
            }),
        ]);

        expect(statusOf(rows, "themes-page")).toBe("active");
    });

    it("counts a user-group audience targeting nobody as inactive", () => {
        // The server rejects an empty target list and the drawer disables Save without a
        // selection, so this should be unreachable — but the table renders whatever the catalog
        // returns, and "Active" on a row reaching no one is the exact lie the split prevents.
        const rows = buildRuntimeFeatureRows([
            feature({ name: "themes-page", audience: { mode: "USER_GROUPS", userGroupIds: [] } }),
        ]);

        expect(statusOf(rows, "themes-page")).toBe("inactive");
    });

    it("surfaces a declared flag that has no catalog row as unsynced", () => {
        const rows = buildRuntimeFeatureRows([]);

        expect(rows.map((row) => [row.name, row.status])).toEqual([
            ["rtsp", "unsynced"],
            ["themes-page", "unsynced"],
        ]);
        expect(rows.every((row) => row.record === undefined)).toBe(true);
    });

    it("marks a frontend row this build no longer declares as archived, even when enabled", () => {
        // Archived describes the manifest relationship, never the row's runtime effect — an
        // archived flag can still be switched on, which is why the status outranks enablement
        // rather than being merged with it.
        const rows = buildRuntimeFeatureRows([
            feature({ name: "legacy-flag", audience: { mode: "ALL" } }),
        ]);

        expect(statusOf(rows, "legacy-flag")).toBe("archived");
        expect(rows.find((row) => row.name === "legacy-flag")?.record?.audience).toEqual({
            mode: "ALL",
        });
    });

    it("judges a backend flag on its audience, never archiving it", () => {
        // The frontend manifest is not the owner of a backend flag, so its silence says nothing
        // about whether the flag is in use. Only frontend flags can go archived.
        const rows = buildRuntimeFeatureRows([
            feature({ name: "watchbox", consumerClass: "BACKEND_ENFORCED" }),
            feature({
                name: "other-backend",
                consumerClass: "BACKEND_ENFORCED",
                audience: { mode: "ALL" },
            }),
        ]);

        expect(statusOf(rows, "watchbox")).toBe("inactive");
        expect(statusOf(rows, "other-backend")).toBe("active");
    });

    it("flags a declared name owned by a backend-enforced row as a conflict", () => {
        const rows = buildRuntimeFeatureRows([
            feature({ name: "themes-page", consumerClass: "BACKEND_ENFORCED" }),
        ]);

        expect(statusOf(rows, "themes-page")).toBe("conflict");
    });
});

describe("computeFlagDrift", () => {
    it("reports nothing to do once every declared flag is registered", () => {
        const rows = buildRuntimeFeatureRows([
            feature({ name: "themes-page" }),
            feature({ name: "rtsp" }),
        ]);

        expect(computeFlagDrift(rows)).toEqual({ missing: [], classConflicts: [] });
    });

    it("separates syncable gaps from conflicts a sync cannot resolve", () => {
        const rows = buildRuntimeFeatureRows([
            feature({ name: "rtsp", consumerClass: "BACKEND_ENFORCED" }),
        ]);

        const drift = computeFlagDrift(rows);

        expect(drift.missing).toEqual(["themes-page"]);
        expect(drift.classConflicts).toEqual(["rtsp"]);
    });

    it("treats neither an archived flag nor a backend flag as drift", () => {
        const rows = buildRuntimeFeatureRows([
            feature({ name: "themes-page" }),
            feature({ name: "rtsp" }),
            feature({ name: "legacy-flag" }),
            feature({ name: "watchbox", consumerClass: "BACKEND_ENFORCED" }),
        ]);

        expect(computeFlagDrift(rows)).toEqual({ missing: [], classConflicts: [] });
    });
});
