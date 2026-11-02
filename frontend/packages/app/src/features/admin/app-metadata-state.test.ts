// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { AppMetadata } from "@opencop/api";
import { describe, expect, it } from "vitest";
import {
    compareVersions,
    formatBuildIdentity,
    getBuildFooterDisplayState,
    isRuntimeFeatureEnabled,
} from "./app-metadata-state";

describe("isRuntimeFeatureEnabled", () => {
    it("requires an enabled entry with the requested name", () => {
        expect(isRuntimeFeatureEnabled([{ name: "rtsp", enabled: true }], "rtsp")).toBe(true);
        expect(isRuntimeFeatureEnabled([{ name: "rtsp", enabled: false }], "rtsp")).toBe(false);
        expect(isRuntimeFeatureEnabled([{ name: "watchbox", enabled: true }], "rtsp")).toBe(false);
    });

    it("fails closed when the feature list is missing or empty", () => {
        expect(isRuntimeFeatureEnabled(undefined, "rtsp")).toBe(false);
        expect(isRuntimeFeatureEnabled([], "rtsp")).toBe(false);
    });

    it("looks features up by name among additive entries", () => {
        expect(
            isRuntimeFeatureEnabled(
                [
                    { name: "future", enabled: true },
                    { name: "rtsp", enabled: true },
                ],
                "rtsp",
            ),
        ).toBe(true);
    });

    it("keeps caller availability separate from a USER_GROUPS admin record", () => {
        const metadata = {
            features: [{ name: "watchbox", enabled: false }],
            admin: {
                runtimeFeatures: [
                    {
                        name: "watchbox",
                        consumerClass: "BACKEND_ENFORCED",
                        audience: {
                            mode: "USER_GROUPS",
                            userGroupIds: ["11111111-1111-4111-8111-111111111111"],
                        },
                        revision: 1,
                        updatedAt: "2026-07-20T12:00:00Z",
                    },
                ],
            },
        } satisfies AppMetadata;

        expect(isRuntimeFeatureEnabled(metadata.features, "watchbox")).toBe(false);
        expect(metadata.admin.runtimeFeatures[0].audience).toEqual({
            mode: "USER_GROUPS",
            userGroupIds: ["11111111-1111-4111-8111-111111111111"],
        });
    });
});

describe("compareVersions", () => {
    it("returns match when both versions are equal", () => {
        expect(compareVersions("0.3.0", "0.3.0")).toBe("match");
    });

    it("returns mismatch when both versions are present and different", () => {
        expect(compareVersions("0.3.0", "0.3.1")).toBe("mismatch");
    });

    it("returns missing-metadata when the web version is unset", () => {
        expect(compareVersions(undefined, "0.3.0")).toBe("missing-metadata");
    });

    it("returns missing-metadata when the server version is unset", () => {
        expect(compareVersions("0.3.0", undefined)).toBe("missing-metadata");
    });

    it("returns missing-metadata when both versions are unset", () => {
        expect(compareVersions(undefined, undefined)).toBe("missing-metadata");
    });

    it("treats the legacy unknown sentinel as unset", () => {
        expect(compareVersions("unknown", "0.3.0")).toBe("missing-metadata");
        expect(compareVersions("0.3.0", "unknown")).toBe("missing-metadata");
    });
});

describe("getBuildFooterDisplayState", () => {
    it("formats a compact match state with display-only v prefix and truncated revisions", () => {
        expect(
            getBuildFooterDisplayState(
                {
                    version: "0.0.0",
                    revision: "5cf9d0cf",
                },
                {
                    version: "0.0.0",
                    revision: "4b4d3b1c691beabea56ad24f3587ad6ce8d47958",
                },
            ),
        ).toEqual({
            status: "match",
            version: "v0.0.0",
            webRevision: "5cf9d0cf",
            serverRevision: "4b4d3b1c6",
        });
    });

    it("formats a compact mismatch state with both build identities", () => {
        expect(
            getBuildFooterDisplayState(
                { version: "0.0.0", revision: "5cf9d0cf" },
                { version: "0.0.1", revision: "4b4d3b1c691beabea56ad24f3587ad6ce8d47958" },
            ),
        ).toEqual({
            status: "mismatch",
            web: {
                version: "v0.0.0",
                revision: "5cf9d0cf",
            },
            server: {
                version: "v0.0.1",
                revision: "4b4d3b1c6",
            },
        });
    });

    it("formats missing metadata while preserving the known web identity", () => {
        expect(getBuildFooterDisplayState({ version: "0.0.0", revision: "5cf9d0cf" }, {})).toEqual({
            status: "missing-metadata",
            web: {
                version: "v0.0.0",
                revision: "5cf9d0cf",
            },
            server: {},
        });
    });
});

describe("formatBuildIdentity", () => {
    it("joins version and revision when both are present", () => {
        expect(formatBuildIdentity({ version: "v0.0.0", revision: "5cf9d0cf" }, "unknown")).toBe(
            "v0.0.0 5cf9d0cf",
        );
    });

    it("falls back to the localized unknown label when no identity fields are present", () => {
        expect(formatBuildIdentity({}, "unknown")).toBe("unknown");
    });
});
