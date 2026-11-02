// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ContributionContext } from "@opencop/contributions";
import { subscribeBackendFeature } from "~/features/admin/runtime-features/backend-feature.subscribe";
import { watchboxDescriptor } from "./watchbox.descriptor";
import { attachWatchboxSelectionSync, watchboxPickResponse } from "./watchbox-click";
import { attachWatchboxPanelPolicy } from "./watchbox-panel.policy";
import { WATCHBOX_STORE_ID } from "./watchbox.store";

export interface InstallWatchboxDeps {
    /** Flag source — the app's backend-feature subscription unless a test injects one. */
    readonly subscribeFeature?: typeof subscribeBackendFeature;
}

/**
 * Availability is registration: the watchbox tool and the click on a saved
 * watchbox exist in the registries exactly while the backend reports the
 * `watchbox` feature available to this caller. The runtime exits a session
 * whose descriptor is removed, so a flag flip mid-draw cannot strand a draft.
 * The map draws watchboxes itself (`WatchboxSceneLayer`), on the same flag.
 *
 * Watchbox has no toolbar button (its descriptor declares `location: "external"`),
 * so toolbar registration was never what gated its "Create watchbox" controls —
 * those live in `WatchboxPanel` itself and call `activate()` unconditionally.
 * What actually keeps them unreachable while the flag is off is that the panel
 * they live in is never mounted: `useVisiblePanels` filters the watchbox mapbar
 * tab on this same flag, so there is no route to the panel, let alone its
 * buttons, while the backend reports the feature unavailable. `activate()`
 * also refuses gracefully if ever called for an unregistered id, as defense
 * in depth, but reaching that path would already mean the panel-visibility
 * gate above had a bug.
 *
 * Returns a disposer (unsubscribe, deregister, detach the panel policy and
 * the selection sync) for the side effects outside the context; the native
 * install path journals it with the subject's registrations.
 */
export function installWatchbox(
    ctx: ContributionContext,
    { subscribeFeature = subscribeBackendFeature }: InstallWatchboxDeps = {},
): () => void {
    let disposeRegistrations: (() => void) | null = null;

    const unsubscribeFlag = subscribeFeature("watchbox", (enabled) => {
        if (enabled && !disposeRegistrations) {
            const cleanups: (() => void)[] = [];
            // Assigned before the first registration: a refusal after install rolls
            // the subject back at once, mid-sequence, and that rollback must reach
            // what was already registered.
            disposeRegistrations = () => {
                for (const cleanup of cleanups.splice(0).reverse()) cleanup();
            };
            try {
                cleanups.push(
                    ctx.registerTool(watchboxDescriptor),
                    ctx.registerPickResponse(WATCHBOX_STORE_ID, watchboxPickResponse),
                );
            } catch (error) {
                // A refusal during install (the flag already on) is rethrown, so this
                // installer returns no disposer for the rollback to call.
                disposeRegistrations();
                disposeRegistrations = null;
                throw error;
            }
        } else if (!enabled && disposeRegistrations) {
            disposeRegistrations();
            disposeRegistrations = null;
        }
    });
    const detachPolicy = attachWatchboxPanelPolicy();
    const detachSelectionSync = attachWatchboxSelectionSync();

    return () => {
        unsubscribeFlag();
        disposeRegistrations?.();
        disposeRegistrations = null;
        detachPolicy();
        detachSelectionSync();
    };
}
