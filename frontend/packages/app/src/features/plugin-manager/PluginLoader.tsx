// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useEffect } from "react";
import { reconcilePlugins, unloadAllPlugins } from "./plugin-runtime";

/**
 * Entry point for the runtime plugin system. Reconciles the loaded plugin set
 * against the enablements document on mount and unloads everything on unmount.
 * Renders nothing. The manager itself lives in `plugin-runtime.ts`, which the
 * admin view also reaches for its retry.
 *
 * The loader is **not flag-gated** — plugins load unconditionally, so a plugin
 * shipping a real feature is on by default. Per-entry gating still exists in the
 * enablements contract: an entry may declare a `flag`, and `runPluginLoader`
 * resolves it through an `isFlagEnabled` predicate. No entry declares one today,
 * so none is passed; the loader then treats any flagged entry as disabled, which
 * is the fail-closed direction. Pass the predicate (and add the flag to the
 * runtime-feature manifest) when an entry needs gating again.
 *
 * Unloading *everything* happens only when this unmounts (the second effect
 * below). A reconcile re-run loads and unloads only the plugins whose eligibility
 * actually changed, so it can never blink an unrelated plugin off the map.
 *
 * A fetch failure is not handled here: the loader retries transient failures
 * itself and publishes the outcome for `/admin/plugins`, which offers a retry.
 * Nothing throws out of this effect.
 */
export function PluginLoader(): null {
    useEffect(() => {
        // The load is an unawaited async fetch. If this unmounts before it
        // resolves, drop the stale result. Reconciliation is not a reset, so a
        // re-run never stacks or blinks plugins.
        let cancelled = false;
        void reconcilePlugins(() => cancelled);
        return () => {
            cancelled = true;
        };
    }, []);

    // Full teardown belongs to the loader's own lifetime, not to a flag change.
    useEffect(() => () => unloadAllPlugins(), []);

    return null;
}
