// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { v4 as uuid } from "uuid";
import {
    CircleGeometry,
    PolygonGeometry,
    PolylineGeometry,
    type DraftObject,
    type Position2D,
} from "@opencop/elements";

/** The product type a measurement is drawn as, in the host's session store. */
export const MEASURE_PRODUCT_TYPE = "measure:ruler";

/** The geometry of each measure shape: a ruler line, an area polygon or a radius circle. */
export type MeasureGeometry = PolylineGeometry | PolygonGeometry | CircleGeometry;

/**
 * A measurement as the measure tool drafts it: a geometry and the measure product
 * type, and nothing an element carries — a measurement has no source, no symbol
 * and no style, and is never stored. Its shape is its geometry's class.
 */
export interface Measurement<G extends MeasureGeometry = MeasureGeometry> extends DraftObject {
    readonly type: typeof MEASURE_PRODUCT_TYPE;
    readonly geometry: G;
}

function measurement<G extends MeasureGeometry>(geometry: G): Measurement<G> {
    return { key: uuid(), type: MEASURE_PRODUCT_TYPE, geometry };
}

/** A line measurement started at `cursor`. */
export function lineMeasurement(cursor: Position2D): Measurement<PolylineGeometry> {
    return measurement(new PolylineGeometry([...cursor], 2));
}

/** A polygon measurement started at `cursor`. */
export function polygonMeasurement(cursor: Position2D): Measurement<PolygonGeometry> {
    return measurement(new PolygonGeometry([...cursor], 2));
}

/** A circle measurement centred at `cursor`, with the circle draw's starting radius. */
export function circleMeasurement(cursor: Position2D): Measurement<CircleGeometry> {
    return measurement(new CircleGeometry(cursor, 10));
}

/** Whether a draft is a measurement: the measure product type over a measure shape's geometry. */
export function isMeasurement(draft: DraftObject): draft is Measurement {
    const { geometry } = draft;
    return (
        draft.type === MEASURE_PRODUCT_TYPE &&
        (geometry instanceof PolylineGeometry ||
            geometry instanceof PolygonGeometry ||
            geometry instanceof CircleGeometry)
    );
}

/** The draft as a measurement — the same object — or null when it is something else. */
export function asMeasurement(draft: DraftObject | null): Measurement | null {
    return draft && isMeasurement(draft) ? draft : null;
}

export function isLineMeasurement(m: Measurement): m is Measurement<PolylineGeometry> {
    return m.geometry instanceof PolylineGeometry;
}

export function isPolygonMeasurement(m: Measurement): m is Measurement<PolygonGeometry> {
    return m.geometry instanceof PolygonGeometry;
}

export function isCircleMeasurement(m: Measurement): m is Measurement<CircleGeometry> {
    return m.geometry instanceof CircleGeometry;
}
