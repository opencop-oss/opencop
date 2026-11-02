// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The attached host — the one slot through which the module-level commands
 * reach the map. Set by `createInteractionRuntime().attach(host)` and cleared
 * by `detach()`; everything effectful a command does goes through it, so
 * nothing fires before the map exists.
 *
 * NO LIVE SESSION EVER SEES `null` HERE, and the guarantee is structural
 * rather than a disposer list: `detach` ends the session (releasing custody)
 * before it clears the slot, and `MapManager.dispose()` detaches the runtime
 * before it drops the map and the deck. So the window in which `getHost()`
 * returns null is the window in which the session is necessarily idle.
 *
 * That is why the commands' null checks are uniform refusals rather than
 * partial-cleanup paths: they guard the entry points a caller can reach
 * before the map exists (activating a tool, editing an element from a panel),
 * and are unreachable from anything holding a session. A check here that
 * *skipped* work instead of refusing would be a silent custody leak in a
 * state that cannot occur — worse than a refusal, and misleading to read.
 *
 * A module-level slot because the commands are module-level functions — the
 * interaction store is a module singleton by decision, and the slot binds
 * *the* runtime to *the* manager exactly as the store binds *the* session.
 *
 * ONE HOST AT A TIME. Everything the runtime rests on is a module singleton —
 * this slot, the session store, the Escape and pointer registries, the key
 * bindings, the surface slot, the router's one keydown listener — so a second
 * runtime attaching while the first is bound would evict the first map's
 * owners silently and double-fire every key. That is the one map per app
 * assumption made loud: attaching a different host over a bound one throws in
 * DEV. A StrictMode double-mount detaches before it re-attaches and never
 * trips it; re-attaching the same host is allowed.
 */
import type { InteractionHost } from "./contract";

let attached: InteractionHost | null = null;

/** Attach the host. Returns the disposer that clears the slot (a stale disposer leaves a newer host alone). */
export function attachHost(host: InteractionHost): () => void {
    if (import.meta.env.DEV && attached !== null && attached !== host) {
        throw new Error(
            "a second interaction host attached while another is bound — the runtime, its registries and its keydown listener are singletons: one map per app. Detach the first runtime before binding another.",
        );
    }
    attached = host;
    return () => {
        if (attached === host) attached = null;
    };
}

/** The attached host, or null before the map binds the runtime (commands refuse then). */
export function getHost(): InteractionHost | null {
    return attached;
}
