// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Draw hints: the instructions the host shows while a tool draws or edits.
 *
 * Every grammar has default hints, so a tool gets instructions without
 * declaring anything; a tool extends or replaces them with
 * the `hints` of its draw kit (`ToolDescriptor.draw`). A hint is a translation key and its values, never
 * display text — the host resolves it with `t()`, so a native subject names a
 * key in the host catalog and a plugin names one in its own namespace
 * (`"<namespace>:<key>"`).
 */
import type { DrawGrammar } from "./draw-grammar";

/**
 * Where the session is, as the hints see it.
 *
 * - `armed` — the tool is active and nothing is placed yet.
 * - `placing` — the first point (or phase) is placed and the draw is in flight.
 * - `editing` — the finished product is held open for adjustment.
 */
export type DrawHintPhase = "armed" | "placing" | "editing";

/** Everything a hint needs to know about the session. */
export interface DrawHintState {
    readonly phase: DrawHintPhase;
    /** Points (or parametric phases) locked so far; 0 while armed. */
    readonly placed: number;
    /** The grammar's minimum point count, when it has one. */
    readonly min?: number;
    /** The grammar's maximum point count (a parametric grammar's phase count). */
    readonly max?: number;
    /** True when a drag on the product's body moves it (its edit model's `translate` is not `false`). */
    readonly movable: boolean;
}

/** One line of instruction: a translation key and its interpolation values. */
export interface DrawHint {
    readonly key: string;
    readonly values?: Readonly<Record<string, string | number>>;
}

/**
 * A tool's hints: replace or extend its grammar's defaults, or
 * `false` for none. `defaults` is what the host would show without the tool.
 */
export type DrawHints =
    | false
    | ((state: DrawHintState, defaults: readonly DrawHint[]) => readonly DrawHint[]);

/**
 * A sequential draw with no `max`, or a `max` above this, completes on a
 * double-click; one at or below it completes on the click that places its
 * last point. Shared by the sequential grammar's binding and its hints so the two cannot
 * disagree.
 */
export const SEQUENTIAL_DOUBLE_CLICK_MAX = 100;

/** Whether a double-click completes a sequential draw's draft. */
export function completesOnDoubleClick(max: number | undefined): boolean {
    return !max || max > SEQUENTIAL_DOUBLE_CLICK_MAX;
}

/** What the host adds to the hints from its own chrome, which the hint state does not describe. */
export interface HostHintOptions {
    /** The edit ends with the host's Save and Cancel, so the edit hints say so. */
    readonly savable: boolean;
}

/**
 * The hints the host shows when a tool declares none: the grammar's draw
 * instructions while armed or placing, and the host's edit instructions while
 * editing. With no resolved grammar, an armed or placing session has no hints.
 * `host` adds the host's own lines, such as the Save line when `savable`.
 */
export function defaultDrawHints(
    grammar: DrawGrammar | undefined,
    state: DrawHintState,
    host: HostHintOptions = { savable: false },
): readonly DrawHint[] {
    if (state.phase === "editing") {
        return [
            { key: state.movable ? "drawHints.edit.reshapeOrMove" : "drawHints.edit.reshape" },
            ...(host.savable ? [{ key: "drawHints.edit.save" }] : []),
        ];
    }
    return grammar ? grammar.hints(state) : [];
}

/** The hints to show: the tool's own over the defaults, or the defaults. */
export function resolveDrawHints(
    hints: DrawHints | undefined,
    grammar: DrawGrammar | undefined,
    state: DrawHintState,
    host?: HostHintOptions,
): readonly DrawHint[] {
    if (hints === false) return [];
    const defaults = defaultDrawHints(grammar, state, host);
    return hints ? hints(state, defaults) : defaults;
}
