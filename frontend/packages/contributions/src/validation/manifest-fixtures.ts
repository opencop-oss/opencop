// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ManifestRule } from "./manifest.ts";
import type { PluginManifest } from "../plugin/manifest.ts";

/** A manifest that breaks no rule. */
export const VALID_MANIFEST: PluginManifest = {
    id: "acme.fixture",
    name: "Fixture",
    version: "1.0.0",
    apiVersion: 1,
};

/** A manifest that breaks one rule, and that rule. */
export interface ManifestFixture {
    readonly rule: ManifestRule;
    readonly manifest: PluginManifest;
    /** The native owner ids in force; `manifest.id-reserved` needs one. */
    readonly reservedOwnerIds?: readonly string[];
}

const broken = (overrides: Record<string, unknown>) =>
    ({ ...VALID_MANIFEST, ...overrides }) as PluginManifest;

/** One fixture per rule in `MANIFEST_RULES`; a coverage test fails for a rule with none. */
export const MANIFEST_FIXTURES: readonly ManifestFixture[] = [
    { rule: "manifest.id-missing", manifest: broken({ id: "" }) },
    { rule: "manifest.id-malformed", manifest: broken({ id: "Acme:Fixture" }) },
    {
        rule: "manifest.id-reserved",
        manifest: broken({ id: "opencop.native-fixture" }),
        reservedOwnerIds: ["opencop.native-fixture"],
    },
    { rule: "manifest.name-missing", manifest: broken({ name: "" }) },
    { rule: "manifest.version-missing", manifest: broken({ version: undefined }) },
    { rule: "manifest.api-version-missing", manifest: broken({ apiVersion: undefined }) },
    { rule: "manifest.api-version-mismatch", manifest: broken({ apiVersion: 999 }) },
];
