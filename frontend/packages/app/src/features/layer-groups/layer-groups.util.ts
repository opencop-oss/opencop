// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { TFunction } from "i18next";
import { LayerGroupRoles } from "./layer-groups.queries";
import type { LayerGroupAccessScope, LayerGroupRole } from "./layer-groups.queries";
import { LayerRoles } from "@opencop/api";
import type { AccessScope, LayerRole } from "@opencop/api";
import type { TypePaletteKey } from "@opencop/ui";
import type { DrawerRelationRoleOption } from "~/features/drawers/drawer-relation.model";
import { isLayerRoleAtLeast } from "~/features/layers/layers.util";
export { LAYER_ROLE_CONFIG } from "~/features/layers/layers.util";

export const LAYER_GROUP_ROLE_CONFIG: Record<
    LayerGroupRole,
    { colorPalette: string; color: TypePaletteKey }
> = {
    OWNER: { colorPalette: "purple", color: "purple" },
    ADMIN: { colorPalette: "red", color: "red" },
    MAINTAINER: { colorPalette: "blue", color: "emerald" },
    MEMBER: { colorPalette: "gray", color: "sky" },
};

export const LAYER_GROUP_ACCESS_SCOPE_CONFIG: Record<
    LayerGroupAccessScope,
    { colorPalette: string }
> = {
    GLOBAL: { colorPalette: "green" },
    PRIVATE: { colorPalette: "blue" },
};

export const LAYER_GROUP_ROLE_OPTIONS: LayerGroupRole[] = [
    "OWNER",
    "ADMIN",
    "MAINTAINER",
    "MEMBER",
];

export const LAYER_GROUP_ROLE_SELECT_OPTIONS: { labelKey: string; value: LayerGroupRole }[] = [
    { labelKey: "roles.owner", value: LayerGroupRoles.OWNER },
    { labelKey: "roles.admin", value: LayerGroupRoles.ADMIN },
    { labelKey: "roles.maintainer", value: LayerGroupRoles.MAINTAINER },
    { labelKey: "roles.member", value: LayerGroupRoles.MEMBER },
];

/** Layer-group management roles: member / maintainer / admin / owner. */
export function layerGroupRoleOptions(t: TFunction): DrawerRelationRoleOption<LayerGroupRole>[] {
    return [
        { label: t("roles.member"), value: LayerGroupRoles.MEMBER },
        { label: t("roles.maintainer"), value: LayerGroupRoles.MAINTAINER },
        { label: t("roles.admin"), value: LayerGroupRoles.ADMIN },
        { label: t("roles.owner"), value: LayerGroupRoles.OWNER },
    ];
}

/**
 * Privilege order, strongest first, matching the ordinal order of the server's `LayerGroupRole`
 * enum. Keep the two in step: a role added on one side without the other silently changes every
 * comparison here.
 */
const LAYER_GROUP_ROLE_RANK: Record<LayerGroupRole, number> = {
    [LayerGroupRoles.OWNER]: 0,
    [LayerGroupRoles.ADMIN]: 1,
    [LayerGroupRoles.MAINTAINER]: 2,
    [LayerGroupRoles.MEMBER]: 3,
};

/**
 * The roles `actorRole` may grant on a layer group, in the same weakest-first order as
 * {@link layerGroupRoleOptions}.
 *
 * `LayerGroupUserGroupsUseCase.patchUserGroups` rejects any role above the actor's own effective
 * role, and the refusal reaches the client as a bare `{"error":"forbidden"}` with no detail, so an
 * unreachable option has to be withheld rather than left to fail. Pass `undefined` for callers the
 * server exempts from that ceiling, which is global admins.
 */
export function grantableLayerGroupRoleOptions(
    t: TFunction,
    actorRole: LayerGroupRole | undefined,
): DrawerRelationRoleOption<LayerGroupRole>[] {
    const all = layerGroupRoleOptions(t);
    if (actorRole == null) return all;
    return all.filter(
        (option) => LAYER_GROUP_ROLE_RANK[option.value] >= LAYER_GROUP_ROLE_RANK[actorRole],
    );
}

// ── Assignability ─────────────────────────────────────────────────────────────

/**
 * Whether a layer of `layerScope` may be assigned to `group`.
 *
 * The server rule is asymmetric: a GLOBAL group accepts only GLOBAL layers, while a PRIVATE group
 * accepts any layer. Violations come back as 409 "Cannot assign a non-global layer to a global
 * layer group", so the pickers exclude those groups up front rather than letting the user pick a
 * target that is guaranteed to fail.
 *
 * Note `LayerGroupAccessScope` and `AccessScope` are separately declared unions that happen to
 * share the same members today. They are compared only against the literal `"GLOBAL"` here, so the
 * two stay independent — if either ever gains a member, this rule still means what it says.
 */
export function isAssignableLayerGroup(
    group: { accessScope: LayerGroupAccessScope },
    layerScope: AccessScope,
): boolean {
    return group.accessScope !== "GLOBAL" || layerScope === "GLOBAL";
}

/**
 * The groups every one of `layerScopes` can be assigned to. An empty input yields no groups —
 * "assign nothing to everything" is not a meaningful target list.
 *
 * Generic so callers get their own row type back rather than a widened shape.
 */
export function assignableLayerGroups<G extends { accessScope: LayerGroupAccessScope }>(
    groups: readonly G[],
    layerScopes: readonly AccessScope[],
): G[] {
    if (layerScopes.length === 0) return [];
    return groups.filter((group) =>
        layerScopes.every((scope) => isAssignableLayerGroup(group, scope)),
    );
}

/**
 * Whether a **non-global-admin** may assign this layer to a layer group. Mirrors
 * `LayerGroupPolicy.assertLayerIsPrivate` and `assertCanAssignLayer` in opencop-java so the picker
 * offers exactly what the server accepts: EDITOR or above on the layer, and PRIVATE scope. GLOBAL
 * layers are excluded because every user resolves to MEMBER on them via the access-scope fallback,
 * so offering them would let anyone redistribute them through a group they own.
 *
 * Global admins are exempt; do not route their picker through this.
 */
export function canAssignLayerAsNonAdmin(layer: {
    accessScope: AccessScope;
    role: LayerRole;
}): boolean {
    return layer.accessScope === "PRIVATE" && isLayerRoleAtLeast(layer.role, LayerRoles.EDITOR);
}

// ── Role info descriptor arrays ───────────────────────────────────────────────

/**
 * color drives TypeColorLabel (TypePaletteKey; resolved to hex internally).
 * The two axes are independent: MAINTAINER uses color "emerald" so TypeColorLabel renders
 * them as visually distinct from EDITOR ("blue").
 * "gray" has no TYPE_PALETTE equivalent; MEMBER uses "sky" as a neutral stand-in.
 */
export interface RoleDescriptor {
    role: string;
    color: TypePaletteKey;
    descriptionKey: string;
}

// Layer group management roles (Members tab in layer group drawer)
export const LAYER_GROUP_ROLE_DESCRIPTIONS: RoleDescriptor[] = [
    { role: "OWNER", color: "purple", descriptionKey: "roles.ownerGroupDesc" },
    { role: "ADMIN", color: "red", descriptionKey: "roles.adminGroupDesc" },
    { role: "MAINTAINER", color: "emerald", descriptionKey: "roles.maintainerGroupDesc" },
    { role: "MEMBER", color: "sky", descriptionKey: "roles.memberGroupDesc" },
];

// Layer access roles (Layers tab in layer group drawer — what group members get on the layer)
export const LAYER_ROLE_DESCRIPTIONS: RoleDescriptor[] = [
    { role: "OWNER", color: "purple", descriptionKey: "roles.ownerLayerDesc" },
    { role: "ADMIN", color: "red", descriptionKey: "roles.adminLayerDesc" },
    { role: "EDITOR", color: "blue", descriptionKey: "roles.editorLayerDesc" },
    { role: "MEMBER", color: "sky", descriptionKey: "roles.memberLayerDesc" },
];

// Deduplicated list for My Layers — layers use EDITOR, groups use MAINTAINER (no overlap)
export const MY_LAYERS_ROLE_DESCRIPTIONS: RoleDescriptor[] = [
    { role: "OWNER", color: "purple", descriptionKey: "roles.ownerDesc" },
    { role: "ADMIN", color: "red", descriptionKey: "roles.adminDesc" },
    { role: "EDITOR", color: "blue", descriptionKey: "roles.editorDesc" },
    { role: "MAINTAINER", color: "emerald", descriptionKey: "roles.maintainerDesc" },
    { role: "MEMBER", color: "sky", descriptionKey: "roles.memberDesc" },
];

export const ACCESS_SCOPE_DESCRIPTIONS: RoleDescriptor[] = [
    { role: "GLOBAL", color: "green", descriptionKey: "roles.globalScopeDesc" },
    { role: "PRIVATE", color: "blue", descriptionKey: "roles.privateScopeDesc" },
];

// ── Permission helpers ─────────────────────────────────────────────────────────

export interface LayerGroupPermissions {
    canAttachLayers: boolean;
    canManageMembers: boolean;
    canEditGroup: boolean;
    canDeleteGroup: boolean;
}

/**
 * What the caller may do on a layer group.
 *
 * `isAdmin` mirrors `LayerGroupPolicy.assertContainerRole`, which returns early for a global admin
 * before consulting any role. Without it the UI is stricter than the server: an admin who holds no
 * role on a group, or only the GLOBAL-scope `MEMBER` fallback, would be shown controls the server
 * would have accepted.
 */
export function getLayerGroupPermissions(
    containerRole: LayerGroupRole,
    isAdmin = false,
): LayerGroupPermissions {
    return {
        canAttachLayers: isAdmin || containerRole !== LayerGroupRoles.MEMBER,
        canManageMembers:
            isAdmin ||
            containerRole === LayerGroupRoles.ADMIN ||
            containerRole === LayerGroupRoles.OWNER,
        canEditGroup:
            isAdmin ||
            containerRole === LayerGroupRoles.ADMIN ||
            containerRole === LayerGroupRoles.OWNER,
        canDeleteGroup: isAdmin || containerRole === LayerGroupRoles.OWNER,
    };
}
