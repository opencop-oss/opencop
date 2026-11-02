// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { localEventSource } from "@opencop/elements";
import type { LayerRole, MyLayerInfo, MyDataSource } from "@opencop/api";

export interface EventMutationTarget {
    kind: "layer" | "source";
    id: string;
}

export type EditabilityReason =
    | "dataSourceReadOnly"
    | "dataSourceOwner"
    | "insufficientLayerRole"
    | "noPermission";

/** Which actions the current user may take on a feature. */
export interface Editability {
    canEditGeometry: boolean;
    canEditProperties: boolean;
    canDelete: boolean;
    canBroadcast: boolean;
    /** Remote mutation route for editable broadcast events. */
    mutationTarget?: EventMutationTarget;
    /**
     * Why the feature is read-only, or which warning state to show for
     * editable data-source events.
     */
    reason?: EditabilityReason;
}

export const EDIT_ROLES: ReadonlySet<LayerRole> = new Set(["OWNER", "ADMIN", "EDITOR"]);

/**
 * Evaluate what the current user can do with a feature based on its source,
 * the user's layer memberships, and the user's data source memberships.
 *
 * Pure function — no React or query dependencies. Consumers call this with
 * pre-fetched data from `layerQueries.myList()` and `dataSourceQueries.myList()`.
 */
export function getEditability(
    source: string,
    myLayers: readonly MyLayerInfo[],
    myDataSources: readonly MyDataSource[],
): Editability {
    // Local features are always fully editable.
    if (source === localEventSource) {
        return {
            canEditGeometry: true,
            canEditProperties: true,
            canDelete: true,
            canBroadcast: true,
        };
    }

    // Data source features — role-based mutability.
    const ds = myDataSources.find((d) => d.serverId === source);
    if (ds) {
        const canMutate = ds.role === "OWNER" || ds.role === "ADMIN";
        if (canMutate) {
            return {
                canEditGeometry: false,
                canEditProperties: true,
                canDelete: true,
                canBroadcast: false,
                mutationTarget: {
                    kind: "source",
                    id: source,
                },
                reason: "dataSourceOwner",
            };
        }
        return {
            canEditGeometry: false,
            canEditProperties: false,
            canDelete: false,
            canBroadcast: false,
            reason: "dataSourceReadOnly",
        };
    }

    // Layer features — check role.
    const layer = myLayers.find((l) => l.layerId === source);
    if (layer && EDIT_ROLES.has(layer.role)) {
        return {
            canEditGeometry: true,
            canEditProperties: true,
            canDelete: true,
            canBroadcast: true,
            mutationTarget: {
                kind: "layer",
                id: source,
            },
        };
    }

    // Layer exists but insufficient role, or source not found in layers.
    return {
        canEditGeometry: false,
        canEditProperties: false,
        canDelete: false,
        canBroadcast: false,
        reason: layer ? "insufficientLayerRole" : "noPermission",
    };
}
