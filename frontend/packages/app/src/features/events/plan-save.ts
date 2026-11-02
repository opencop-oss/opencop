// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { localEventSource } from "@opencop/elements";
import type { EventMutationTarget } from "~/features/map/editability";

/**
 * The single, typed decision for an event save: which of the four outcomes a
 * Save click resolves to. `useEventSave` computes this once and dispatches —
 * it no longer recomputes create-vs-patch from string heuristics in two places.
 *
 * - `local` — no broadcast target chosen: save to the local store only.
 * - `create` — a never-saved local element broadcast to a layer: POST.
 * - `patch` — editing an existing layer / data-source event: PATCH the
 *   `target` route supplied by `getEditability`.
 * - `move` — relocating an existing layer event to a different layer: DELETE
 *   from `fromLayer`, then POST to `targetLayer`. The two mutations are one
 *   user-level action, so they are one action here rather than a delete the
 *   caller has to remember to sequence before a save. Only layer events move —
 *   a data-source event's identity belongs to its upstream feed, so relocating
 *   it would fork the event while the feed re-populates the deleted original.
 */
export type SaveAction =
    | { readonly kind: "local" }
    | { readonly kind: "create"; readonly targetLayer: string }
    | { readonly kind: "patch"; readonly target: EventMutationTarget; readonly objectId: string }
    | {
          readonly kind: "move";
          readonly fromLayer: string;
          readonly objectId: string;
          readonly targetLayer: string;
      };

/**
 * Decide how to save an event from its origin, identity, chosen broadcast
 * target, and mutation route. Pure — no React, no queries, no I/O.
 *
 * The create-vs-patch split keys off the element's *original* source, never the
 * overlay-derived `broadcastTarget` (which becomes the chosen layer once the user
 * picks one). Shadowing the origin with the target caused the original
 * create-vs-patch regression; keeping the discriminator on the origin makes
 * that class of bug impossible.
 *
 * @param originalSource  The element's true origin (`localEventSource` for a
 *   freshly-drawn shape; a layer/data-source id for an existing event).
 * @param objectId        The event's stable object id, if it has one.
 * @param broadcastTarget The save target resolved from the edit overlay
 *   (`overlay.source ?? originalSource`): the chosen layer, the existing source,
 *   or `localEventSource` / empty when staying local.
 * @param mutationTarget  The remote mutation route from `getEditability`, present
 *   only for editable existing layer/data-source events.
 * @param move            The user ticked "move to layer": an existing remote event
 *   should leave its current source rather than be patched in place. Ignored when
 *   the chosen target is the source the event already lives on (a move to where it
 *   already is is just a patch).
 * @throws if a remote element resolves to a patch or a move but has no
 *   `mutationTarget`; if a remote element has no broadcast target at all (the
 *   caller must fall back to the origin, never let a cleared field re-key the
 *   event to a local save); or if a data-source event is asked to move (the UI
 *   gates moves on `Editability.canBroadcast`, which data-source events never
 *   hold — reaching here is a bug above this function).
 */
export function planSave(
    originalSource: string,
    objectId: string | undefined,
    broadcastTarget: string | undefined,
    mutationTarget: EventMutationTarget | undefined,
    move = false,
): SaveAction {
    // No target, or staying local → local-only save. Only a local element may
    // resolve here: a remote event with an empty target would silently re-key
    // itself into a local no-op while the server copy lives on.
    if (!broadcastTarget || broadcastTarget === localEventSource) {
        if (originalSource !== localEventSource && objectId) {
            throw new Error("Remote event has no broadcast target");
        }
        return { kind: "local" };
    }

    // A never-saved local element (origin still `local`, or no object id yet) that
    // is being broadcast → create. It has no `mutationTarget` to patch against.
    if (originalSource === localEventSource || !objectId) {
        return { kind: "create", targetLayer: broadcastTarget };
    }

    // An existing remote event → patch or move via its editability-supplied route.
    if (!mutationTarget) {
        throw new Error("Missing mutation target for remote event");
    }

    // Relocating to a *different* source: the event has to be dropped from its
    // current source and re-created on the target. Patching would only rewrite
    // it in place, since the patch route is keyed off the origin.
    if (move && broadcastTarget !== originalSource) {
        if (mutationTarget.kind === "source") {
            throw new Error("Data-source events cannot be moved");
        }
        return {
            kind: "move",
            fromLayer: mutationTarget.id,
            objectId,
            targetLayer: broadcastTarget,
        };
    }

    return { kind: "patch", target: mutationTarget, objectId };
}
