// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import * as turf from "@turf/turf";
import {
    BBOX_STEPS,
    type CircleGeometry,
    type DraftObject,
    type PolygonGeometry,
    type PolylineGeometry,
} from "@opencop/elements";
import { formatLength, type MeasurementSystem } from "@opencop/layers";
import {
    interleave,
    label,
    measurement as measurementLabel,
    path,
    polygon,
    type Color,
    type Primitive,
} from "@opencop/scene";
import { buildSegmentLabels, buildTotalLabel, circleLabel, polygonLabel } from "./measure.util";
import {
    asMeasurement,
    isCircleMeasurement,
    isLineMeasurement,
    isPolygonMeasurement,
    type Measurement,
} from "./measurement.model";

const DARK_GREY: Color = [60, 60, 60, 230];
const LIGHT_GREY: Color = [190, 190, 190, 230];
const MEASURE_FILL: Color = [100, 100, 100, 120];
const MEASURE_DASH: [number, number] = [10, 10];
/** The ruler line's width, px. */
const MEASURE_LINE_WIDTH_PX = 3;
/** The polygon and circle outline width, px. */
const MEASURE_STROKE_WIDTH_PX = 2;

const degPerStep = 360 / BBOX_STEPS;
const stepsPer90 = 90 / degPerStep;
const stepsPer45 = 45 / degPerStep;

/**
 * How a measurement looks, by its shape, labelled in the operator's unit system:
 *
 * - Line: a dark solid path under a light dashed one (the two-tone ruler), each
 *   segment's length along it, and the total at the end.
 * - Polygon: a dashed polygon with area and perimeter at its centroid.
 * - Circle: a dashed circle with its area at the centre and its radius on the diagonals.
 */
export function measurePrimitives(m: Measurement, unit: MeasurementSystem): Primitive[] {
    if (isCircleMeasurement(m)) return circlePrimitives(m.geometry, unit);
    if (isPolygonMeasurement(m)) return polygonPrimitives(m as Measurement<PolygonGeometry>, unit);
    if (isLineMeasurement(m)) return linePrimitives(m as Measurement<PolylineGeometry>, unit);
    return [];
}

/** A measure session's product, in the operator's unit system. Nothing for a draft that is not a measurement. */
export function measureProductPrimitives(
    draft: DraftObject,
    { measurement }: { readonly measurement: MeasurementSystem },
): Primitive[] {
    const m = asMeasurement(draft);
    return m ? measurePrimitives(m, measurement) : [];
}

/** Whether a line or polygon has at least two vertices — a one-vertex draft is not drawn. */
function hasSegment(m: Measurement): boolean {
    return (m.geometry.getAllPos() as number[]).length >= 4;
}

function linePrimitives(m: Measurement<PolylineGeometry>, unit: MeasurementSystem): Primitive[] {
    if (!hasSegment(m)) return [];
    const positions = interleave(m.geometry.getAllPos() as number[]);
    return [
        path({
            id: "base",
            geometry: { positions },
            style: { strokeColor: DARK_GREY, strokeWidth: MEASURE_LINE_WIDTH_PX },
        }),
        path({
            id: "dashed",
            geometry: { positions },
            style: {
                strokeColor: LIGHT_GREY,
                strokeWidth: MEASURE_LINE_WIDTH_PX,
                strokeDash: MEASURE_DASH,
            },
        }),
        ...buildSegmentLabels(m, unit).map((segment, i) =>
            measurementLabel({
                id: `segment-${i}`,
                geometry: { positions: [...segment.position, 0] },
                style: { text: segment.text, angle: segment.angle },
            }),
        ),
        ...buildTotalLabel(m, unit).map((total, i) =>
            measurementLabel({
                id: `total-${i}`,
                geometry: { positions: [...total.position, 0] },
                style: { text: total.text, offset: [0, 16] },
            }),
        ),
    ];
}

function polygonPrimitives(m: Measurement<PolygonGeometry>, unit: MeasurementSystem): Primitive[] {
    if (!hasSegment(m)) return [];
    const positions = interleave(m.geometry.getClosed());
    const primitives: Primitive[] = [
        path({
            id: "outline",
            geometry: { positions },
            style: {
                strokeColor: DARK_GREY,
                strokeWidth: MEASURE_STROKE_WIDTH_PX,
                strokeDash: MEASURE_DASH,
            },
        }),
        measurementLabel({
            id: "label",
            geometry: { positions: [...m.geometry.getCentroid(), 0] as [number, number, number] },
            style: { text: polygonLabel(m, unit) },
        }),
    ];
    // A fill needs a third vertex.
    if (positions.length / 3 > 3)
        primitives.push(
            polygon({ id: "fill", geometry: { positions }, style: { fillColor: MEASURE_FILL } }),
        );
    return primitives;
}

function circlePrimitives(geometry: CircleGeometry, unit: MeasurementSystem): Primitive[] {
    const origin = geometry.getOrigin() as [number, number];
    const radius = geometry.getRadius();
    // Same rasterization the geometry measures its bbox against.
    const ring = turf.circle(origin, radius, { steps: BBOX_STEPS, units: "meters" }).geometry
        .coordinates[0];
    const positions = interleave(ring.flat());
    const radiusText = formatLength(radius, unit);

    return [
        polygon({ id: "fill", geometry: { positions }, style: { fillColor: MEASURE_FILL } }),
        path({
            id: "outline",
            geometry: { positions },
            style: {
                strokeColor: DARK_GREY,
                strokeWidth: MEASURE_STROKE_WIDTH_PX,
                strokeDash: MEASURE_DASH,
            },
        }),
        label({
            id: "area",
            geometry: { positions: [...origin, 0] },
            style: { text: circleLabel(radius, unit) },
        }),
        // Radius readouts on the diagonals (45°, 135°, …).
        ...[0, 1, 2, 3].map((i) =>
            measurementLabel({
                id: `radius-${i}`,
                geometry: {
                    positions: [...ring[Math.floor(stepsPer45 + stepsPer90 * i)], 0] as [
                        number,
                        number,
                        number,
                    ],
                },
                style: { text: radiusText, angle: 45 + 90 * i },
            }),
        ),
    ];
}
