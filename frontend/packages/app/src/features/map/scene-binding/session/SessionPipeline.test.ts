// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it } from "vitest";
import { PolygonGeometry, type DraftObject } from "@opencop/elements";
import type { ObjectRef, StoreEvent } from "@opencop/ingest";
import { SESSION_STORE_ID, type ToolDescriptor } from "@opencop/tools";
import type { Primitive, Scene } from "@opencop/scene";
import type { MeasurementSystem } from "@opencop/utils";
import { lassoDescriptor } from "~/plugins/lasso/lasso.descriptor";
import { LASSO_PRODUCT_TYPE } from "~/plugins/lasso/lasso-trace.model";
import { measureDescriptor } from "~/plugins/measure/measure.descriptor";
import { measurePrimitives } from "~/plugins/measure/measure.mapper";
import { circleMeasurement } from "~/plugins/measure/measurement.model";
import type { RenderSink, RenderState, Selector } from "../RenderSink";
import { LABEL } from "../elements/element-pipeline.util";
import { codec } from "../ref-codec";
import { SessionPipeline } from "./SessionPipeline";
import type { SessionObject, SessionStore } from "./SessionStore";

/** A session store whose one object the test sets, emitting as the real one does. */
function sessionStore() {
    let current: SessionObject | null = null;
    const listeners: Record<StoreEvent, Set<(objects: SessionObject[]) => void>> = {
        add: new Set(),
        update: new Set(),
        remove: new Set(),
    };
    const store: SessionStore = {
        type: SESSION_STORE_ID,
        get: (key) => (current?.key === key ? current : undefined),
        getAll: () => (current ? [current] : []),
        ref: (object): ObjectRef => ({ type: SESSION_STORE_ID, key: object.key }),
        on: (event, listener) => void listeners[event].add(listener),
        off: (event, listener) => void listeners[event].delete(listener),
        connect: () => () => {},
    };
    const set = (next: Omit<SessionObject, "key"> | null) => {
        const prev = current;
        current = next && { key: "current", ...next };
        const [event, object] = current ? [prev ? "update" : "add", current] : ["remove", prev];
        if (object) listeners[event as StoreEvent].forEach((listener) => listener([object]));
    };
    return { store, set };
}

/** A sink holding only the measurement unit, which the test changes. */
function unitSink(initial: MeasurementSystem = "METRIC") {
    let state = { units: { measurement: initial } } as RenderState;
    const subscribers = new Set<{
        selector: Selector;
        listener: (n: unknown, p: unknown) => void;
    }>();
    const sink = {
        get: () => state,
        subscribe: (selector: Selector, listener: (n: unknown, p: unknown) => void) => {
            const subscriber = { selector, listener };
            subscribers.add(subscriber);
            return () => subscribers.delete(subscriber);
        },
    } as unknown as RenderSink;
    const setUnit = (measurement: MeasurementSystem) => {
        const prev = state;
        state = { ...state, units: { ...state.units, measurement } };
        for (const { selector, listener } of subscribers) {
            const [next, last] = [selector.select(state), selector.select(prev)];
            if (next !== last) listener(next, last);
        }
    };
    return { sink, setUnit };
}

function fakeScene() {
    const drawn = new Map<string, Primitive>();
    const scene = {
        get: (id: string) => drawn.get(id),
        add: (primitives: Primitive | Iterable<Primitive>) => {
            for (const primitive of Symbol.iterator in primitives ? primitives : [primitives])
                drawn.set(primitive.id, primitive);
        },
        update: (primitive: Primitive) => void drawn.set(primitive.id, primitive),
        remove: (ids: string | Iterable<string>) => {
            for (const id of typeof ids === "string" ? [ids] : ids) drawn.delete(id);
        },
    } as unknown as Scene;
    return { scene, drawn };
}

const pipelines: SessionPipeline[] = [];

function setup(unit?: MeasurementSystem) {
    const { store, set } = sessionStore();
    const { sink, setUnit } = unitSink(unit);
    const { scene, drawn } = fakeScene();
    const pipeline = new SessionPipeline({ scene, store, sink });
    pipeline.initialize();
    pipelines.push(pipeline);
    const nanoids = () => [...drawn.keys()].map((id) => codec.decode(id)!.nanoid);
    const texts = () =>
        [...drawn.values()].flatMap((p) => (p.style.type === "text" ? [p.style.text] : []));
    return { pipeline, set, setUnit, drawn, nanoids, texts };
}

const product = (tool: ToolDescriptor, draft: DraftObject) => ({
    product: { tool, draft },
    construction: null,
});

/** A measured circle, 10 km in radius. */
function measuredCircle() {
    const circle = circleMeasurement([0, 0]);
    circle.geometry.setRadius(10_000);
    return circle;
}

const draftOf = (type: string, positions: number[]): DraftObject => ({
    key: "draft",
    type,
    geometry: new PolygonGeometry(positions, 2),
});

afterEach(() => {
    pipelines.splice(0).forEach((pipeline) => pipeline.dispose());
});

describe("SessionPipeline", () => {
    it("draws a measurement with the measure tool's mapper in the sink's unit", () => {
        const { set, nanoids, texts } = setup("IMPERIAL");
        const circle = measuredCircle();

        set(product(measureDescriptor, circle));

        const expected = measurePrimitives(circle, "IMPERIAL");
        expect(nanoids()).toEqual(expected.map((p) => p.id));
        expect(texts()).toEqual(
            expected.flatMap((p) => (p.style.type === "text" ? [p.style.text] : [])),
        );
    });

    it("draws a lasso trace as marching ants", () => {
        const { set, drawn } = setup();

        set(product(lassoDescriptor, draftOf(LASSO_PRODUCT_TYPE, [0, 0, 1, 1])));

        expect([...drawn.values()].map((p) => p.style.type)).toEqual(["marching-ants"]);
    });

    it("draws nothing for a tool that declares no product mapper", () => {
        const { set, drawn } = setup();
        const region: ToolDescriptor = {
            id: "test:region",
            label: "region",
            icon: () => null,
            produces: { store: SESSION_STORE_ID, type: "test:region" },
        };

        set(product(region, draftOf("test:region", [0, 0, 1, 1])));

        expect(drawn.size).toBe(0);
    });

    it("draws the construction path dashed, at ground level", () => {
        const { set, drawn } = setup();

        set({ product: null, construction: [0, 0, 1, 1] });

        expect([...drawn.values()]).toEqual([
            {
                id: codec.encode({
                    object: { type: SESSION_STORE_ID, key: "current" },
                    nanoid: "construction",
                }),
                geometry: { type: "path", positions: [0, 0, 0, 1, 1, 0] },
                style: {
                    type: "path",
                    strokeColor: [123, 116, 116, 255],
                    strokeWidth: 3,
                    strokeDash: [3, 3],
                },
            },
        ]);
    });

    it("draws the product and the construction path together", () => {
        const { set, nanoids } = setup();

        set({
            product: { tool: lassoDescriptor, draft: draftOf(LASSO_PRODUCT_TYPE, [0, 0, 1, 1]) },
            construction: [0, 0, 1, 1],
        });

        expect(nanoids()).toEqual(["trace", "construction"]);
    });

    it("pins every label it draws", () => {
        const { set, drawn } = setup();

        set(product(measureDescriptor, measuredCircle()));

        const filters = [...drawn.values()].map((p) => [p.style.type, p.filter]);
        expect(filters.filter(([type]) => type === "text").length).toBeGreaterThan(0);
        for (const [type, filter] of filters)
            expect(filter).toEqual(type === "text" ? [1, LABEL.pinned] : undefined);
    });

    it("relabels the measurement when the measurement unit changes", () => {
        const { set, setUnit, texts } = setup("METRIC");
        const circle = measuredCircle();
        set(product(measureDescriptor, circle));
        const metric = texts();

        setUnit("IMPERIAL");

        expect(texts()).not.toEqual(metric);
        expect(texts()).toEqual(
            measurePrimitives(circle, "IMPERIAL").flatMap((p) =>
                p.style.type === "text" ? [p.style.text] : [],
            ),
        );
    });

    it("erases what it drew when the session ends", () => {
        const { set, drawn } = setup();
        set(product(measureDescriptor, measuredCircle()));

        set(null);

        expect(drawn.size).toBe(0);
    });

    it("leaves what it drew and stops following the unit when disposed", () => {
        const { pipeline, set, setUnit, drawn } = setup();
        set(product(measureDescriptor, measuredCircle()));
        const before = new Map(drawn);

        pipeline.dispose();
        setUnit("IMPERIAL");

        expect(drawn).toEqual(before);
    });
});
