// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { GroupSource, RuntimeFeature, RuntimeFeatureAudience } from "@opencop/api";
import type { Group } from "~/features/groups/groups.queries";

export type AudienceMode = RuntimeFeatureAudience["mode"];

/** Least-exposed first, so the picker reads as widening access as you go down. */
export const AUDIENCE_MODES: readonly AudienceMode[] = ["NONE", "ALL", "USER_GROUPS"];

/**
 * Display label for a stored consumer class.
 *
 * The wire values (`FRONTEND_ONLY`, `BACKEND_ENFORCED`) are server-internal and read as shouted
 * constants in a table cell, so the UI shows "Frontend" / "Backend" instead.
 */
export function consumerClassLabelKey(consumerClass: string): string {
    return `runtimeFeatures.consumerClasses.${consumerClass}`;
}

/**
 * The stored targets, read-only because this hands back the array inside the cached record rather
 * than a copy — sorting or splicing it in place would mutate query cache state outside
 * `setQueryData`.
 */
export function audienceUserGroupIds(audience: RuntimeFeatureAudience): readonly string[] {
    return audience.mode === "USER_GROUPS" ? audience.userGroupIds : [];
}

export function buildAudience(
    mode: AudienceMode,
    userGroupIds: readonly string[],
): RuntimeFeatureAudience {
    return mode === "USER_GROUPS" ? { mode, userGroupIds: [...userGroupIds] } : { mode };
}

/** A selected audience target, resolved against the groups the directory currently returns. */
export interface AudienceTarget {
    id: string;
    /** Undefined when the group no longer resolves — the id is then the only thing to show. */
    name?: string;
}

/**
 * Resolves stored audience ids against the live group list.
 *
 * An id that no longer resolves is kept as a target with no name rather than dropped. Silently
 * discarding it would mean an unrelated edit — changing the description, adding a different
 * group — quietly revokes access for whoever that group still covers server-side. Removing it has
 * to be something an administrator does deliberately.
 */
export function resolveAudienceTargets(
    userGroupIds: readonly string[],
    groups: readonly Group[] | undefined,
): AudienceTarget[] {
    const byId = new Map((groups ?? []).map((group) => [group.id, group]));
    return userGroupIds.map((id) => ({ id, name: byId.get(id)?.name }));
}

/** Short form for disambiguating two same-named groups without showing a full UUID inline. */
export function shortId(id: string): string {
    return id.slice(0, 8);
}

export function isUnresolved(target: AudienceTarget): boolean {
    return target.name == null;
}

/** Why a pending audience change is worth a confirmation step. */
export type ConfirmationReason =
    /** The audience is being emptied: a feature that reached someone will reach nobody. */
    | "turningOff"
    /** Still reaches someone, but not everyone it used to — somebody loses access. */
    | "narrowingAccess"
    /** The server enforces this feature, so the change reaches server behavior, not just the UI. */
    | "backendEnforced";

export interface PendingAudienceChange {
    consumerClass: RuntimeFeature["consumerClass"];
    storedMode: AudienceMode;
    storedGroupIds: readonly string[];
    nextMode: AudienceMode;
    nextGroupIds: readonly string[];
}

/**
 * Whether a pending audience change should be confirmed first, and why.
 *
 * Returns `null` for the changes that stay one click — enabling a feature, or widening who it
 * reaches. Confirming every save would train an administrator to click through the dialog, which
 * costs more safety than it buys.
 *
 * Reasons are ordered most-specific-first: a backend-enforced feature that loses reach reports the
 * loss, because "somebody loses access" is the consequence worth naming and the enforcement
 * mechanism is not.
 */
export function confirmationReason(change: PendingAudienceChange): ConfirmationReason | null {
    if (losesAccess(change)) return reachesNobody(change) ? "turningOff" : "narrowingAccess";

    if (change.consumerClass === "BACKEND_ENFORCED" && isAudienceChange(change)) {
        return "backendEnforced";
    }

    return null;
}

/**
 * Whether somebody the stored audience reached is not reached by the next one.
 *
 * One test rather than a set of special cases, so every narrowing transition is covered: `ALL` to a
 * group list drops everyone outside those groups just as surely as dropping a group does, and
 * exchanging one group for another still revokes the group that left.
 */
function losesAccess({
    storedMode,
    storedGroupIds,
    nextMode,
    nextGroupIds,
}: PendingAudienceChange): boolean {
    if (storedMode === "NONE") return false;
    if (storedMode === "ALL") return nextMode !== "ALL";
    if (nextMode === "ALL") return false;
    if (nextMode === "NONE") return storedGroupIds.length > 0;
    return storedGroupIds.some((id) => !nextGroupIds.includes(id));
}

/** Whether the pending audience reaches anyone at all. */
function reachesNobody({ nextMode, nextGroupIds }: PendingAudienceChange): boolean {
    return nextMode === "NONE" || (nextMode === "USER_GROUPS" && nextGroupIds.length === 0);
}

/**
 * Whether the audience differs at all.
 *
 * Group targets are compared as a set: the picker hands back its own selection order, so
 * re-selecting a group it already had would otherwise read as a change and raise a dialog for a
 * save that writes the same audience.
 */
function isAudienceChange({
    storedMode,
    storedGroupIds,
    nextMode,
    nextGroupIds,
}: PendingAudienceChange): boolean {
    if (storedMode !== nextMode) return true;
    if (nextMode !== "USER_GROUPS") return false;
    if (nextGroupIds.length !== storedGroupIds.length) return true;
    const stored = new Set(storedGroupIds);
    return nextGroupIds.some((id) => !stored.has(id));
}

/**
 * Label for a group in the audience combobox.
 *
 * A combobox item is a plain string, so the source cannot be a badge here the way it is in the
 * table pickers. It is appended as text instead, and the short id is added only when the source
 * cannot separate the collision either — two groups sharing both a name and a source.
 */
export function audienceGroupLabel(
    group: { id: string; name: string; source: GroupSource },
    counts: { total: number; scim: number; scimLabel: string },
): string {
    if (counts.total <= 1) return group.name;
    const local = counts.total - counts.scim;
    const sourceSeparates = counts.scim <= 1 && local <= 1;
    const suffix = group.source === "SCIM" ? ` — ${counts.scimLabel}` : "";
    return sourceSeparates
        ? `${group.name}${suffix}`
        : `${group.name}${suffix} (${shortId(group.id)})`;
}
