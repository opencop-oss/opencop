// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { RuntimeFeature } from "@opencop/api";
import { DECLARED_FEATURE_FLAGS, isDeclaredFeatureFlag } from "./runtime-features.manifest";

/**
 * A catalog row's state, combining two questions: does this build still reference it, and is it
 * switched on? Deliberately separate from consumer class, which is its own column.
 *
 * Only a *frontend* flag can go archived. The frontend manifest is not the owner of a backend flag,
 * so its silence says nothing about whether that flag is in use — a backend flag is judged purely on
 * its audience, exactly like a declared frontend one.
 */
export type RuntimeFeatureRowStatus =
    /** Registered and switched on — its audience reaches at least someone. */
    | "active"
    /**
     * Registered but reaching nobody: audience `NONE`, or `USER_GROUPS` targeting no group.
     *
     * Distinct from `active` because a green "Active" chip on a flag nobody can see is a lie: the
     * definition exists, but the feature is not reaching anyone.
     */
    | "inactive"
    /** Declared by this build but absent from the catalog — Sync creates it. */
    | "unsynced"
    /**
     * A `FRONTEND_ONLY` row this build no longer declares.
     *
     * Archived means "no code in this build gates on it", **not** "disabled" or "safe to delete".
     * Nothing here can read it — `FeatureFlagId` admits declared names only — but the row keeps its
     * stored audience and the server keeps resolving it, and a build that *does* declare the name
     * (another environment's release, or `main` after a revert) still gates on it. The label
     * describes the manifest relationship, never the row's runtime effect.
     */
    | "archived"
    /** A declared name owned by a `BACKEND_ENFORCED` row. Sync cannot fix this. */
    | "conflict";

export interface RuntimeFeatureRow {
    name: string;
    status: RuntimeFeatureRowStatus;
    /** Absent only when `status` is `unsynced`. */
    record?: RuntimeFeature;
}

export interface FlagDrift {
    /** Declared names with no catalog row; exactly what a Sync run would register. */
    missing: readonly string[];
    /** Declared names whose catalog row is `BACKEND_ENFORCED`. Requires a human, not a retry. */
    classConflicts: readonly string[];
}

/**
 * Joins the server catalog against this build's manifest.
 *
 * Rows are returned for the union of both sides so a declared-but-unregistered flag is visible
 * (and syncable) rather than silently absent from the table.
 */
export function buildRuntimeFeatureRows(
    catalog: readonly RuntimeFeature[] | undefined,
): RuntimeFeatureRow[] {
    const rows: RuntimeFeatureRow[] = (catalog ?? []).map((record) => ({
        name: record.name,
        record,
        status: classifyCatalogRow(record),
    }));

    const catalogNames = new Set(rows.map((row) => row.name));
    const missing: RuntimeFeatureRow[] = DECLARED_FEATURE_FLAGS.filter(
        (flag) => !catalogNames.has(flag.id),
    ).map((flag) => ({ name: flag.id, status: "unsynced" as const }));

    return [...rows, ...missing].sort((a, b) => a.name.localeCompare(b.name));
}

function classifyCatalogRow(record: RuntimeFeature): RuntimeFeatureRowStatus {
    const declared = isDeclaredFeatureFlag(record.name);
    if (record.consumerClass !== "FRONTEND_ONLY") {
        // A declared name colliding with a backend-owned row is a real problem. Any other backend
        // row is simply outside the manifest's remit, and being outside it is not a defect.
        return declared ? "conflict" : enablementStatus(record);
    }
    // Archived outranks enablement: that this build no longer references the flag is the more
    // actionable fact, and an archived row can be switched on or off like any other.
    return declared ? enablementStatus(record) : "archived";
}

/**
 * Whether the stored audience reaches anyone.
 *
 * `USER_GROUPS` with an empty target list counts as reaching nobody. The server rejects an empty
 * target list and the drawer's Save is disabled without a selection, so this should be unreachable —
 * but the table renders whatever the catalog returns, and a row that reached no one while showing a
 * green "Active" chip would be exactly the lie this split exists to prevent.
 */
function enablementStatus(record: RuntimeFeature): RuntimeFeatureRowStatus {
    const audience = record.audience;
    if (audience.mode === "NONE") return "inactive";
    if (audience.mode === "USER_GROUPS" && audience.userGroupIds.length === 0) return "inactive";
    return "active";
}

/** Derives drift from already-built rows, so the join runs once per render rather than twice. */
export function computeFlagDrift(rows: readonly RuntimeFeatureRow[]): FlagDrift {
    return {
        missing: rows.filter((row) => row.status === "unsynced").map((row) => row.name),
        classConflicts: rows.filter((row) => row.status === "conflict").map((row) => row.name),
    };
}
