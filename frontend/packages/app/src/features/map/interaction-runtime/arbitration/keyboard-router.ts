// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The keyboard router — ONE document-capture keydown listener for the whole
 * interaction layer. It states the runtime's exceptions once, first: a
 * focused text control holds every key, a foreign overlay holds Escape
 * (`exceptions.ts`). For Escape, resolution (which party wins) is then the
 * pure ladder in `derive/keyboard-ladder.ts` over the claimant registry
 * (`escape-claimants.ts`); this file samples the slots, executes the winner's
 * `dismiss()`, and stops the event after every party that acted so no
 * listener beneath it can act on the same press. It learns nothing about
 * what a dismissal does.
 *
 * Enter and the single-letter tool shortcuts are the session's keys. Enter
 * goes to the running draw's grammar, which binds it through
 * `DrawInput.onEnter` and decides what it does (`../session/session-enter.ts`);
 * a shortcut goes to the session's commands. A Ctrl/Cmd chord goes to a
 * registered key binding (`key-bindings.ts`) — the events clipboard's copy and paste —
 * when nothing outranks it: a live session takes the chord first, and it
 * falls through to the browser.
 */
import type { InteractionHost } from "../contract";
import { getInteractionState } from "../session/interaction.store";
import { resolveEscape, resolveShortcut } from "./derive/keyboard-ladder";
import { dismissEscapeSlot, isEscapeSlotActive } from "./escape-claimants";
import { escapeIsExcepted, everyKeyIsExcepted, sampleKeyboardContext } from "./exceptions";
import { matchKeyBinding } from "./key-bindings";
import { activate, canActivate } from "../session/commands";
import { deliverSessionEnter } from "../session/session-enter";

export function attachKeyboardRouter(host: InteractionHost): () => void {
    const { environment } = host;

    const onKeyDown = (e: KeyboardEvent) => {
        // The parties the runtime does not arbitrate, first and once.
        const keys = sampleKeyboardContext();
        if (everyKeyIsExcepted(keys)) return;

        if (e.key === "Escape") {
            // The registry's answer per slot. One record, so a new slot fails
            // to compile here until it is supplied.
            const slots = {
                session: isEscapeSlotActive("session"),
                surface: isEscapeSlotActive("surface"),
                "map-selection": isEscapeSlotActive("map-selection"),
                "area-selection": isEscapeSlotActive("area-selection"),
            };
            // A foreign overlay holds Escape; a map-owned surface is ours and
            // wins its slot instead.
            if (escapeIsExcepted(keys, slots.surface)) return;

            const resolution = resolveEscape(slots);
            if (resolution.rung === "none") return;
            e.stopPropagation();
            dismissEscapeSlot(resolution.rung);
            return;
        }

        if (e.key === "Enter") {
            deliverSessionEnter();
            return;
        }

        const { session } = getInteractionState();

        // A modified chord: a registered key binding's, when idle; the
        // browser's otherwise, and whenever no binding can act — the default is
        // prevented only for a binding that ran.
        if (e.ctrlKey || e.metaKey) {
            if (session.kind !== "idle") return;
            const binding = matchKeyBinding(e);
            if (binding?.canRun()) {
                e.preventDefault();
                binding.run();
            }
            return;
        }

        // Single-letter tool shortcuts. `canActivate` is the whole gate —
        // refused in historical, over an edit and over an in-flight draw (a
        // trace mid-press included); a tool merely waiting for its first
        // click is preempted, exactly as from the toolbar.
        if (e.altKey) return;
        const descriptor = resolveShortcut(e.key, environment.tools());
        if (descriptor && canActivate(descriptor.id)) {
            e.preventDefault();
            activate(descriptor.id);
        }
    };

    document.addEventListener("keydown", onKeyDown, true);
    return () => document.removeEventListener("keydown", onKeyDown, true);
}
