// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useQuery } from "@tanstack/react-query";
import { isRuntimeFeatureEnabled } from "~/features/admin/app-metadata-state";
import { appMetadataQueries } from "~/features/admin/app-metadata.queries";
import type { BackendFeatureId, FeatureFlagId } from "./runtime-features.manifest";

/**
 * Whether a gate has resolved yet.
 *
 * `pending` exists because app metadata arrives asynchronously. Collapsing it into `false` makes a
 * cold load of an enabled deep link indistinguishable from a disabled flag, which is how a route
 * guard ends up bouncing a legitimately enabled page to `/map` before the answer arrives.
 */
export type FeatureGateStatus = "pending" | "enabled" | "disabled";

/**
 * Resolves a declared frontend flag to `pending | enabled | disabled`.
 *
 * The flag's audience is the whole access decision. There is deliberately **no** client-side admin
 * check: that existed only because the old flags lived in one admin's `localStorage` and had no
 * server-side audience to respect. Now the server resolves `features[].enabled` per the stored
 * audience — and it grants admins no bypass, so an admin outside the audience reads `false` here
 * exactly as any other user does.
 *
 * Role restrictions belong at the route (`ProtectedRoute roles={["ROLE_admin"]}`), not in this
 * hook. Keeping them here would make it impossible to express a flag that gates a feature for
 * ordinary users — which `rtsp` is.
 *
 * A settled query that does not list the name reads as `disabled`: an unregistered flag is off,
 * the safe default for an environment where nobody has run Sync. A query that has never resolved
 * and then errors is also `disabled` rather than `pending`, so a failing metadata endpoint denies
 * access instead of hanging.
 */
export function useFeatureFlagStatus(id: FeatureFlagId | undefined): FeatureGateStatus {
    return useRuntimeFeatureStatus(id);
}

/**
 * The shared read. Metadata resolves every runtime feature's audience for the caller regardless of
 * which side owns the name, so ownership only decides which union types the id — never how the
 * answer is obtained. Private so a call site cannot pass an arbitrary string and gate on a name no
 * array in the manifest accounts for.
 */
function useRuntimeFeatureStatus(name: string | undefined): FeatureGateStatus {
    const { data, isPending } = useQuery(appMetadataQueries.detail());

    if (!name) return "disabled";
    if (isPending) return "pending";
    // Deliberately keyed on `data`, not `isError`. A query that resolved and is now failing a
    // background refetch reports `isError` while still holding last-known-good data — reading that
    // as denied would bounce an active user off an enabled route on one focus-triggered refetch.
    // Only a query with nothing to read from denies — nullish, because an errored query holds
    // `undefined` while an endpoint answering with a null body resolves to `null`.
    if (data == null) return "disabled";
    return isRuntimeFeatureEnabled(data.features, name) ? "enabled" : "disabled";
}

/**
 * Boolean form for call sites that render conditionally rather than redirect — hiding a card or a
 * menu entry while the answer is pending is invisible to the user, so they do not need the third
 * state. Anything that *navigates* must use {@link useFeatureFlagStatus} instead.
 */
export function useFeatureFlagEnabled(id: FeatureFlagId | undefined): boolean {
    return useFeatureFlagStatus(id) === "enabled";
}

/**
 * Whether a backend-owned feature is available to this caller.
 *
 * Separate from {@link useFeatureFlagEnabled} because the name is not this build's to declare —
 * see {@link CONSUMED_BACKEND_FEATURES}. The distinction is about ownership, not access: the
 * server is the enforcement point either way, and this hook only spares the user a control whose
 * every use the API would refuse.
 *
 * Boolean-only by design. A backend feature gates a control inside an already-reachable view, not
 * a route, so there is no redirect that could fire on a still-`pending` answer.
 */
export function useBackendFeatureEnabled(id: BackendFeatureId | undefined): boolean {
    return useRuntimeFeatureStatus(id) === "enabled";
}
