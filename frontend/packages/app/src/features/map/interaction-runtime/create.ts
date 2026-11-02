// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The runtime's composition point — wires the two modules to each other and
 * to a host, and takes them apart again. `MapManager.bindInteraction(rt)` is
 * the one caller in the app; the test harness is the other.
 *
 * `session/` and `arbitration/` do not know each other's shapes. Arbitration
 * reads the session snapshot and calls its commands directly in two places —
 * the router's Enter and shortcut branches, the appliers' cursor and lock
 * derivations (the README's "still in motion" list) — and everything else
 * that joins the two is here. On the Escape channel it registers the session as one claimant among
 * the others (`isActive` = a session is live; `dismiss` = the session's own
 * `sessionEscape`) and the open map surface as another. On the pointer channel
 * it registers the three owners — the session (its draw or edit bindings,
 * identified by `bindingKey`), the open surface, and ambient — and the runner
 * binds whichever wins. It also closes the open surface when a session starts:
 * a subscriber to the session's lifecycle notification, one step after the
 * command's own write, in the same synchronous tick.
 *
 * Attaching registers the owners and binds the runner, the keyboard router,
 * the diffed appliers and the registry watch. Detaching ends any live session
 * FIRST — releasing its custody while the host is still attached — then
 * withdraws the surface slot (cleared, not closed: the surfaces are unmounting
 * with the map) and unbinds everything. A StrictMode double-mount of the map
 * runs attach → detach → attach before any session can exist, so nothing is
 * lost to it; the runner alone detaching and re-attaching mid-session
 * (`session/custody.test.ts`) disposes listeners and nothing else.
 *
 * The runtime attaches no feature policy: how the selection, the events
 * panel or a tool's own panel react to the session is each feature's to
 * attach, where that feature composes — and the map selection and the area
 * selection register their own Escape claimants the same way.
 */
import { assertNever } from "assert-never";
import type { DescriptorResolver, InteractionHost, InteractionRuntime } from "./contract";
import { attachHost } from "./host";
import { bindingKey, getInteractionState, subscribeInteraction } from "./session/interaction.store";
import { sessionEscape } from "./session/session";
import { bindSession } from "./session/bind";
import { cancel, exit, resetInteraction, restart } from "./session/commands";
import { subscribeSessionLifecycle } from "./session/session-lifecycle";
import { attachRegistryWatch } from "./session/registry-watch";
import { attachRunner } from "./arbitration/runner";
import { attachKeyboardRouter } from "./arbitration/keyboard-router";
import { attachAppliers } from "./arbitration/appliers";
import { registerEscapeClaimant, type EscapeClaimant } from "./arbitration/escape-claimants";
import { registerPointerOwners, type PointerOwner } from "./arbitration/pointer-owners";
import { ambientPointerOwner } from "./arbitration/sources/ambient-pointer";
import {
    closeSurface,
    surfaceEscapeClaimant,
    surfacePointerOwner,
    withdrawSurface,
} from "./arbitration/surface";

/** The session as the Escape channel sees it: one claimant, whose meaning is its own. */
function sessionEscapeClaimant(resolve: DescriptorResolver): EscapeClaimant {
    return {
        isActive: () => getInteractionState().session.kind !== "idle",
        dismiss: () => {
            const { session } = getInteractionState();
            if (session.kind === "idle") return;
            const outcome = sessionEscape(session, resolve);
            switch (outcome) {
                case "restart":
                    restart();
                    return;
                case "cancel":
                    cancel();
                    return;
                case "exit":
                    exit();
                    return;
                default:
                    assertNever(outcome);
            }
        },
    };
}

/** The session as the pointer channel sees it: one owner, whose handler set and its identity are its own. */
function sessionPointerOwner(): PointerOwner {
    return {
        isActive: () => getInteractionState().session.kind !== "idle",
        identity: () => bindingKey(getInteractionState().session),
        bind: (host) => bindSession(getInteractionState().session, host),
        subscribe: (listener) => subscribeInteraction(listener),
    };
}

/**
 * Seat the pointer's three owners — the whole closed set, as the registry
 * requires. Part of `attach`; on its own it is what the runner canary attaches
 * beside a bare runner to have something to bind.
 */
export function attachPointerOwners(): () => void {
    return registerPointerOwners({
        session: sessionPointerOwner(),
        surface: surfacePointerOwner,
        ambient: ambientPointerOwner,
    });
}

export function createInteractionRuntime(): InteractionRuntime {
    let disposers: Array<() => void> | null = null;

    return {
        attach(host: InteractionHost) {
            if (disposers) throw new Error("the interaction runtime is already attached");
            disposers = [
                // Teardown first: end any session (releasing custody) before the
                // host, the runner and the appliers detach beneath it; then empty
                // the surface slot without closing — the surfaces unmount with the map.
                () => resetInteraction(),
                () => withdrawSurface(),
                attachHost(host),
                attachPointerOwners(),
                attachRunner(host),
                attachKeyboardRouter(host),
                attachAppliers(host),
                attachRegistryWatch(host.environment),
                registerEscapeClaimant("session", sessionEscapeClaimant(host.environment.tool)),
                registerEscapeClaimant("surface", surfaceEscapeClaimant),
                subscribeSessionLifecycle((event) => {
                    if (event.type === "activated" || event.type === "edit-started") closeSurface();
                }),
            ];
        },
        detach() {
            if (!disposers) return;
            const pending = disposers;
            disposers = null;
            for (const dispose of pending) dispose();
        },
    };
}
