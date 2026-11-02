// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { LayerRoles } from "@opencop/api";
import type { AccessScope, LayerRole } from "@opencop/api";
import { maxBy } from "lodash";
import type { TFunction } from "i18next";
import type { TypePaletteKey } from "@opencop/ui";
import type { DrawerRelationRoleOption } from "~/features/drawers/drawer-relation.model";

export const LAYER_ROLE_CONFIG: Record<LayerRole, { colorPalette: string; color: TypePaletteKey }> =
    {
        OWNER: { colorPalette: "purple", color: "purple" },
        ADMIN: { colorPalette: "red", color: "red" },
        EDITOR: { colorPalette: "blue", color: "blue" },
        MEMBER: { colorPalette: "gray", color: "sky" },
    };

export const ACCESS_SCOPE_CONFIG: Record<AccessScope, { colorPalette: string }> = {
    GLOBAL: { colorPalette: "green" },
    PRIVATE: { colorPalette: "blue" },
};

/** Layer access roles: member / editor / admin / owner. */
export function layerRoleOptions(t: TFunction): DrawerRelationRoleOption<LayerRole>[] {
    return [
        { label: t("roles.member"), value: LayerRoles.MEMBER },
        { label: t("roles.editor"), value: LayerRoles.EDITOR },
        { label: t("roles.admin"), value: LayerRoles.ADMIN },
        { label: t("roles.owner"), value: LayerRoles.OWNER },
    ];
}

/**
 * Privilege order, strongest first, matching the ordinal order of the server's `LayerRole` enum.
 * Keep the two in step: a role added on one side without the other silently changes every
 * comparison here.
 */
const LAYER_ROLE_RANK: Record<LayerRole, number> = {
    [LayerRoles.OWNER]: 0,
    [LayerRoles.ADMIN]: 1,
    [LayerRoles.EDITOR]: 2,
    [LayerRoles.MEMBER]: 3,
};

/** True when `role` is at least as strong as `other`; mirrors `LayerRole.isAtLeast` in opencop-java. */
export function isLayerRoleAtLeast(role: LayerRole, other: LayerRole): boolean {
    return LAYER_ROLE_RANK[role] <= LAYER_ROLE_RANK[other];
}

/** The weakest of the given roles, which caps what may be granted across all of them. */
export function weakestLayerRole(roles: readonly LayerRole[]): LayerRole | undefined {
    return maxBy(roles, (role) => LAYER_ROLE_RANK[role]);
}

// ── Owner-safety predicates ──────────────────────────────────────────────────
// The layer member surfaces (inline lock, change-role validation, bulk gates)
// enforce two invariants. Keep both encoded here so every site agrees, rather
// than re-deriving the booleans independently.

/**
 * Per-row edit lock: an owner's role can't be changed when the viewer can't
 * assign owners (only an owner / global admin may touch an owner) or when they
 * are the sole owner (the layer must keep one).
 */
export function isOwnerEditBlocked(
    role: LayerRole,
    canAssignOwner: boolean,
    isSoleOwner: boolean,
): boolean {
    return role === LayerRoles.OWNER && (!canAssignOwner || isSoleOwner);
}

/**
 * Last-owner guard: true when `ids` covers every current owner, so applying the
 * action to them (revoke, or demote via change-role) would leave the layer with
 * no owner — which the server rejects (LAYER_REQUIRES_OWNER).
 */
export function wouldStripLastOwner(ids: string[], owners: readonly { userId: string }[]): boolean {
    if (owners.length === 0) return false;
    const idSet = new Set(ids);
    return owners.every((o) => idSet.has(o.userId));
}
