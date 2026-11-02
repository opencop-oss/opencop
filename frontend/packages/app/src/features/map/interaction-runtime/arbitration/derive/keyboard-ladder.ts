// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Keyboard-ladder RESOLUTION — which of the runtime's OWN parties wins an
 * Escape press, as a pure function over the claimant slots. The parties the
 * runtime does not arbitrate — a focused text control, a foreign overlay —
 * are `../exceptions.ts`'s, checked by the router before this ladder is
 * consulted; nothing here knows they exist. The DOM listener that executes
 * the winning rung lives in `../keyboard-router.ts`; this file decides, the
 * router does.
 *
 * The written Escape priority order (one rung per press, top wins):
 *   1. a live session — waiting, in flight, or editing              → the session's
 *   2. an open map-owned SURFACE (the click-disambiguation popover, the map
 *      context menu)                                              → close it
 *   3. the map selection (the events panel's detail)              → clear it
 *   4. an area selection (the events panel's working set)         → clear it
 *
 * The ladder ranks; it does not interpret. What Escape DOES to the party it
 * reached is that party's own `dismiss()` (`../escape-claimants.ts`) — for
 * the session, `sessionEscape` in `../../session/session` — so a change to
 * the tool's state machine changes no line here, and a new party wanting the
 * key registers into a slot and changes no line here either. While any
 * session is live Escape is its and touches no selection. Rungs 3–4 dismiss
 * selections innermost-first — a point picked from inside a lasso goes back
 * to its list before the working set, the operator's most deliberate state,
 * is touched. The events panel owns no rung: it is the presentation of a
 * selection and follows it.
 *
 * The slots are passed as one record, so adding a slot to {@link EscapeSlot}
 * fails to compile at the router's call site until it is supplied — the
 * written order has exactly one representation.
 */
import type { ToolDescriptor } from "@opencop/tools";

/**
 * The slots a party may register into (`../escape-claimants.ts`), in the order
 * the ladder ranks them — the session, the open map surface, the map
 * selection, the area selection.
 */
export type EscapeSlot = "session" | "surface" | "map-selection" | "area-selection";

export type EscapeResolution =
    /**
     * The slot's claimant owns Escape. `session`: what it does is the
     * session's own answer (`sessionEscape`). `surface`: close it.
     * `map-selection`: clear it — the events panel follows, closing when the
     * selection opened it and returning to its list when the operator did.
     * `area-selection`: clear it and its boundary — the last thing Escape touches.
     */
    { readonly rung: EscapeSlot } | { readonly rung: "none" };

/** Resolve which slot this Escape press belongs to, from whether each slot's claimant holds something. */
export function resolveEscape(slots: Readonly<Record<EscapeSlot, boolean>>): EscapeResolution {
    // 1. A live session owns Escape whatever stands beneath it. A surface
    // cannot coexist with one (starting a session closes it), and a standing
    // selection is never Escape's business while a tool is armed — the tool
    // is what the operator is doing, and the working set is what they built.
    if (slots.session) return { rung: "session" };

    // 2. A map-owned surface.
    if (slots.surface) return { rung: "surface" };

    // 3. The map selection — innermost-first. A point picked from inside a
    // lasso is dismissed back to the list it came from; the working set
    // beneath it stands.
    if (slots["map-selection"]) return { rung: "map-selection" };

    // 4. The area selection: the working set in the events panel, the last
    // thing Escape touches.
    if (slots["area-selection"]) return { rung: "area-selection" };

    return { rung: "none" };
}

/** Which registered descriptor a single-letter shortcut names, if any. */
export function resolveShortcut(
    key: string,
    descriptors: readonly ToolDescriptor[],
): ToolDescriptor | undefined {
    const lower = key.toLowerCase();
    return descriptors.find((d) => d.shortcut === lower);
}
