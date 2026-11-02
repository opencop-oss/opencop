// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { StrokeStyle, StrokePattern } from "../style/StrokeStyle";
import { type TypeDef, type TypeDefProps } from "./TypeDef";
import { Element } from "../Element";
import type { Position2D } from "../geometry/Geometry";
import * as turf from "@turf/turf";
import { directionalAngle, type AngleOrientation } from "@opencop/utils";
import { Polyline } from "../geometry/Polyline";

/** Construction properties for {@link Angle.create}. */
export interface AngleProps extends TypeDefProps<StrokeStyle> {
    /** The first leg endpoint. */
    first: Position2D;
    /** The vertex (shared point between the two legs). */
    second: Position2D;
    /** The second leg endpoint. */
    third: Position2D;
}

/** Default style for angle elements. */
export const DefaultAngleStyle = new StrokeStyle({
    color: "#0084ffff",
    weight: 4,
    pattern: new StrokePattern([6, 2]),
});

export type AngleElement = Element<Polyline, StrokeStyle>;

/**
 * Type definition for angle elements.
 *
 * An angle is a three-point {@link Polyline} with {@link StrokeStyle},
 * representing the angle formed at the second (vertex) point.
 */
export const Angle = {
    type: "angle",

    /** Type guard narrowing to an angle element. */
    is(element: Element): element is AngleElement {
        return element.type === this.type;
    },

    /**
     * Create an angle from three positions.
     * @param props The angle properties.
     */
    create({ first, second, third, style, meta }: AngleProps): AngleElement {
        return new Element({
            ...meta,
            type: this.type,
            geometry: new Polyline([...first, ...second, ...third], 2),
            style: style ?? DefaultAngleStyle.clone(),
        });
    },

    /**
     * Compute the geodesic angle at the vertex in degrees.
     * @param el The angle element.
     */
    getAngle(el: AngleElement): number {
        const second = el.geometry.getPos(1) as Position2D;
        const first = el.geometry.getPos(0) as Position2D;
        const third = el.geometry.getPos(2) as Position2D;
        return interiorAngleFromPositions(first, second, third);
    },

    /**
     * Compute the rhumb angle at the vertex in degrees.
     * @param el The angle element.
     */
    getRhumbAngle(el: AngleElement): number {
        const second = el.geometry.getPos(1) as Position2D;
        const first = el.geometry.getPos(0) as Position2D;
        const third = el.geometry.getPos(2) as Position2D;
        const rb0 = turf.rhumbBearing(second, first);
        const rb1 = turf.rhumbBearing(second, third);
        return clampAngle(rb0, rb1);
    },

    /**
     * Compute the directional geodesic angle at the vertex, as a full [0, 360) sweep from the
     * first leg to the second — unlike {@link getAngle}'s orientation-agnostic [0, 180] interior
     * angle, this distinguishes clockwise from counterclockwise and matches the arc a renderer
     * (e.g. `AngleLayer` in `@opencop/layers`) sweeps on the map.
     * @param el The angle element.
     * @param orientation Sweep direction from the first leg's bearing to the second's.
     */
    getDirectionalAngle(el: AngleElement, orientation: AngleOrientation): number {
        return directionalAngle(
            el.geometry.getPos(1) as Position2D,
            el.geometry.getPos(0) as Position2D,
            el.geometry.getPos(2) as Position2D,
            orientation,
        );
    },
} as const satisfies TypeDef<Polyline, StrokeStyle>;

/**
 * Normalize the difference between two bearings to a positive angle in [0, 180].
 * @param angle0 The first bearing in degrees.
 * @param angle1 The second bearing in degrees.
 */
function clampAngle(angle0: number, angle1: number): number {
    return Math.abs(((((angle1 - angle0 + 180) % 360) + 360) % 360) - 180);
}

/** Orientation-agnostic interior angle at `second`, in [0, 180]. */
function interiorAngleFromPositions(
    first: Position2D,
    second: Position2D,
    third: Position2D,
): number {
    const b0 = turf.bearing(second, first);
    const b1 = turf.bearing(second, third);
    return clampAngle(b0, b1);
}
