// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The frontend flags this build declares.
 *
 * This array is the ownership authority, not merely a display list: {@link FeatureFlagId} is
 * derived from it, so a call site can only gate on a name declared here. Adding an entry and
 * running Sync is the whole workflow for a new frontend-only gate — no Flyway migration and no
 * coordinated Java release.
 *
 * A name's presence in the server catalog does **not** imply it belongs to this build, and its
 * absence from this manifest does **not** mean it is dead. Because the catalog is shared and this
 * array is per-build, a row this build stops declaring can still be gated on by a build another
 * environment is running, or by `main` after a revert — while its stored audience keeps resolving
 * for users either way. That is why the admin table describes rows as declared or not declared *by
 * this build* rather than claiming a global lifecycle.
 */
export const DECLARED_FEATURE_FLAGS = [
    {
        id: "themes-page",
        labelKey: "featureFlags.flags.themesPage.label",
        descriptionKey: "featureFlags.flags.themesPage.description",
    },
    {
        // Unlike `themes-page`, this gates a capability for *ordinary users* rather than an admin
        // preview: it controls whether RTSP can be picked when creating a data source.
        // Seeded by migration V33, so Sync never creates it.
        id: "rtsp",
        labelKey: "featureFlags.flags.rtsp.label",
        descriptionKey: "featureFlags.flags.rtsp.description",
    },
] as const satisfies readonly FeatureFlagDefinition[];

/**
 * Backend-owned features this build *reads* but does not own.
 *
 * Deliberately a separate array. These names are declared server-side (`BackendRuntimeFeature`)
 * and their catalog rows are `BACKEND_ENFORCED`, which is why they must never be added to
 * {@link DECLARED_FEATURE_FLAGS}: `classifyCatalogRow` reports a declared name owned by a
 * backend-enforced row as a `conflict` — permanent drift no Sync run can clear — and Sync would
 * try to create a name the server already maintains.
 *
 * Reading one is still legitimate: app metadata resolves *every* runtime feature's audience for
 * the caller regardless of who owns it, so a UI that would otherwise offer a control the API
 * refuses can hide it. See {@link useBackendFeatureEnabled}.
 */
export const CONSUMED_BACKEND_FEATURES = [
    {
        // The server enforces this one for real: `ManageWatchboxesUsecase` throws
        // WATCHBOX_FEATURE_DISABLED for a caller outside the audience. The toolbar gate only spares
        // the user a button whose every use would be rejected.
        id: "watchbox",
        labelKey: "featureFlags.flags.watchbox.label",
        descriptionKey: "featureFlags.flags.watchbox.description",
    },
] as const satisfies readonly FeatureFlagDefinition[];

export interface FeatureFlagDefinition {
    /** Wire name; must satisfy the server's `^[a-z0-9._-]+$` grammar. */
    id: string;
    /** i18n key for the flag label, resolved via t() at render time. */
    labelKey: string;
    /** i18n key for the flag description, resolved via t() at render time. */
    descriptionKey: string;
}

/** Only names this build declares. Widening this to `string` would defeat the manifest. */
export type FeatureFlagId = (typeof DECLARED_FEATURE_FLAGS)[number]["id"];

/** Only backend-owned names this build reads. Kept out of {@link FeatureFlagId} on purpose. */
export type BackendFeatureId = (typeof CONSUMED_BACKEND_FEATURES)[number]["id"];

const DECLARED_IDS: ReadonlySet<string> = new Set(DECLARED_FEATURE_FLAGS.map((flag) => flag.id));

export function isDeclaredFeatureFlag(name: string): name is FeatureFlagId {
    return DECLARED_IDS.has(name);
}

/**
 * Display copy for a name, from either array.
 *
 * Ownership decides sync and drift, not whether the admin table can name a row: a backend-owned
 * row this build reads has copy worth showing, and falling back to the bare wire name for it would
 * be a worse table for no gain.
 */
export function findFeatureCopy(name: string): FeatureFlagDefinition | undefined {
    return [...DECLARED_FEATURE_FLAGS, ...CONSUMED_BACKEND_FEATURES].find(
        (flag) => flag.id === name,
    );
}
