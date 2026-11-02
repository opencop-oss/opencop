// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import { Polygon, Rectangle, type Element } from "@opencop/elements";
import type { DraftRefs, DrawGrammar, DrawInput } from "./draw-grammar";
import { createSequentialGrammar, createTraceGrammar } from "./grammars";
import { arrowGrammar, polygonGrammar, rectangleGrammar, traceGrammar } from "./shape-grammars";

/**
 * The built-in grammars, driven the way the host's input would drive them.
 * Store-free like the grammars themselves: the draft refs are plain values,
 * and the assertions are about which pointer events change the draft and which
 * end it.
 */

type Listener = (ev: unknown) => void;

function fakeInput() {
    const pointer = new Map<string, Listener[]>();
    const release: Listener[] = [];
    const enter: Array<() => void> = [];
    const consumeNextClick = vi.fn();
    const input = {
        onPointer: (event, listener) => {
            pointer.set(event, [...(pointer.get(event) ?? []), listener as Listener]);
            return { event, unsub: vi.fn() };
        },
        onRelease: (listener) => {
            release.push(listener as Listener);
            return { event: "release", unsub: vi.fn() };
        },
        onEnter: (listener) => {
            enter.push(listener);
            return { event: "enter", unsub: vi.fn() };
        },
        consumeNextClick,
    } satisfies DrawInput;
    return {
        input,
        fireMap: (event: string, ev: unknown) => pointer.get(event)?.forEach((l) => l(ev)),
        fireDoc: (_event: "mouseup", ev: unknown) => release.forEach((l) => l(ev)),
        fireEnter: () => enter.forEach((l) => l()),
    };
}

function fakeRefs() {
    let element: Element | undefined;
    let locked = 0;
    let placed = false;
    const refs = {
        getDraft: () => element,
        setDraft: (el: Element) => {
            element = el;
        },
        getLocked: () => locked,
        setLocked: (n: number) => {
            locked = n;
        },
        isPlaced: () => placed,
        onPlace: vi.fn(() => {
            placed = true;
        }),
        onComplete: vi.fn(),
        onAbort: vi.fn(),
        onUpdate: vi.fn(),
    } satisfies DraftRefs;
    return refs;
}

function mouse(lngLat: [number, number], point: { x: number; y: number }, button = 0) {
    return { lngLat, point, button, preventDefault: vi.fn() };
}

function harness(grammar: DrawGrammar = traceGrammar) {
    const { input, fireMap, fireDoc } = fakeInput();
    const refs = fakeRefs();
    grammar.bind(input, refs);
    return { refs, ctx: refs, input, fireMap, fireDoc };
}

describe("traceGrammar", () => {
    it("the primary press creates AND places the polygon draft with its first vertex locked", () => {
        const { refs, ctx, fireMap } = harness();

        fireMap("mousedown", mouse([10, 20], { x: 0, y: 0 }));

        expect(refs.getDraft()).toBeDefined();
        expect(refs.getDraft()!.geometry.getAllPos()).toEqual([10, 20]);
        expect(ctx.onPlace).toHaveBeenCalledWith(refs.getDraft());
        expect(refs.getLocked()).toBe(1);
        expect(refs.onUpdate).toHaveBeenCalledTimes(1);
    });

    it("a move appends a vertex only once the pointer has travelled the throttle distance on screen", () => {
        const { refs, fireMap } = harness();
        fireMap("mousedown", mouse([0, 0], { x: 0, y: 0 }));

        // 4px: under the default 10px throttle — nothing captured.
        fireMap("mousemove", mouse([0.1, 0], { x: 4, y: 0 }));
        expect(refs.getDraft()!.geometry.getAllPos()).toEqual([0, 0]);
        expect(refs.getLocked()).toBe(1);

        // 12px from the last CAPTURED vertex, not from the last move.
        fireMap("mousemove", mouse([1, 0], { x: 12, y: 0 }));
        expect(refs.getDraft()!.geometry.getAllPos()).toEqual([0, 0, 1, 0]);
        expect(refs.getLocked()).toBe(2);

        // The throttle is measured from (12, 0) now: 8px more is not enough…
        fireMap("mousemove", mouse([2, 0], { x: 20, y: 0 }));
        expect(refs.getLocked()).toBe(2);
        // …but a diagonal that clears it is.
        fireMap("mousemove", mouse([2, 2], { x: 20, y: 10 }));
        expect(refs.getDraft()!.geometry.getAllPos()).toEqual([0, 0, 1, 0, 2, 2]);
        expect(refs.getLocked()).toBe(3);
    });

    it("honours a tool's own screenThrottlePx", () => {
        const { refs, fireMap } = harness(
            createTraceGrammar({
                create: (cursor) => Polygon.create({ positions: cursor }),
                screenThrottlePx: 30,
            }),
        );
        fireMap("mousedown", mouse([0, 0], { x: 0, y: 0 }));
        fireMap("mousemove", mouse([1, 0], { x: 20, y: 0 }));
        expect(refs.getLocked()).toBe(1);
        fireMap("mousemove", mouse([1, 0], { x: 30, y: 0 }));
        expect(refs.getLocked()).toBe(2);
    });

    it("the release completes with the captured ring — heard at document level, once", () => {
        const { refs, input, fireMap, fireDoc } = harness();
        fireMap("mousedown", mouse([0, 0], { x: 0, y: 0 }));
        fireMap("mousemove", mouse([1, 0], { x: 40, y: 0 }));
        fireMap("mousemove", mouse([1, 1], { x: 40, y: 40 }));

        fireDoc("mouseup", { button: 0 });

        expect(refs.onComplete).toHaveBeenCalledTimes(1);
        expect(refs.onComplete).toHaveBeenCalledWith(refs.getDraft());
        // The click the map may synthesize from the press–release pair is dropped first.
        expect(input.consumeNextClick).toHaveBeenCalledTimes(1);
        expect(input.consumeNextClick.mock.invocationCallOrder[0]).toBeLessThan(
            refs.onComplete.mock.invocationCallOrder[0],
        );
        // A second release (or a stray one) completes nothing.
        fireDoc("mouseup", { button: 0 });
        expect(refs.onComplete).toHaveBeenCalledTimes(1);
    });

    it("moves before a press and releases without one are ignored", () => {
        const { refs, ctx, fireMap, fireDoc } = harness();

        fireMap("mousemove", mouse([1, 0], { x: 40, y: 0 }));
        fireDoc("mouseup", { button: 0 });

        expect(refs.getDraft()).toBeUndefined();
        expect(ctx.onPlace).not.toHaveBeenCalled();
        expect(refs.onComplete).not.toHaveBeenCalled();
    });

    it("buttons other than the primary neither start nor complete a trace", () => {
        const { refs, fireMap, fireDoc } = harness();

        fireMap("mousedown", mouse([0, 0], { x: 0, y: 0 }, 2));
        expect(refs.getDraft()).toBeUndefined();

        fireMap("mousedown", mouse([0, 0], { x: 0, y: 0 }, 0));
        fireMap("mousemove", mouse([1, 0], { x: 40, y: 0 }));
        fireDoc("mouseup", { button: 2 });
        expect(refs.onComplete).not.toHaveBeenCalled();

        fireDoc("mouseup", { button: 0 });
        expect(refs.onComplete).toHaveBeenCalledTimes(1);
    });

    it("a right-click mid-press abandons the trace; the release that follows completes nothing", () => {
        const { refs, fireMap, fireDoc } = harness();
        fireMap("mousedown", mouse([0, 0], { x: 0, y: 0 }));
        fireMap("mousemove", mouse([1, 0], { x: 40, y: 0 }));

        const menu = mouse([1, 0], { x: 40, y: 0 }, 2);
        fireMap("contextmenu", menu);

        // The map context menu stays shut while the session owns the pointer.
        expect(menu.preventDefault).toHaveBeenCalled();
        expect(refs.onAbort).toHaveBeenCalledTimes(1);

        fireDoc("mouseup", { button: 0 });
        expect(refs.onComplete).not.toHaveBeenCalled();
    });

    it("a right-click with no trace live only keeps the menu shut", () => {
        const { refs, fireMap } = harness();
        const menu = mouse([1, 0], { x: 40, y: 0 }, 2);
        fireMap("contextmenu", menu);
        expect(menu.preventDefault).toHaveBeenCalled();
        expect(refs.onAbort).not.toHaveBeenCalled();
    });
});

describe("rectangleGrammar", () => {
    function drawTo(corners: [number, number][], grammar: DrawGrammar = rectangleGrammar) {
        const { input, fireMap } = fakeInput();
        const refs = fakeRefs();
        grammar.bind(input, refs);
        fireMap("mousemove", mouse(corners[0], { x: 0, y: 0 }));
        fireMap("click", mouse(corners[0], { x: 0, y: 0 }));
        fireMap("mousemove", mouse(corners[1], { x: 0, y: 0 }));
        fireMap("click", mouse(corners[1], { x: 0, y: 0 }));
        return { refs, fireMap };
    }
    const ring = (el: Element) => el.geometry.getAllPos() as number[];
    const signedArea = (flat: number[]) => {
        let sum = 0;
        for (let i = 0; i < flat.length; i += 2) {
            const j = (i + 2) % flat.length;
            sum += flat[i] * flat[j + 1] - flat[j] * flat[i + 1];
        }
        return sum;
    };

    it("keeps the drawn edge at positions 0 and 1 and winds counter-clockwise, on either side", () => {
        for (const cursor of [
            [1, 1],
            [1, -1],
        ] as [number, number][]) {
            const { refs, fireMap } = drawTo([
                [0, 0],
                [2, 0],
            ]);
            fireMap("mousemove", mouse(cursor, { x: 0, y: 0 }));
            fireMap("mousemove", mouse([cursor[0], cursor[1] * 2], { x: 0, y: 0 }));
            const positions = ring(refs.getDraft()!);
            expect(positions).toHaveLength(8);
            const edge = [positions.slice(0, 2), positions.slice(2, 4)].map((p) =>
                p.map((v) => Math.round(v * 1e6) / 1e6),
            );
            expect(edge).toEqual(
                expect.arrayContaining([
                    [0, 0],
                    [2, 0],
                ]),
            );
            expect(signedArea(positions)).toBeGreaterThan(0);
        }
    });

    it("two drafts in flight do not share state", () => {
        const first = drawTo([
            [0, 0],
            [2, 0],
        ]);
        const second = drawTo([
            [10, 10],
            [10, 12],
        ]);
        first.fireMap("mousemove", mouse([1, 1], { x: 0, y: 0 }));
        second.fireMap("mousemove", mouse([11, 11], { x: 0, y: 0 }));
        first.fireMap("mousemove", mouse([1, 1.5], { x: 0, y: 0 }));

        const a = ring(first.refs.getDraft()!);
        expect(Math.max(...a.filter((_, i) => i % 2 === 0))).toBeLessThan(3);
        const b = ring(second.refs.getDraft()!);
        expect(Math.min(...b.filter((_, i) => i % 2 === 0))).toBeGreaterThanOrEqual(9.99);
        expect(Rectangle.is(first.refs.getDraft()!)).toBe(true);
    });
});

describe("sequentialGrammar", () => {
    function sequential(grammar: DrawGrammar = polygonGrammar) {
        const { input, fireMap, fireEnter } = fakeInput();
        const refs = fakeRefs();
        grammar.bind(input, refs);
        const at = (lngLat: [number, number]) => mouse(lngLat, { x: 0, y: 0 });
        const moveAndClick = (lngLat: [number, number]) => {
            fireMap("mousemove", at(lngLat));
            fireMap("click", at(lngLat));
        };
        /** A double-click: the browser's two clicks, then the dblclick. */
        const doubleClick = (lngLat: [number, number]) => {
            fireMap("mousemove", at(lngLat));
            fireMap("click", at(lngLat));
            fireMap("click", at(lngLat));
            fireMap("dblclick", at(lngLat));
        };
        const positions = () => [...(refs.getDraft()?.geometry.getAllPos() ?? [])];
        return { refs, fireMap, fireEnter, at, moveAndClick, doubleClick, positions };
    }

    it("a double-click's two clicks lock its point once", () => {
        const { refs, moveAndClick, doubleClick } = sequential();
        moveAndClick([0, 0]);
        doubleClick([1, 0]);

        expect(refs.getLocked()).toBe(2);
    });

    it("a double-click does not complete a polygon that has fewer than three points", () => {
        const { refs, fireMap, at, moveAndClick, doubleClick, positions } = sequential();
        moveAndClick([0, 0]);
        doubleClick([1, 0]);

        expect(refs.onComplete).not.toHaveBeenCalled();
        // Still drawing: the next point follows the cursor after the two placed ones.
        fireMap("mousemove", at([1, 1]));
        expect(positions()).toEqual([0, 0, 1, 0, 1, 1]);
    });

    it("a double-click completes a polygon once it has three points", () => {
        const { refs, moveAndClick, doubleClick, positions } = sequential();
        moveAndClick([0, 0]);
        moveAndClick([1, 0]);
        doubleClick([1, 1]);

        expect(refs.onComplete).toHaveBeenCalledOnce();
        expect(positions()).toEqual([0, 0, 1, 0, 1, 1]);
    });

    it("Enter completes only when the draft holds the minimum, counting the cursor's point", () => {
        const { refs, fireMap, fireEnter, at, moveAndClick } = sequential();
        moveAndClick([0, 0]);
        moveAndClick([1, 0]);

        // Two placed, the cursor not yet moved: two points — not a polygon.
        fireEnter();
        expect(refs.onComplete).not.toHaveBeenCalled();

        fireMap("mousemove", at([1, 1]));
        fireEnter();
        expect(refs.onComplete).toHaveBeenCalledOnce();
    });

    it("Enter completes a draw whose max rules out the double-click", () => {
        const { refs, fireMap, fireEnter, at, moveAndClick } = sequential(
            createSequentialGrammar({
                min: 3,
                max: 5,
                create: (cursor) => Polygon.create({ positions: cursor }),
            }),
        );
        moveAndClick([0, 0]);
        moveAndClick([1, 0]);
        fireMap("mousemove", at([1, 1]));

        fireEnter();

        expect(refs.onComplete).toHaveBeenCalledOnce();
    });

    it("a draft created with all its points up front still locks one per click", () => {
        const { refs, moveAndClick } = sequential(arrowGrammar);
        moveAndClick([0, 0]);
        moveAndClick([1, 1]);

        expect(refs.onComplete).toHaveBeenCalledOnce();
    });
});
