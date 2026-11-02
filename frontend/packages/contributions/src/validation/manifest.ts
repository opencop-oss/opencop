// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { SDK_API_VERSION } from "../plugin/define-plugin.ts";
import type { PluginManifest } from "../plugin/manifest.ts";

/** Every rule a manifest must meet, by stable id. */
export const MANIFEST_RULES = [
    "manifest.id-missing",
    "manifest.id-malformed",
    "manifest.id-reserved",
    "manifest.name-missing",
    "manifest.version-missing",
    "manifest.api-version-missing",
    "manifest.api-version-mismatch",
] as const;

export type ManifestRule = (typeof MANIFEST_RULES)[number];

/**
 * The manifest rules a host stops a load on, before running `install`: who the plugin is
 * (every contribution rule is namespaced by its id) and which protocol it was built for (an
 * `install` built against another would mislead). On any other manifest problem the host
 * goes on to check the contributions, so every problem is reported in one pass — and still
 * refuses the plugin.
 */
export const STOPPING_MANIFEST_RULES: ReadonlySet<ManifestRule> = new Set([
    "manifest.id-missing",
    "manifest.id-malformed",
    "manifest.id-reserved",
    "manifest.api-version-missing",
    "manifest.api-version-mismatch",
]);

/**
 * One thing wrong with a manifest. Every one makes the host refuse the plugin. Shaped
 * like a contribution `Problem`, so a plugin's checks report one list.
 */
export interface ManifestProblem {
    readonly rule: ManifestRule;
    readonly kind: "manifest";
    readonly severity: "error";
    readonly message: string;
}

/**
 * A plugin id is an owner id, `<namespace>.<name>`: lower-case words joined by
 * hyphens, with at least one dot (`opencop.symbology`, `acme.heatmap`). It
 * cannot contain `:`, which joins an owner to the ids of its contributions.
 */
const OWNER_ID = /^[a-z0-9]+(-[a-z0-9]+)*(\.[a-z0-9]+(-[a-z0-9]+)*)+$/;

/**
 * Every problem with a plugin's manifest. `reservedOwnerIds` are ids no plugin may
 * take — the host's native plugins' (`NATIVE_OWNER_IDS` in
 * `@opencop/contributions/validation`). An `apiVersion` other than this build's
 * `SDK_API_VERSION` is a problem too: the host's version gate.
 */
export function validateManifest(
    manifest: PluginManifest,
    reservedOwnerIds: readonly string[],
): ManifestProblem[] {
    const problems: ManifestProblem[] = [];
    const report = (rule: ManifestRule, message: string) =>
        void problems.push({ rule, kind: "manifest", severity: "error", message });
    if (typeof manifest.id !== "string" || manifest.id.length === 0)
        report("manifest.id-missing", "manifest.id missing");
    else if (!OWNER_ID.test(manifest.id))
        report(
            "manifest.id-malformed",
            `manifest.id "${manifest.id}" must be "<namespace>.<name>": lower case, at least one ".", no ":"`,
        );
    else if (reservedOwnerIds.includes(manifest.id))
        report(
            "manifest.id-reserved",
            `manifest.id "${manifest.id}" is reserved by a native plugin`,
        );
    if (typeof manifest.name !== "string" || manifest.name.length === 0)
        report("manifest.name-missing", "manifest.name missing");
    if (typeof manifest.version !== "string")
        report("manifest.version-missing", "manifest.version missing");
    if (typeof manifest.apiVersion !== "number")
        report("manifest.api-version-missing", "manifest.apiVersion missing");
    else if (manifest.apiVersion !== SDK_API_VERSION)
        report(
            "manifest.api-version-mismatch",
            `apiVersion ${manifest.apiVersion} != host ${SDK_API_VERSION}`,
        );
    return problems;
}
