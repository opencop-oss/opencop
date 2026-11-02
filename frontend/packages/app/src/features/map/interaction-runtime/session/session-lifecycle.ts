// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The session-lifecycle notification — how the rest of the app learns that a
 * session began, reached its product, or ended, WITHOUT the commands knowing
 * who cares.
 *
 * The commands are the only emitters. They fire each event synchronously, in
 * process, from inside the command that caused it: a subscriber runs before
 * the command returns and before React renders anything, so state a reaction
 * writes (a selection, an editing overlay, an open panel) is already in place
 * on the first render that shows the new session. That is load-bearing — the
 * events edit form reads its overlay on its first render — which is why this
 * is a listener list and not an event queue.
 *
 * Subscribers are host features reacting to the session (selection, the
 * events-editing overlay, the watchbox panel). A subscriber that throws is
 * contained and logged: the store is already consistent when an event fires,
 * and one broken reaction must not take the command — or the other
 * reactions — down with it.
 *
 * Host-internal. A plugin that needs "tell me when an edit ends" gets a
 * `PluginContext` capability through the plugin-capabilities checklist, not
 * this list.
 */
import Logger from "js-logger";
import type { DrawSession, EditSession } from "./session";

const logger = Logger.get("interaction");

/**
 * How a session ended: `save` kept the product, `cancel` reverted it, anything
 * else left it. `"saved"` says the outcome, not who wrote it away — a custody
 * save and a custody-less one (the watchbox form persisting through its own
 * API) are deliberately indistinguishable here.
 */
export type SessionEnd = "saved" | "cancelled" | "exited";

export type SessionLifecycleEvent =
    /**
     * `activate()` passed its gates and a session on this descriptor is
     * beginning — a draw, or an edit straight away for an immediate placement.
     * The ONE event fired before its session is written (the store is idle at
     * that moment, the previous session already ended): it announces the
     * session so a reaction can clear what the session's completion will
     * replace. A restart, a params change or a re-arm continues the session and
     * fires nothing.
     */
    | { readonly type: "activated"; readonly descriptorId: string; readonly custody: boolean }
    /**
     * The session now holds a finished product open for adjustment — a draft
     * completed into an edit, or `startEdit` over an existing element. Fired
     * after the edit session is in the store.
     */
    | { readonly type: "edit-started"; readonly session: EditSession }
    /**
     * The session is over and the store is idle again. `session` is the one
     * that ended; `how` is why — a lasso leaving through `onComplete`, an
     * Escape, a tool switch and a teardown are all `"exited"`.
     */
    | {
          readonly type: "ended";
          readonly session: DrawSession | EditSession;
          readonly how: SessionEnd;
      };

export type SessionLifecycleListener = (event: SessionLifecycleEvent) => void;

const listeners = new Set<SessionLifecycleListener>();

/** Hear every session-lifecycle event until the returned disposer runs. */
export function subscribeSessionLifecycle(listener: SessionLifecycleListener): () => void {
    listeners.add(listener);
    return () => {
        listeners.delete(listener);
    };
}

/**
 * Deliver one event to every subscriber, synchronously, in subscription
 * order. For the commands — nothing else emits.
 */
export function emitSessionLifecycle(event: SessionLifecycleEvent): void {
    // A copy: a listener may unsubscribe (itself or another) while we iterate.
    for (const listener of [...listeners]) {
        try {
            listener(event);
        } catch (err) {
            logger.error(`session-lifecycle subscriber threw on "${event.type}"`, err);
        }
    }
}
