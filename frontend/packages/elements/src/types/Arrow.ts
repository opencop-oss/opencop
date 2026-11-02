// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Polyline } from "../geometry/Polyline";
import { ArrowStyle } from "../style/ArrowStyle";
import { StrokePattern } from "../style/StrokeStyle";
import { type TypeDef, type TypeDefProps } from "./TypeDef";
import { Element } from "../Element";
import { mercatorMidpoint } from "@opencop/utils";
import type { Position, Position2D } from "../geometry/Geometry";
import * as turf from "@turf/turf";
import Logger from "js-logger";

/** Construction properties for {@link Arrow.create}. */
export interface ArrowProps extends TypeDefProps<ArrowStyle> {
    /** The start position. */
    start: Position2D;
    /** The end position. */
    end: Position2D;
}

/** Construction properties for {@link Arrow.fromRB}. */
export interface ArrowRBProps extends TypeDefProps<ArrowStyle> {
    /** The start (origin) position. */
    start: Position2D;
    /** The range in kilometers. */
    range: number;
    /** The bearing in degrees from north. */
    bearing: number;
}

/** Default style for arrow elements. */
export const DefaultArrowStyle = new ArrowStyle({
    color: "#ff0000ff",
    weight: 3,
    pattern: StrokePattern.Solid,
});

export type ArrowElement = Element<Polyline, ArrowStyle>;

/**
 * Type definition for arrow elements.
 *
 * An arrow is a two-point {@link Polyline} with {@link ArrowStyle},
 * representing a directed line from start to end.
 */
export const Arrow = {
    type: "arrow",

    Signals: {
        Angle: "angle",
    },

    /** Type guard narrowing to an arrow element. */
    is(element: Element): element is ArrowElement {
        return element.type === this.type;
    },

    /**
     * Create an arrow from start and end positions.
     * @param props The arrow properties.
     */
    create({ start, end, style, meta }: ArrowProps): ArrowElement {
        return new Element({
            ...meta,
            type: this.type,
            geometry: new Polyline([...start, ...end], 2),
            style: style ?? DefaultArrowStyle.clone(),
        });
    },

    /**
     * Create an arrow from a start position, range, and bearing.
     * @param props The arrow range/bearing properties.
     */
    fromRB({ start, range, bearing, style, meta }: ArrowRBProps): ArrowElement {
        const end = turf.destination(start, range, bearing).geometry.coordinates as Position2D;
        return Arrow.create({ start, end, style, meta });
    },

    /**
     * Compute the geodesic bearing from start to end in degrees.
     * @param el The arrow element.
     */
    getBearing(el: ArrowElement): number {
        return turf.bearing(el.geometry.getPos(0), el.geometry.getPos(1));
    },

    /**
     * Compute the rhumb bearing from start to end in degrees.
     * @param el The arrow element.
     */
    getRhumbBearing(el: ArrowElement): number {
        return turf.rhumbBearing(el.geometry.getPos(0), el.geometry.getPos(1));
    },

    /**
     * Compute the label angle for an arrow, normalized to stay readable.
     * This ensures westbound arrows don't have upside-down labels.
     * @param el The arrow element.
     * @returns Angle in degrees, constrained to [-90, 90].
     */
    getLabelAngle(el: ArrowElement): number {
        const rawAngle = 90 - turf.rhumbBearing(el.geometry.getPos(0), el.geometry.getPos(1));
        // Normalize to [-180, 180]
        const normalized = ((((rawAngle + 180) % 360) + 360) % 360) - 180;
        // Keep in readable range [-90, 90]
        if (normalized > 90) return normalized - 180;
        if (normalized <= -90) return normalized + 180;
        return normalized;
    },

    /**
     * Compute the geodesic distance from start to end in kilometers.
     * @param el The arrow element.
     */
    getRange(el: ArrowElement): number {
        return turf.distance(el.geometry.getPos(0), el.geometry.getPos(1));
    },

    /**
     * Compute the mercator midpoint between start and end.
     * @param el The arrow element.
     */
    midpoint(el: ArrowElement): Position {
        try {
            return mercatorMidpoint(
                el.geometry.getPos(0) as Position2D,
                el.geometry.getPos(1) as Position2D,
            );
        } catch {
            Logger.get("pipeline").error("Error calculating midpoint on " + el.objectId, el);
            throw new Error("Failed to calculate midpoint");
        }
    },

    /** Angle offset from reversed bearing for each barb, in degrees. */
    BARB_ANGLE: 30,

    /**
     * Produce the 2-vertex shaft path for the arrow as a flat coordinate array.
     * Barbs are not included here — they are computed at render time using
     * viewport projection (see `computePixelBarbPath` in layers/arrow.ts).
     */
    toPath(el: ArrowElement): number[] {
        const start = el.geometry.getPos(0);
        const end = el.geometry.getPos(1);
        return [...start, ...end];
    },
} as const satisfies TypeDef<Polyline, ArrowStyle>;
