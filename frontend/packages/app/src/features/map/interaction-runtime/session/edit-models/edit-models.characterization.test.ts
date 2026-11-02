// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it, vi } from "vitest";
import { WebMercatorViewport } from "@deck.gl/core";
import * as turf from "@turf/turf";
import {
    Circle,
    Line,
    Rectangle,
    type CircleElement,
    type Element,
    type RectangleElement,
} from "@opencop/elements";
import { circleEditModel, rectangleEditModel, type EditModel } from "@opencop/tools";
import { resetInteractionForTest, setSessionForTest } from "~/test/interaction";
import { getInteractionState, emptySub, type EditSession } from "../interaction.store";
import { deriveCursor } from "../../arbitration/derive/cursor";
import { deriveLocks } from "../../arbitration/derive/locks";
import { bindEditSession, type EditSessionHost } from "../edit-session";
import { genericDragModel } from "./generic-drag.model";

/**
 * Behaviour pins for the edit half of the lifecycle. They drive the
 * `EditModel` interpreter with plain pointer input and assert the operator-
 * visible outcomes: the grab tolerance, the cursor sequence, the pan lock held
 * for exactly the drag, and the geometry each drag produces.
 *
 * Where the old handler read something the interpreter derives instead
 * (cursor and pan lock were manager calls; they are now `deriveCursor` and
 * `deriveLocks` over the session's drag substate), the pin reads the
 * derivation. Body hits used to come from deck's hover; they are geometric now.
 */

type Handler = (...args: never[]) => void;

const view = new WebMercatorViewport({
    width: 1000,
    height: 800,
    longitude: -104.99,
    latitude: 39.74,
    zoom: 14,
});

const lngLatAt = (x: number, y: number): [number, number] =>
    view.unproject([x, y]) as [number, number];
const screenOf = (lngLat: readonly number[]): [number, number] =>
    view.project([lngLat[0], lngLat[1]]) as [number, number];

function recorder() {
    const handlers = new Map<string, Handler[]>();
    const bind = (kind: string) => (event: string, fn: Handler) => {
        const key = `${kind}:${event}`;
        handlers.set(key, [...(handlers.get(key) ?? []), fn]);
        return { event, unsub: vi.fn() };
    };
    const sources = {
        onMap: bind("map"),
        onDocument: bind("document"),
    } as unknown as EditSessionHost["sources"];
    const project = (lngLat: [number, number]) => screenOf(lngLat);
    const fire = (key: string, ...args: unknown[]) => {
        for (const h of handlers.get(key) ?? []) (h as (...a: unknown[]) => void)(...args);
    };
    return { sources, project, fire };
}

/** A maplibre-shaped mouse event at a screen position, primary button. */
function mouseAt(x: number, y: number) {
    const [lng, lat] = lngLatAt(x, y);
    return { lngLat: { lng, lat, toArray: () => [lng, lat] }, originalEvent: { button: 0 } };
}

/** Place a custody-less edit session over `element` and bind the model's interpreter. */
function edit(element: Element, model: EditModel) {
    const session: EditSession = {
        kind: "edit",
        descriptorId: null,
        epoch: 1,
        product: element,
        custody: false,
        params: {},
        isNew: true,
        sub: emptySub(),
    };
    setSessionForTest(session);
    const rec = recorder();
    bindEditSession(model, {
        sources: rec.sources,
        project: rec.project,
        getDraft: () => element,
    });
    const cursor = () =>
        deriveCursor(getInteractionState().session, () => undefined, { hoveringPickable: false });
    const panLocked = () =>
        deriveLocks(getInteractionState().session, () => undefined, { hoveringElement: false })
            .panLocked;
    return { ...rec, cursor, panLocked };
}

const positions = (el: Element): number[] => [...el.geometry.getAllPos()];

afterEach(() => resetInteractionForTest());

describe("generic drag — vertex and body drags of a polyline", () => {
    function lineAcross(): Element {
        const el = Line.create({ positions: lngLatAt(300, 400) });
        el.geometry.setAllPos([...lngLatAt(300, 400), ...lngLatAt(700, 400)]);
        return el;
    }

    it("grabs a vertex within 15 px of it and not beyond", () => {
        const el = lineAcross();
        const { fire, cursor } = edit(el, genericDragModel);

        fire("map:mousemove", mouseAt(300 + 14, 400));
        expect(cursor()).toBe("move");

        fire("map:mousemove", mouseAt(300 + 16, 400));
        expect(cursor()).toBe("grab"); // still on the line's body
    });

    it("shows grab over the body, move near a vertex, default off the element", () => {
        const el = lineAcross();
        const { fire, cursor } = edit(el, genericDragModel);

        fire("map:mousemove", mouseAt(500, 300)); // 100 px off the line
        expect(cursor()).toBe("default");

        fire("map:mousemove", mouseAt(500, 400));
        expect(cursor()).toBe("grab");

        fire("map:mousemove", mouseAt(700, 400));
        expect(cursor()).toBe("move");
    });

    it("a vertex drag moves only that vertex, locks pan for the drag, and releases it on mouseup", () => {
        const el = lineAcross();
        const before = positions(el);
        const { fire, cursor, panLocked } = edit(el, genericDragModel);

        fire("map:mousedown", mouseAt(700, 400));
        expect(panLocked()).toBe(true);
        expect(cursor()).toBe("move");

        fire("map:mousemove", mouseAt(720, 380));
        const after = positions(el);
        expect(after.slice(0, 2)).toEqual(before.slice(0, 2));
        expect(after.slice(2)).toEqual([...lngLatAt(720, 380)]);

        fire("map:mouseup", mouseAt(720, 380));
        expect(panLocked()).toBe(false);
    });

    it("a body drag translates every vertex by the same offset", () => {
        const el = lineAcross();
        const before = positions(el);
        const { fire, cursor, panLocked } = edit(el, genericDragModel);

        fire("map:mousedown", mouseAt(500, 400));
        expect(cursor()).toBe("grabbing");
        expect(panLocked()).toBe(true);

        fire("map:mousemove", mouseAt(560, 430));
        fire("map:mouseup", mouseAt(560, 430));

        const after = positions(el);
        const [a0, a1] = [screenOf(after.slice(0, 2)), screenOf(after.slice(2, 4))];
        const [b0, b1] = [screenOf(before.slice(0, 2)), screenOf(before.slice(2, 4))];
        expect(a0[0] - b0[0]).toBeCloseTo(60, 0);
        expect(a0[1] - b0[1]).toBeCloseTo(30, 0);
        expect(a1[0] - b1[0]).toBeCloseTo(60, 0);
        expect(a1[1] - b1[1]).toBeCloseTo(30, 0);
        expect(panLocked()).toBe(false);
    });

    it("with `translate: false` the body is no drag target: no grab cursor, no pan lock, no move", () => {
        const el = lineAcross();
        const before = positions(el);
        const { fire, cursor, panLocked } = edit(el, { ...genericDragModel, translate: false });

        fire("map:mousemove", mouseAt(500, 400));
        expect(cursor()).toBe("default");

        fire("map:mousedown", mouseAt(500, 400));
        expect(panLocked()).toBe(false);
        fire("map:mousemove", mouseAt(560, 430));
        fire("map:mouseup", mouseAt(560, 430));
        expect(positions(el)).toEqual(before);

        // Handles still drag.
        fire("map:mousedown", mouseAt(700, 400));
        fire("map:mousemove", mouseAt(720, 380));
        expect(positions(el).slice(2)).toEqual([...lngLatAt(720, 380)]);
    });

    it("a release heard at document level ends the drag too", () => {
        const el = lineAcross();
        const { fire, panLocked } = edit(el, genericDragModel);
        fire("map:mousedown", mouseAt(700, 400));
        expect(panLocked()).toBe(true);

        fire("document:mouseup", { button: 0 });

        expect(panLocked()).toBe(false);
    });
});

describe("circle — boundary resize and origin drag", () => {
    function circleAt(x: number, y: number, radiusM: number): CircleElement {
        return Circle.create({ origin: lngLatAt(x, y), radius: radiusM }) as CircleElement;
    }

    /** Screen position on the ring at a compass bearing. */
    function onRing(el: CircleElement, bearing: number, extraPx = 0): [number, number] {
        const p = turf.destination(el.geometry.getOrigin(), el.geometry.getRadius(), bearing, {
            units: "meters",
        }).geometry.coordinates as [number, number];
        const s = screenOf(p);
        return [s[0] + extraPx, s[1]];
    }

    it("grabs the ring within 15 px anywhere along it, not only at fixed vertices", () => {
        const el = circleAt(500, 400, 400);
        const { fire, cursor } = edit(el, circleEditModel as EditModel);

        for (const bearing of [0, 37, 90, 200, 315]) {
            const [x, y] = onRing(el, bearing);
            fire("map:mousemove", mouseAt(x, y));
            expect(cursor()).toBe("move");
        }

        const [fx, fy] = onRing(el, 90, -20); // 20 px inside the ring: the body
        fire("map:mousemove", mouseAt(fx, fy));
        expect(cursor()).toBe("grab");
    });

    it("dragging the ring resizes to the cursor distance, floored at 10 m", () => {
        const el = circleAt(500, 400, 400);
        const { fire, cursor, panLocked } = edit(el, circleEditModel as EditModel);

        const [x, y] = onRing(el, 90);
        fire("map:mousedown", mouseAt(x, y));
        expect(panLocked()).toBe(true);
        expect(cursor()).toBe("move");

        fire("map:mousemove", mouseAt(x + 200, y));
        const expected = turf.distance(el.geometry.getOrigin() as number[], lngLatAt(x + 200, y), {
            units: "meters",
        });
        expect(el.geometry.getRadius()).toBeCloseTo(expected, 3);

        fire("map:mousemove", mouseAt(500, 400));
        expect(el.geometry.getRadius()).toBe(10);

        fire("map:mouseup", mouseAt(500, 400));
        expect(panLocked()).toBe(false);
    });

    it("dragging the body moves the origin and keeps the radius", () => {
        const el = circleAt(500, 400, 400);
        const { fire, cursor, panLocked } = edit(el, circleEditModel as EditModel);

        fire("map:mousedown", mouseAt(500, 400));
        expect(cursor()).toBe("grabbing");
        expect(panLocked()).toBe(true);

        fire("map:mousemove", mouseAt(540, 430));
        fire("map:mouseup", mouseAt(540, 430));

        const origin = screenOf(el.geometry.getOrigin());
        expect(origin[0]).toBeCloseTo(540, 0);
        expect(origin[1]).toBeCloseTo(430, 0);
        expect(el.geometry.getRadius()).toBe(400);
        expect(panLocked()).toBe(false);
    });
});

describe("rectangle — a corner drag keeps the rectangle rectangular", () => {
    function rectangle(): RectangleElement {
        const el = Rectangle.create({ positions: lngLatAt(400, 300) }) as RectangleElement;
        el.geometry.setAllPos([
            ...lngLatAt(400, 300),
            ...lngLatAt(600, 300),
            ...lngLatAt(600, 500),
            ...lngLatAt(400, 500),
        ]);
        return el;
    }

    function corners(el: Element): Array<[number, number]> {
        const p = positions(el);
        const out: Array<[number, number]> = [];
        for (let i = 0; i < p.length; i += 2) out.push(screenOf(p.slice(i, i + 2)));
        return out;
    }

    it("dragging one corner leaves the opposite corner fixed and every angle right", () => {
        const el = rectangle();
        const { fire, cursor, panLocked } = edit(el, rectangleEditModel as EditModel);

        fire("map:mousedown", mouseAt(400, 300));
        expect(cursor()).toBe("move");
        expect(panLocked()).toBe(true);
        fire("map:mousemove", mouseAt(350, 260));
        fire("map:mousemove", mouseAt(330, 240));
        fire("map:mouseup", mouseAt(330, 240));

        const c = corners(el);
        expect(c).toHaveLength(4);
        expect(c.find((p) => Math.abs(p[0] - 600) < 1 && Math.abs(p[1] - 500) < 1)).toBeDefined();
        expect(c.find((p) => Math.abs(p[0] - 330) < 1 && Math.abs(p[1] - 240) < 1)).toBeDefined();
        for (let i = 0; i < 4; i++) {
            const prev = c[(i + 3) % 4];
            const cur = c[i];
            const next = c[(i + 1) % 4];
            const u = [prev[0] - cur[0], prev[1] - cur[1]];
            const v = [next[0] - cur[0], next[1] - cur[1]];
            expect(Math.abs(u[0] * v[0] + u[1] * v[1])).toBeLessThan(1);
        }
        expect(panLocked()).toBe(false);
    });

    it("a body drag translates all four corners together", () => {
        const el = rectangle();
        const before = corners(el);
        const { fire } = edit(el, rectangleEditModel as EditModel);

        fire("map:mousedown", mouseAt(500, 400));
        fire("map:mousemove", mouseAt(530, 420));
        fire("map:mouseup", mouseAt(530, 420));

        const after = corners(el);
        for (let i = 0; i < 4; i++) {
            expect(after[i][0] - before[i][0]).toBeCloseTo(30, 0);
            expect(after[i][1] - before[i][1]).toBeCloseTo(20, 0);
        }
    });
});
