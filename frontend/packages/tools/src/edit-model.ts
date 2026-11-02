// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The declarative edit contract: how a product's control points, hit regions,
 * and drag math are expressed WITHOUT touching the host (no map handle, no
 * store, no React). The host's interaction runtime interprets an
 * {@link EditModel}; the model itself is pure over the draft's geometry +
 * geometry math + a caller-supplied screen projector.
 *
 * This is the counterpart of {@link import("./draw-grammar").DrawGrammar} for the edit half of
 * a tool's lifecycle: a `DrawGrammar` says how geometry is *created* from clicks,
 * `EditModel` says how it is *changed* by drags.
 */
import type { DraftObject } from "@opencop/elements";

/** Projects a [lng, lat] pair to canvas [x, y] pixels; undefined before the map binds. */
export type ScreenProjector = (lngLat: [number, number]) => [number, number] | undefined;

/**
 * One draggable handle. `kind` is model-owned vocabulary ("vertex", "origin",
 * "radius", …); `index` disambiguates repeated kinds (vertex index).
 *
 * `id` is an optional stable identity for a handle that outlives its index —
 * a model whose parts can be inserted or removed (a route's waypoints) can
 * name them so a host or an editor can refer to one across drags. Nothing in
 * the interpreter reads it yet; index-identified handles need not set it.
 */
export interface ControlPoint {
    readonly kind: string;
    readonly position: [number, number];
    readonly index?: number;
    readonly id?: string;
}

/**
 * What a pointer position means to the edit session right now — resolved at
 * pointer-down (to latch a drag target) and on hover (to derive the cursor).
 */
export type EditIntent =
    | { kind: "handle"; controlPoint: ControlPoint }
    | { kind: "boundary" }
    | { kind: "body" }
    | { kind: "none" };

/**
 * Declarative control-point editing for one kind of product — any
 * {@link DraftObject}, an element or not.
 *
 * All methods are pure over the draft's current geometry: the host captures a
 * geometry snapshot at drag start and recomputes from the live pointer each
 * frame, so a model never sees drag deltas — only "this handle is now at this
 * lngLat".
 */
export interface EditModel<D extends DraftObject = DraftObject> {
    /** The draggable handles for the draft's current geometry. */
    controlPoints(draft: D): readonly ControlPoint[];

    /**
     * Move one handle to a new position (mutates the draft's geometry).
     * `snapshot` is the flat geometry snapshot captured at drag start — models
     * whose handle math needs stable reference geometry (a rectangle's anchor
     * corners) read it instead of the live positions, so recomputing each frame
     * cannot drift.
     */
    applyHandle(
        draft: D,
        controlPoint: ControlPoint,
        lngLat: [number, number],
        snapshot?: readonly number[],
    ): void;

    /**
     * Resolve what the pointer is over. When omitted, the host's default
     * applies: nearest control point within `tolerancePx`, else `none`. Models
     * with non-vertex hit regions (a circle's boundary ring, a shape body that
     * supports translate) implement this.
     *
     * `tolerancePx` is the host's grab tolerance in screen pixels — a policy of
     * the running app (pointer type, display density), not of the geometry, so
     * it is passed in rather than chosen by each model.
     */
    hitTest?(
        draft: D,
        screen: [number, number],
        lngLat: [number, number],
        project: ScreenProjector,
        tolerancePx: number,
    ): EditIntent;

    /** Drag response for a `boundary` intent (e.g. circle radius). */
    applyBoundary?(draft: D, lngLat: [number, number]): void;

    /**
     * Whole-product translation for a `body` intent. `from`/`to` are the
     * pointer's previous and current positions for this frame; the host applies
     * Mercator-offset math when this is omitted but a `body` intent is reported.
     *
     * `false` means the product is never moved as a whole: the host treats a
     * `body` intent as `none` — no drag, no move cursor — and leaves the move
     * instruction out of the edit hints.
     */
    readonly translate?: ((draft: D, from: [number, number], to: [number, number]) => void) | false;

    /**
     * Which render attribute a drag invalidates — passed to the host's
     * invalidate call so only the layers reading it rebuild. Default "vertex".
     */
    readonly invalidation?: "vertex" | "all";
}
