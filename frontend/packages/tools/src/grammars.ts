// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The built-in draw grammars — the click/preview loops that turn pointer events
 * into geometry — as factories over the configuration each one takes. They bind
 * through {@link DrawInput} and mutate the draft through {@link DraftRefs}; they
 * know nothing about sessions, stores or persistence. `./shape-grammars` holds
 * the grammar each built-in shape draws with.
 */
import type { DraftObject, PolygonGeometry, PolylineGeometry, Position2D } from "@opencop/elements";
import { completesOnDoubleClick, type DrawHint } from "./draw-hints";
import type { DraftRefs, DrawGrammar, DrawSubscription } from "./draw-grammar";
import { setVertex } from "./screen-math";

/**
 * What every built-in grammar is configured with. `D` is the draft type `create`
 * makes — an element type for a shape, a subject's own draft object otherwise —
 * and the type the grammar's previews receive.
 */
export interface GrammarConfig<D extends DraftObject = DraftObject> {
    /** The draft at the cursor's first position. */
    readonly create: (cursor: Position2D) => D;
    /** See {@link DrawGrammar.construction}. */
    readonly construction?: "path";
}

type Creator<D extends DraftObject> = Pick<GrammarConfig<D>, "create">;

/**
 * The session's draft as the type this grammar's `create` makes. Sound because a
 * grammar only reads back a draft it created: the host binds one grammar per draw
 * session, and only that grammar sets the session's draft.
 */
function current<D extends DraftObject>(draft: DraftRefs): D | undefined {
    return draft.getDraft() as D | undefined;
}

/** The draft, created at `cursor` on first sight of the pointer. */
function ensureDraft<D extends DraftObject>(
    config: Creator<D>,
    draft: DraftRefs,
    cursor: Position2D,
): D {
    const existing = current<D>(draft);
    if (existing) return existing;
    const created = config.create(cursor);
    draft.setDraft(created);
    draft.onUpdate();
    return created;
}

/** The first locked click places the draft (creating it if no preview preceded the click). */
function placeIfNeeded<D extends DraftObject>(
    config: Creator<D>,
    draft: DraftRefs,
    cursor: Position2D,
): D {
    const object = ensureDraft(config, draft, cursor);
    if (!draft.isPlaced()) {
        draft.onPlace(object);
        draft.onUpdate();
    }
    return object;
}

/** The configuration of a point draw: nothing beyond what it creates. */
export type PointGrammarConfig<D extends DraftObject = DraftObject> = GrammarConfig<D>;

/** One click places and completes. */
export function createPointGrammar<D extends DraftObject>(
    config: PointGrammarConfig<D>,
): DrawGrammar {
    return {
        create: config.create,
        construction: config.construction,
        bind: (input, draft) => [
            input.onPointer("mousemove", (ev) => {
                const existing = current<D>(draft);
                if (!existing) ensureDraft(config, draft, ev.lngLat);
                else setVertex(existing.geometry, ev.lngLat, 0);
            }),
            input.onPointer("mousedown", () => {
                const object = current<D>(draft);
                if (!object) return;
                draft.onPlace(object);
                draft.setLocked(draft.getLocked() + 1);
                draft.onComplete(object);
            }),
        ],
        hints: () => [{ key: "drawHints.point.place" }],
    };
}

/** The configuration of a vertex-by-vertex draw. */
export interface SequentialGrammarConfig<
    D extends DraftObject = DraftObject,
> extends GrammarConfig<D> {
    /** The fewest points the product may complete with. */
    readonly min?: number;
    /** The most points; reaching it completes the draft. */
    readonly max?: number;
    /**
     * Move the cursor's point, called on every pointer move once the draft
     * exists, with the count of points locked so far. Omitted ⇒ the point at
     * index `locked` follows the cursor.
     */
    readonly preview?: (object: D, cursor: Position2D, locked: number) => void;
}

/**
 * Vertex by vertex: a click locks one, right-click removes the last, and a
 * double-click, Enter or `max` completes the draft.
 */
export function createSequentialGrammar<D extends DraftObject>(
    config: SequentialGrammarConfig<D>,
): DrawGrammar {
    const { min, max, preview } = config;
    return {
        create: config.create,
        construction: config.construction,
        bounds: { min, max },
        bind(input, draft) {
            // The draft's own points, not the locked count: they include the one
            // following the cursor, and a double-click's point is already locked
            // with no cursor point beyond it.
            const completeIfEnough = () => {
                const object = current<D>(draft);
                if (object && (min ?? 0) <= object.geometry.numPositions) {
                    draft.onComplete(object);
                }
            };
            const subs: DrawSubscription[] = [
                input.onPointer("mousemove", (ev) => {
                    const existing = current<D>(draft);
                    if (!existing) ensureDraft(config, draft, ev.lngLat);
                    else if (preview) preview(existing, ev.lngLat, draft.getLocked());
                    else existing.geometry.setPos(ev.lngLat, draft.getLocked());
                }),
                input.onPointer("click", (ev) => {
                    const object = placeIfNeeded(config, draft, ev.lngLat);
                    // A click with no point beyond the locked ones — the second click of
                    // a double-click — would count the same point twice.
                    if (object.geometry.numPositions <= draft.getLocked()) return;
                    const locked = draft.getLocked() + 1;
                    draft.setLocked(locked);
                    if (locked >= (max ?? Infinity)) {
                        draft.onComplete(object);
                        return;
                    }
                    draft.onUpdate();
                }),
                input.onPointer("contextmenu", () => {
                    const object = current<D>(draft);
                    if (object && object.geometry.getAllPos().length > 2) {
                        (object.geometry as PolylineGeometry).pop();
                        draft.setLocked(draft.getLocked() - 1);
                    }
                    draft.onUpdate();
                }),
                input.onEnter(completeIfEnough),
            ];
            if (completesOnDoubleClick(max)) {
                subs.push(
                    input.onPointer("dblclick", (ev) => {
                        ev.preventDefault();
                        completeIfEnough();
                    }),
                );
            }
            return subs;
        },
        hints(state) {
            if (state.phase === "armed") return [{ key: "drawHints.sequential.start" }];
            const floor = min ?? 0;
            // The draft holds the placed points plus the cursor's, so the next
            // click is what reaches `min`.
            const next: DrawHint =
                floor <= state.placed + 1
                    ? {
                          key: completesOnDoubleClick(max)
                              ? "drawHints.sequential.addOrFinish"
                              : "drawHints.sequential.addOrFinishEnter",
                      }
                    : {
                          key: "drawHints.sequential.needMore",
                          values: { count: floor - state.placed },
                      };
            return [next, { key: "drawHints.sequential.undo" }];
        },
    };
}

/** One state of a parametric draw: what the cursor does while it is the next to place. */
export interface ParametricPhase<D extends DraftObject = DraftObject> {
    readonly preview: (object: D, cursor: Position2D) => void;
    /** What to tell the operator while this phase is the next to place. */
    readonly hint?: DrawHint;
}

/** The configuration of a draw through a fixed series of phases. */
export interface ParametricGrammarConfig<
    D extends DraftObject = DraftObject,
> extends GrammarConfig<D> {
    /** The draw's states in order; a click locks the current one and moves to the next. */
    readonly phases: readonly ParametricPhase<D>[];
}

/** A fixed series of phases, each previewed under the cursor and locked by a click. */
export function createParametricGrammar<D extends DraftObject>(
    config: ParametricGrammarConfig<D>,
): DrawGrammar {
    const { phases } = config;
    return {
        create: config.create,
        construction: config.construction,
        bounds: { min: phases.length, max: phases.length },
        bind: (input, draft) => [
            input.onPointer("mousemove", (ev) => {
                const existing = current<D>(draft);
                if (!existing) ensureDraft(config, draft, ev.lngLat);
                else phases[draft.getLocked()].preview(existing, ev.lngLat);
            }),
            input.onPointer("click", (ev) => {
                const object = placeIfNeeded(config, draft, ev.lngLat);
                phases[draft.getLocked()]!.preview(object, ev.lngLat);
                const locked = draft.getLocked() + 1;
                draft.setLocked(locked);
                if (locked >= phases.length) draft.onComplete(object);
            }),
        ],
        hints: (state) => [phases[state.placed]?.hint ?? { key: "drawHints.parametric.next" }],
    };
}

const DEFAULT_TRACE_THROTTLE_PX = 10;

/** The configuration of a freehand trace. */
export interface TraceGrammarConfig<D extends DraftObject = DraftObject> extends Omit<
    GrammarConfig<D>,
    "construction"
> {
    /** Minimum on-screen travel between captured vertices, px (default 10). */
    readonly screenThrottlePx?: number;
}

/**
 * Press → drag → release. A trace has no preview phase: the primary press
 * creates AND places the draft, each move past the screen throttle appends a
 * vertex, and the release completes. The release is heard anywhere in the
 * document, so a pointer that leaves the canvas mid-trace still completes; a
 * right-click mid-press abandons the draft.
 *
 * @remarks provisional — removal is free while `SDK_API_VERSION` is 1.
 */
export function createTraceGrammar<D extends DraftObject>(
    config: TraceGrammarConfig<D>,
): DrawGrammar {
    const throttle = config.screenThrottlePx ?? DEFAULT_TRACE_THROTTLE_PX;
    return {
        create: config.create,
        locksPan: true,
        bind(input, draft) {
            let last: [number, number] | null = null;
            return [
                input.onPointer("mousedown", (ev) => {
                    if (ev.button !== 0) return;
                    const object = config.create(ev.lngLat);
                    draft.setDraft(object);
                    draft.onPlace(object);
                    draft.setLocked(1);
                    last = [ev.point.x, ev.point.y];
                    draft.onUpdate();
                }),
                input.onPointer("mousemove", (ev) => {
                    const object = current<D>(draft);
                    if (!last || !object) return;
                    const point: [number, number] = [ev.point.x, ev.point.y];
                    if (Math.hypot(point[0] - last[0], point[1] - last[1]) < throttle) return;
                    (object.geometry as PolygonGeometry).push(ev.lngLat);
                    draft.setLocked(draft.getLocked() + 1);
                    last = point;
                }),
                input.onPointer("contextmenu", (ev) => {
                    ev.preventDefault();
                    if (!last) return;
                    last = null;
                    draft.onAbort();
                }),
                input.onRelease((ev) => {
                    if (ev.button !== 0 || !last) return;
                    last = null;
                    const object = current<D>(draft);
                    if (!object) return;
                    // If the pointer barely moved, the map synthesizes a click from the
                    // press–release pair; it must never reach ambient picking.
                    input.consumeNextClick();
                    draft.onComplete(object);
                }),
            ];
        },
        hints: (state) =>
            state.phase === "armed"
                ? [{ key: "drawHints.trace.start" }]
                : [{ key: "drawHints.trace.release" }],
    };
}
