// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import * as turf from "@turf/turf";
import type { Primitive } from "@opencop/scene";
import { measurePrimitives } from "./measure.mapper";
import { circleMeasurement, lineMeasurement, polygonMeasurement } from "./measurement.model";

const ids = (primitives: Primitive[]) => primitives.map((p) => p.id);
const texts = (primitives: Primitive[]) =>
    primitives.flatMap((p) => (p.style.type === "text" ? [p.style.text] : []));

function line(positions: number[]) {
    const m = lineMeasurement([0, 0]);
    m.geometry.setAllPos(positions);
    return m;
}

describe("measurePrimitives", () => {
    it("draws nothing for a line or polygon with one vertex", () => {
        expect(measurePrimitives(lineMeasurement([0, 0]), "METRIC")).toEqual([]);
        expect(measurePrimitives(polygonMeasurement([0, 0]), "METRIC")).toEqual([]);
    });

    it("draws a line as the two-tone ruler, a length per segment, and the total", () => {
        const primitives = measurePrimitives(line([0, 0, 1, 0, 1, 1]), "METRIC");

        expect(ids(primitives).slice(0, 2)).toEqual(["base", "dashed"]);
        expect(ids(primitives)).toEqual(
            expect.arrayContaining(["segment-0", "segment-1", "total-0"]),
        );
    });

    it("draws a polygon as an outline over a fill, labelled", () => {
        const m = polygonMeasurement([0, 0]);
        m.geometry.setAllPos([0, 0, 1, 0, 1, 1]);

        expect(ids(measurePrimitives(m, "METRIC"))).toEqual(
            expect.arrayContaining(["outline", "fill", "label"]),
        );
    });

    it("draws a circle with its area at the centre and its radius on the diagonals", () => {
        const m = circleMeasurement([0, 0]);
        m.geometry.setRadius(10_000);
        const primitives = measurePrimitives(m, "METRIC");

        expect(ids(primitives)).toEqual(
            expect.arrayContaining(["fill", "outline", "area", "radius-0"]),
        );
        const bearings = primitives
            .filter((p) => p.id.startsWith("radius-"))
            .map((p) => turf.bearing([0, 0], p.geometry.positions.slice(0, 2)));
        [-45, -135, 135, 45].forEach((expected, i) => expect(bearings[i]).toBeCloseTo(expected, 0));
    });

    it("writes its labels in the operator's unit system", () => {
        const m = line([0, 0, 1, 0]);

        expect(texts(measurePrimitives(m, "METRIC"))).not.toEqual(
            texts(measurePrimitives(m, "IMPERIAL")),
        );
    });
});
