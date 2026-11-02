// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { RuntimeFeatureRowStatus } from "./runtime-features.rows";

/** Triage order: what needs action first, then the normal state, then the informational ones. */
export const STATUS_ORDER: readonly RuntimeFeatureRowStatus[] = [
    "unsynced",
    "conflict",
    "active",
    "inactive",
    "archived",
];

export const STATUS_LABEL_KEY: Record<RuntimeFeatureRowStatus, string> = {
    active: "runtimeFeatures.statuses.active",
    inactive: "runtimeFeatures.statuses.inactive",
    unsynced: "runtimeFeatures.statuses.unsynced",
    archived: "runtimeFeatures.statuses.archived",
    conflict: "runtimeFeatures.statuses.conflict",
};

/**
 * Palette is the only thing that varies between statuses; every chip renders `subtle` at the
 * default size, per the app's badge convention. It maps onto the palettes the other admin status
 * columns already use: green for healthy, red for a problem, orange for "needs attention", grey for
 * a state that is simply set aside.
 *
 * Inactive and archived deliberately share grey. Neither is an error, and inventing a colour to
 * separate them would imply a severity neither carries — their labels tell them apart.
 */
export const STATUS_PALETTE: Record<RuntimeFeatureRowStatus, string> = {
    active: "green",
    inactive: "gray",
    unsynced: "orange",
    archived: "gray",
    conflict: "red",
};

/**
 * Placeholder for a cell with no server record to read — a declared-but-unregistered row.
 *
 * Shared by the table and the drawer so the two cannot drift onto different dashes.
 */
export const EMPTY_CELL = "—";
