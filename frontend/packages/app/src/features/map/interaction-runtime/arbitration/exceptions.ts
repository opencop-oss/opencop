// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * ESCAPE'S EXCEPTIONS — the UI whose keyboard input this runtime deliberately
 * does NOT arbitrate: a focused text control, and a FOREIGN overlay (a dialog,
 * a menu, a combobox, a select — something with no reason to know the map
 * exists).
 *
 * These parties never register a claimant, and must not. Registration exists
 * so the runtime can DISMISS a party (`escape-claimants.ts`). A dialog closes
 * itself, from its own Escape handler downstream of ours — so claiming the
 * press would `stopPropagation()` and PREVENT it from closing. Standing down
 * is the opposite action from claiming, not a weaker form of it. The runtime
 * therefore recognises these parties by inspecting the DOM and abstains; the
 * two selectors below are the only DOM reads in the keyboard path.
 *
 * Two rules, deliberately different in reach:
 *   - a focused text control holds EVERY key — Escape, Enter, a chord and a
 *     letter shortcut all belong to the field;
 *   - a foreign overlay holds ESCAPE only. A letter shortcut or a Ctrl chord
 *     still reaches the map beneath an open dialog. Longstanding behaviour,
 *     recorded here as a decision rather than left to look accidental.
 *
 * A map-owned SURFACE (`surface.ts`) matches the same overlay selector but is
 * not an exception: the runtime can close it, so it holds a slot in the
 * ladder and gets an action. `escapeIsExcepted` takes the surface slot's state
 * to tell the two apart.
 */

const OPEN_OVERLAY_SELECTOR = ["dialog", "menu", "popover", "select", "color-picker"]
    .map((scope) => `[data-scope="${scope}"][data-state="open"]`)
    .join(",");

/** Keyboard focus is in an editable form control — `INPUT`, `TEXTAREA` or `SELECT`, by tag only. */
function textControlIsFocused(): boolean {
    const tag = document.activeElement?.tagName;
    return tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT";
}

/** An open Zag overlay is in the document — a map-owned surface or a foreign one; the caller tells them apart. */
function overlayIsOpen(): boolean {
    return document.querySelector(OPEN_OVERLAY_SELECTOR) !== null;
}

/** What the DOM says about the parties that never register, sampled once per key press. */
export interface KeyboardContext {
    /** An open Zag overlay exists — the map's own surface or a foreign dialog. */
    readonly overlayOpen: boolean;
    /** Keyboard focus is in an editable form control. */
    readonly textFocused: boolean;
}

export function sampleKeyboardContext(): KeyboardContext {
    return { overlayOpen: overlayIsOpen(), textFocused: textControlIsFocused() };
}

/** A focused text control holds the keyboard — the runtime takes no key at all. */
export function everyKeyIsExcepted(keys: Pick<KeyboardContext, "textFocused">): boolean {
    return keys.textFocused;
}

/**
 * A foreign overlay holds Escape. A map-owned surface matches the same DOM
 * sniff but is the runtime's own to close, so an open surface is not an
 * exception — it wins its slot in the ladder instead.
 */
export function escapeIsExcepted(keys: KeyboardContext, surfaceOpen: boolean): boolean {
    return keys.overlayOpen && !surfaceOpen;
}
