// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { Line, Polygon, type DraftObject } from "@opencop/elements";
import {
    ELEMENT_STORE_ID,
    SESSION_STORE_ID,
    polygonGrammar,
    traceGrammar,
    vertexControlPoints,
    createSequentialGrammar,
    type ToolDescriptor,
} from "@opencop/tools";
import { measureDescriptor } from "~/plugins/measure/measure.descriptor";
import { measureHandlesModel } from "~/plugins/measure/measure-handles.model";
import { circleMeasurement } from "~/plugins/measure/measurement.model";
import type { Session } from "../../interaction-runtime/session/session";
import { sessionConstructionPath, sessionControlPoints, sessionProduct } from "./session-render";

const RING = [-104.99, 39.74, -104.98, 39.74, -104.98, 39.75];

const descriptor = (id: string, extra: Partial<ToolDescriptor> = {}): ToolDescriptor => ({
    id,
    label: id,
    icon: () => null,
    draw: { grammar: polygonGrammar },
    ...extra,
});

const lasso = descriptor("lasso", { draw: { grammar: traceGrammar }, onComplete: () => "exit" });
const polygon = descriptor("polygon", { produces: { store: ELEMENT_STORE_ID, type: "polygon" } });
const region = descriptor("region", { produces: { store: SESSION_STORE_ID, type: "test:region" } });
const boxed = descriptor("boxed", { produces: { store: "test:boxes", type: "box" } });
const needsThree = descriptor("needsThree", {
    produces: { store: ELEMENT_STORE_ID, type: "graphic" },
    draw: {
        grammar: createSequentialGrammar({
            min: 3,
            construction: "path",
            create: (cursor) => Line.create({ positions: [...cursor] }),
        }),
    },
});
const plainThree = descriptor("plainThree", {
    produces: { store: ELEMENT_STORE_ID, type: "line" },
    draw: {
        grammar: createSequentialGrammar({
            min: 3,
            create: (cursor) => Line.create({ positions: [...cursor] }),
        }),
    },
});
const tools: Record<string, ToolDescriptor> = {
    [measureDescriptor.id]: measureDescriptor,
    [lasso.id]: lasso,
    [polygon.id]: polygon,
    [region.id]: region,
    [boxed.id]: boxed,
    [needsThree.id]: needsThree,
    [plainThree.id]: plainThree,
};
const resolve = (id: string) => tools[id];

/** A finished circle measurement, 10 km in radius. */
function measuredCircle() {
    const circle = circleMeasurement([0, 0]);
    circle.geometry.setRadius(10_000);
    return circle;
}

function drawing(
    descriptorId: string,
    element: DraftObject,
    placed: boolean,
    custody = false,
): Session {
    return {
        kind: "draw",
        descriptorId,
        epoch: 1,
        params: {},
        custody,
        locked: placed ? 1 : 0,
        draft: { object: element, placed },
    } as Session;
}

function editing(descriptorId: string | null, element: DraftObject, custody = false): Session {
    return {
        kind: "edit",
        descriptorId,
        epoch: 1,
        product: element,
        ref: { objectId: element.key } as never,
        custody,
        params: {},
        isNew: true,
        sub: { hotspot: null, dragging: null },
    } as Session;
}

describe("sessionControlPoints", () => {
    it("draws the edit model's control points, not the geometry's vertices", () => {
        // A measured circle's handles are its origin plus four radius handles —
        // none of them is a vertex, so drawing from geometry would show the wrong dots.
        const circle = measuredCircle();
        const expected = measureHandlesModel.controlPoints(circle);

        const points = sessionControlPoints(editing(measureDescriptor.id, circle), resolve);

        expect(points).toEqual(expected);
        expect(points).not.toEqual(vertexControlPoints(circle));
    });

    it("falls back to the generic vertex model, as the drag loop does", () => {
        const shape = Polygon.create({ positions: RING });

        expect(sessionControlPoints(drawing(polygon.id, shape, true), resolve)).toEqual(
            vertexControlPoints(shape),
        );
        // An existing element with no owning tool edits under the generic model too.
        expect(sessionControlPoints(editing(null, shape), resolve)).toEqual(
            vertexControlPoints(shape),
        );
    });

    it("draws nothing before the first click", () => {
        const shape = Polygon.create({ positions: RING });

        expect(sessionControlPoints(drawing(polygon.id, shape, false), resolve)).toEqual([]);
    });

    it("draws nothing for a tool whose product is never edited", () => {
        const trace = Polygon.create({ positions: RING });

        expect(sessionControlPoints(drawing(lasso.id, trace, true), resolve)).toEqual([]);
    });

    it("draws nothing while idle", () => {
        expect(sessionControlPoints({ kind: "idle" }, resolve)).toEqual([]);
    });
});

describe("sessionProduct", () => {
    it("is the tool and its draft for a session-store product", () => {
        const circle = measuredCircle();

        expect(sessionProduct(drawing(measureDescriptor.id, circle, true), resolve)).toEqual({
            tool: measureDescriptor,
            draft: circle,
        });
    });

    it("is the finished product of an edit session", () => {
        const shape = Polygon.create({ positions: RING });

        expect(sessionProduct(editing(region.id, shape), resolve)).toEqual({
            tool: region,
            draft: shape,
        });
    });

    it("is nothing before the first click", () => {
        const shape = Polygon.create({ positions: RING });

        expect(sessionProduct(drawing(region.id, shape, false), resolve)).toBeNull();
    });

    it("is nothing for an element-store tool, whose draft draws through the element store", () => {
        const shape = Polygon.create({ positions: RING });

        expect(sessionProduct(drawing(polygon.id, shape, true, true), resolve)).toBeNull();
    });

    it("is nothing for a tool whose product another store holds as its draft", () => {
        const shape = Polygon.create({ positions: RING });

        expect(sessionProduct(drawing(boxed.id, shape, true), resolve)).toBeNull();
    });

    it("is nothing for a tool that produces nothing, or while idle", () => {
        const trace = Polygon.create({ positions: RING });

        expect(sessionProduct(drawing(lasso.id, trace, true), resolve)).toBeNull();
        expect(sessionProduct({ kind: "idle" }, resolve)).toBeNull();
    });
});

describe("sessionConstructionPath", () => {
    const twoPoints = () => Line.create({ positions: [0, 0, 1, 1] });
    const threePoints = () => Line.create({ positions: [0, 0, 1, 1, 2, 0] });

    it("is the draft's positions while it has fewer points than the grammar's minimum", () => {
        expect(sessionConstructionPath(drawing(needsThree.id, twoPoints(), true), resolve)).toEqual(
            [0, 0, 1, 1],
        );
    });

    it("is nothing once the draft has its minimum — the product draws itself", () => {
        expect(
            sessionConstructionPath(drawing(needsThree.id, threePoints(), true), resolve),
        ).toBeNull();
    });

    it("is nothing for a grammar that does not ask for one, before the first click, or in an edit", () => {
        expect(
            sessionConstructionPath(drawing(plainThree.id, twoPoints(), true), resolve),
        ).toBeNull();
        expect(
            sessionConstructionPath(drawing(needsThree.id, twoPoints(), false), resolve),
        ).toBeNull();
        expect(sessionConstructionPath(editing(needsThree.id, twoPoints()), resolve)).toBeNull();
    });
});
