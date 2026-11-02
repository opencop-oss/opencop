// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { getAllTools } from "./tool-registry";
import { getElementMapperEntries } from "./element-mapper-registry";
import { getNativeSubjectRecords } from "./native-install";
import { NATIVE_OWNER_IDS } from "@opencop/contributions/validation";
import { installBuiltInSubjects, NATIVE_SUBJECT_IDS } from "./install-built-in-subjects";

/** Drives watchbox's backend flag; the real one reads the app-metadata query. */
let setWatchboxAvailable: (enabled: boolean) => void = () => undefined;

vi.mock("~/features/admin/runtime-features/backend-feature.subscribe", () => ({
    subscribeBackendFeature: (_id: string, onChange: (enabled: boolean) => void) => {
        setWatchboxAvailable = onChange;
        onChange(false);
        return () => {
            setWatchboxAvailable = () => undefined;
        };
    },
}));

const SUBJECT_IDS = [
    "opencop.shapes",
    "opencop.lasso",
    "opencop.media-icons",
    "opencop.measure",
    "opencop.watchbox",
];

/** What the registries hold, as counts. */
const counts = () => ({
    tools: getAllTools().length,
    mappers: getElementMapperEntries().length,
});

let dispose: () => void = () => undefined;

beforeEach(() => {
    dispose = installBuiltInSubjects();
});

afterEach(() => {
    dispose();
});

describe("the native owner ids", () => {
    // The shared list lets a plugin's own checks refuse a native id without the host.
    it("are the composition root's, in the shared list no plugin may take", () => {
        expect([...NATIVE_SUBJECT_IDS].sort()).toEqual([...NATIVE_OWNER_IDS].sort());
    });
});

describe("installBuiltInSubjects", () => {
    it("installs every native subject, each recorded active", () => {
        expect(getNativeSubjectRecords()).toEqual(
            SUBJECT_IDS.map((id) => ({ id, status: "active" })),
        );
        expect(counts().tools).toBeGreaterThan(0);
        expect(counts().mappers).toBeGreaterThan(0);
    });

    it("records each element mapper as owned by its native subject", () => {
        for (const entry of getElementMapperEntries()) {
            expect(entry.ownerKind).toBe("native");
            expect(SUBJECT_IDS).toContain(entry.ownerId);
        }
    });

    it("is idempotent: a repeat call installs nothing more and returns the same disposer", () => {
        const before = counts();
        expect(installBuiltInSubjects()).toBe(dispose);
        expect(counts()).toEqual(before);
    });

    it("disposes everything it installed, and installs the same again", () => {
        const installed = counts();

        dispose();
        expect(counts()).toEqual({ tools: 0, mappers: 0 });
        expect(getNativeSubjectRecords()).toEqual([]);

        dispose = installBuiltInSubjects();
        expect(counts()).toEqual(installed);
    });

    it("journals a registration made after install, so dispose removes it too", () => {
        const before = counts();
        setWatchboxAvailable(true);
        expect(counts().tools).toBe(before.tools + 1);

        dispose();
        expect(counts()).toEqual({ tools: 0, mappers: 0 });
    });
});
