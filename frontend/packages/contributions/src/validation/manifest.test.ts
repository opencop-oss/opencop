// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { NATIVE_OWNER_IDS } from "./native-owner-ids.ts";
import { MANIFEST_RULES, validateManifest } from "./manifest.ts";
import { MANIFEST_FIXTURES, VALID_MANIFEST } from "./manifest-fixtures.ts";

const manifest = { id: "acme.heatmap", name: "Heat map", version: "1.0.0", apiVersion: 1 };

describe("validateManifest", () => {
    it("accepts a well-formed manifest", () => {
        expect(validateManifest(manifest, NATIVE_OWNER_IDS)).toEqual([]);
    });

    it("reports every problem, not the first", () => {
        expect(
            validateManifest({ ...manifest, id: "Acme", name: "" }, NATIVE_OWNER_IDS).map(
                (p) => p.rule,
            ),
        ).toEqual(["manifest.id-malformed", "manifest.name-missing"]);
    });

    it("refuses a native plugin's owner id", () => {
        expect(
            validateManifest({ ...manifest, id: NATIVE_OWNER_IDS[0] }, NATIVE_OWNER_IDS).map(
                (p) => p.rule,
            ),
        ).toEqual(["manifest.id-reserved"]);
    });
});

describe("the manifest fixtures", () => {
    it("the valid manifest breaks no rule", () => {
        expect(validateManifest(VALID_MANIFEST, [])).toEqual([]);
    });

    it.each(MANIFEST_FIXTURES.map((fixture) => [fixture.rule, fixture] as const))(
        "%s breaks exactly its rule",
        (rule, fixture) => {
            expect(
                validateManifest(fixture.manifest, fixture.reservedOwnerIds ?? []).map(
                    (p) => p.rule,
                ),
            ).toEqual([rule]);
        },
    );

    it("has a fixture for every rule", () => {
        const covered = new Set(MANIFEST_FIXTURES.map((fixture) => fixture.rule));
        expect(MANIFEST_RULES.filter((rule) => !covered.has(rule))).toEqual([]);
    });
});
