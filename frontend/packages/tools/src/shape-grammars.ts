// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    Angle,
    Arrow,
    Circle,
    Line,
    Point,
    Polygon,
    PolygonGeometry,
    Rectangle,
    type CircleGeometry,
    type PolylineGeometry,
    type Position2D,
} from "@opencop/elements";
import * as turf from "@turf/turf";
import { MercatorVector } from "./MercatorVector";

import {
    createParametricGrammar,
    createPointGrammar,
    createSequentialGrammar,
    createTraceGrammar,
    type ParametricPhase,
} from "./grammars";

/**
 * The grammar each built-in shape draws with — building blocks a subject or a
 * plugin uses as-is, or rebuilds from the same factory with its own config.
 */

export const polygonGrammar = createSequentialGrammar({
    min: 3,
    create: (cursor) => Polygon.create({ positions: cursor }),
});

/** A freehand polygon: the pointer's path while the button is held is the ring. */
export const traceGrammar = createTraceGrammar({
    create: (cursor) => Polygon.create({ positions: cursor }),
});

/**
 * Three phases: the first corner, the second (one full edge), then the width
 * from the cursor's distance off that edge. The edge is read back from the
 * draft on every move — positions 0 and 1 always hold it — so the phases keep
 * no state between moves and two drafts cannot share any.
 *
 * Exported because they touch only `geometry`: any subject whose product is a
 * four-vertex polygon draws with them and supplies its own `create`.
 */
export const rectanglePhases: readonly ParametricPhase[] = [
    {
        preview(object, cursor) {
            object.geometry.setPos(cursor, 0);
        },
    },
    {
        preview(object, cursor) {
            object.geometry.setPos(cursor, 1);
        },
    },
    {
        preview(object, cursor) {
            const a = object.geometry.getPos(0) as Position2D;
            const b = object.geometry.getPos(1) as Position2D;
            const v0 = MercatorVector.fromLngLat(a);
            const v1 = MercatorVector.fromLngLat(b);

            const c = MercatorVector.fromLngLat(cursor);
            const u = v1.clone().subtract(v0);
            const v = c.clone().subtract(v1);
            const proj = u.clone().scale(u.dot(v) / u.dot(u)); // Projection of v onto u.
            const perp = v.clone().subtract(proj); // Component of v perpindicular to u.

            const w = v1.clone().add(perp).toLngLat().slice(0, 2) as Position2D;
            const x = v0.clone().add(perp).toLngLat().slice(0, 2) as Position2D;

            // Counter-clockwise, as GeoJSON wants an outer ring, with the edge
            // a–b kept at positions 0 and 1: [a, b, w, x] or, reversed, [b, a, x, w].
            // Either order describes the same rectangle, so the next move
            // may read the edge back in whichever order it was written.
            const ring: Position2D[] = [a, b, w, x];
            // Turf reads a GeoJSON ring, which repeats its first position at the end.
            const ccw = turf.booleanClockwise([...ring, a]) ? [b, a, x, w] : ring;
            (object.geometry as PolygonGeometry).setAllPos(ccw.flat());
        },
    },
];

export const rectangleGrammar = createParametricGrammar({
    create: (cursor) => Rectangle.create({ positions: cursor }),
    phases: rectanglePhases,
});

export const lineGrammar = createSequentialGrammar({
    min: 2,
    create: (cursor) => Line.create({ positions: cursor }),
});

export const arrowGrammar = createSequentialGrammar({
    min: 2,
    max: 2,
    create: (cursor) => Arrow.create({ start: cursor, end: cursor }),
    preview: (object, cursor, locked) => {
        const geo = object.geometry as PolylineGeometry;
        if (locked === 0) {
            geo.setAllPos([...cursor, ...cursor]);
        } else {
            geo.setPos(cursor, locked);
        }
    },
});

export const angleGrammar = createSequentialGrammar({
    min: 3,
    max: 3,
    create: (cursor) => Angle.create({ first: cursor, second: cursor, third: cursor }),
    preview: (object, cursor, locked) => {
        const geo = object.geometry as PolylineGeometry;
        if (locked === 0) {
            geo.setAllPos([...cursor, ...cursor, ...cursor]);
        } else {
            geo.setPos(cursor, locked);
        }
    },
});

export const pointGrammar = createPointGrammar({
    create: (cursor) => Point.create({ position: cursor }),
});

/**
 * The circle draw's phases — place the origin, then drag out the radius — for
 * any draft whose geometry is a circle. A subject drafting its own object
 * configures `createParametricGrammar` with these and its own `create`.
 */
export const circlePhases: readonly ParametricPhase[] = [
    {
        preview: (object, cursor) => {
            (object.geometry as CircleGeometry).setOrigin(cursor);
        },
    },
    {
        preview: (object, cursor) => {
            const geo = object.geometry as CircleGeometry;
            const dist = turf.distance(geo.getOrigin(), cursor, {
                units: "meters",
            });
            geo.setRadius(dist);
        },
    },
];

export const circleGrammar = createParametricGrammar({
    create: (cursor) => Circle.create({ origin: cursor, radius: 10 }),
    phases: circlePhases,
});
