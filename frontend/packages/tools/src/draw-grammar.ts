// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Draw grammars: how the pointer becomes geometry for one tool.
 *
 * A grammar is the whole draw interaction — its states, its transitions, and
 * the product-specific pieces it was configured with. A tool's draw kit carries
 * one (`DrawKit.grammar`), and the host binds exactly that grammar for each draw
 * session. The built-in grammars are factories over their configuration
 * (`createSequentialGrammar`, …, in `./grammars`); a tool whose draw none of them
 * fits implements {@link DrawGrammar} itself.
 *
 * The contract is host-neutral: a grammar hears pointer events through
 * {@link DrawInput}, never a map library's own events, and touches the session
 * only through {@link DraftRefs}.
 */
import type { DraftObject, Position2D } from "@opencop/elements";
import type { DrawHint, DrawHintState } from "./draw-hints";

/** The pointer events a grammar can bind on the map. */
export type DrawPointerEventType = "mousedown" | "mousemove" | "click" | "dblclick" | "contextmenu";

/** A pointer event over the map, as a grammar sees it. */
export interface DrawPointerEvent {
    /** The cursor's map position, `[lng, lat]`. */
    readonly lngLat: Position2D;
    /** The cursor's position on the canvas, px. */
    readonly point: { readonly x: number; readonly y: number };
    /** The mouse button (0 primary, 2 secondary). */
    readonly button: number;
    /** Stop the map's own handling of this event — its double-click zoom, its context menu. */
    preventDefault(): void;
}

/**
 * A bound listener. `unsub()` means "no further calls" — the event being
 * dispatched right now included.
 */
export interface DrawSubscription {
    readonly event: string;
    unsub(): void;
}

/** The input a grammar binds on. Every binding is released when the session ends. */
export interface DrawInput {
    onPointer(
        type: DrawPointerEventType,
        listener: (ev: DrawPointerEvent) => void,
    ): DrawSubscription;
    /**
     * The release of a mouse button anywhere in the document — for a gesture
     * that begins on the map and may end off it.
     */
    onRelease(listener: (ev: { readonly button: number }) => void): DrawSubscription;
    /**
     * The Enter key, pressed while this draw runs. The host owns the key and
     * delivers it only when it gives it to the session — never while a text
     * control has focus. The grammar decides whether the press completes its draft.
     */
    onEnter(listener: () => void): DrawSubscription;
    /**
     * The release that ends a press may make the map synthesize a click from the
     * press–release pair. Call this before completing on a release, so that click
     * never reaches the host's ambient picking.
     */
    consumeNextClick(): void;
}

/**
 * The draft as a grammar sees it: read and write the in-flight object and the
 * count of locked points, record placement, and report completion,
 * abandonment, and geometry changes that should re-apply the tool's style.
 *
 * The draft is a {@link DraftObject} — any object on the map with a live
 * geometry — so a grammar draws a product of any kind, an element or not.
 */
export interface DraftRefs {
    getDraft(): DraftObject | undefined;
    setDraft(draft: DraftObject): void;
    getLocked(): number;
    setLocked(locked: number): void;
    /** Whether the first locked point has been recorded. */
    isPlaced(): boolean;
    /** Record the first locked point — the draft becomes the session's product. */
    onPlace(draft: DraftObject): void;
    /** The draw is over — the host moves the session to edit (or runs the tool's `onComplete`). */
    onComplete(draft: DraftObject): void;
    /** The operator abandoned the in-flight draft. */
    onAbort(): void;
    /** The draft's geometry changed in a way that should re-apply style values. */
    onUpdate(): void;
}

/**
 * How the pointer becomes geometry for one tool: the draw interaction, with the
 * product-specific pieces it needs. One grammar value may serve many draws, so
 * any state a single draw needs lives inside {@link bind}, never on the grammar.
 *
 * Only {@link bind} and {@link hints} run code the host calls; every other
 * member is a plain value.
 */
export interface DrawGrammar {
    /** Bind the input that draws the product; the host releases every returned binding. */
    bind(input: DrawInput, draft: DraftRefs): DrawSubscription[];
    /**
     * The instructions to show while armed or placing. Every grammar ships its
     * own, so a tool is never without hints; the host supplies the edit phase's.
     */
    hints(state: DrawHintState): readonly DrawHint[];
    /**
     * The product at `cursor`, whole — for immediate placement, when the host
     * places a product without a draw (a point placed from the map context menu).
     */
    create(cursor: Position2D): DraftObject;
    /** The point counts the draw needs, for hints and the construction path. Omitted ⇒ none. */
    readonly bounds?: { readonly min?: number; readonly max?: number };
    /**
     * Ask the host to draw a dashed construction path through the draft's points
     * while the draft has fewer points than {@link bounds}' `min` — for a product
     * that cannot be drawn until it has enough points.
     * Opt-in, so a plain shape keeps its own look from the first point.
     */
    readonly construction?: "path";
    /**
     * The map does not pan while this grammar's draft exists — for a grammar that
     * draws by dragging, so the drag draws instead of moving the map.
     */
    readonly locksPan?: boolean;
}
