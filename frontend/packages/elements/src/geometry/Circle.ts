// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import * as turf from "@turf/turf";
import {
    BBOX_STEPS,
    Geometry,
    polesEnclosed,
    ringBbox,
    type Dimension,
    type Position,
} from "./Geometry";

/**
 * A circle defined by an origin point and a radius.
 *
 * Serialized as a GeoJSON `Point` with the radius stored as a
 * parametric property via {@link toProperties}, plus a `bbox` covering the
 * ground the circle occupies.
 */
export class Circle extends Geometry {
    private _radius: number;

    /** Inverse of {@link toProperties}. Reconstructs a {@link Circle} from serialized coordinates and properties. */
    static fromProperties(origin: Position, props: Record<string, unknown>): Circle {
        return new Circle(
            [...origin] as Position,
            typeof props["radius"] === "number" ? props["radius"] : 0,
            origin.length === 3 ? 3 : 2,
        );
    }

    /**
     * @param origin The center position [lng, lat] or [lng, lat, alt].
     * @param radius The radius in meters.
     * @param dim Coordinate dimension. Defaults to `2`.
     */
    constructor(origin: Position, radius: number, dim?: Dimension) {
        super(origin, dim);
        this._radius = radius;
    }

    /** Retrieve the origin (center) position. */
    getOrigin(): Position {
        return [...this.positions] as Position;
    }

    /**
     * Set the origin (center) position.
     * @param position The new origin.
     */
    setOrigin(position: Position): this {
        return super.setPos(position, 0);
    }

    /** The radius in meters. */
    getRadius(): number {
        return this._radius;
    }

    /**
     * Set the radius.
     * @param radius The radius in meters.
     */
    setRadius(radius: number): this {
        this._radius = radius;
        this.dirty(Circle.Signals.Positions);
        return this;
    }

    /** Clone the circle geometry. */
    clone(): Circle {
        return new Circle(this.getOrigin(), this._radius, this.dim);
    }

    /**
     * Serialize to a GeoJSON `Point` (radius is stored in {@link toProperties}),
     * carrying the radius as a `bbox` so consumers measuring extent see the
     * ground the circle covers rather than its centre alone.
     */
    toGeoJSONGeometry(): GeoJSON.Geometry {
        const origin = this.getOrigin();
        return {
            type: "Point",
            coordinates: origin,
            bbox: ringBbox(
                turf.circle([origin[0], origin[1]], this._radius, {
                    steps: BBOX_STEPS,
                    units: "meters",
                }).geometry,
                polesEnclosed(origin, this._radius),
            ),
        };
    }

    /** Return `{ radius }` for the wire format. */
    toProperties(): object {
        return { radius: this._radius };
    }
}
