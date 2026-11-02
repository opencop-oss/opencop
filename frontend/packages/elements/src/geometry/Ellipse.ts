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
 * An ellipse defined by an origin, major/minor semi-axes, and bearing.
 *
 * Serialized as a GeoJSON `Point` with the axis lengths and bearing
 * stored as parametric properties via {@link toProperties}, plus a `bbox`
 * covering the ground the ellipse occupies.
 */
export class Ellipse extends Geometry {
    /** Signal keys emitted by {@link Ellipse}. */
    static readonly Signals = {
        ...Geometry.Signals,
        Params: "params",
    };

    /** Inverse of {@link toProperties}. Reconstructs an {@link Ellipse} from serialized coordinates and properties. */
    static fromProperties(origin: Position, props: Record<string, unknown>): Ellipse {
        return new Ellipse(
            [...origin] as Position,
            typeof props["major"] === "number" ? props["major"] : 0,
            typeof props["minor"] === "number" ? props["minor"] : 0,
            typeof props["bearing"] === "number" ? props["bearing"] : 0,
            origin.length === 3 ? 3 : 2,
        );
    }

    private _major: number;
    private _minor: number;
    private _bearing: number;

    /**
     * @param origin The center position [lng, lat] or [lng, lat, alt].
     * @param major The semi-major axis length in meters.
     * @param minor The semi-minor axis length in meters.
     * @param bearing Rotation angle in degrees from north. Defaults to `0`.
     * @param dim Coordinate dimension. Defaults to `2`.
     */
    constructor(origin: Position, major: number, minor: number, bearing?: number, dim?: Dimension) {
        super(origin, dim);
        this._major = major;
        this._minor = minor;
        this._bearing = bearing ?? 0;
    }

    /** Whether the ellipse is a circle (major === minor). */
    isCircle(): boolean {
        return this._major === this._minor;
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

    /** The semi-major axis length in meters. */
    getMajor(): number {
        return this._major;
    }

    /**
     * Set the semi-major axis length.
     * @param major The length in meters.
     */
    setMajor(major: number): this {
        this._major = major;
        this.dirty(Ellipse.Signals.Params);
        return this;
    }

    /** The semi-minor axis length in meters. */
    getMinor(): number {
        return this._minor;
    }

    /**
     * Set the semi-minor axis length.
     * @param minor The length in meters.
     */
    setMinor(minor: number): this {
        this._minor = minor;
        this.dirty(Ellipse.Signals.Params);
        return this;
    }

    /** The bearing (rotation) in degrees from north. */
    getBearing(): number {
        return this._bearing;
    }

    /**
     * Set the bearing (rotation).
     * @param bearing Degrees from north.
     */
    setBearing(bearing: number): this {
        this._bearing = bearing;
        this.dirty(Ellipse.Signals.Params);
        return this;
    }

    /** Clone the ellipse geometry. */
    clone(): Ellipse {
        return new Ellipse(this.getOrigin(), this._major, this._minor, this._bearing, this.dim);
    }

    /**
     * Serialize to a GeoJSON `Point` (axes/bearing are stored in
     * {@link toProperties}), carrying the axes as a `bbox` so consumers
     * measuring extent see the ground the ellipse covers rather than its centre
     * alone.
     *
     * {@link getBearing} is degrees clockwise from north along the major axis,
     * while turf lays the semi-major axis at bearing `angle - 90` — hence
     * `bearing + 90`.
     */
    toGeoJSONGeometry(): GeoJSON.Geometry {
        const origin = this.getOrigin();

        // turf.ellipse rejects a zero axis outright ("xSemiAxis is required"),
        // and an ellipse missing its axes is constructible: fromProperties
        // defaults them to 0 for wire data that omits them. Serialization must
        // not throw on that, so degrade to the origin the way a zero radius
        // does for a circle.
        if (this._major === 0 || this._minor === 0) {
            return {
                type: "Point",
                coordinates: origin,
                bbox: [origin[0], origin[1], origin[0], origin[1]],
            };
        }

        return {
            type: "Point",
            coordinates: origin,
            bbox: ringBbox(
                turf.ellipse([origin[0], origin[1]], this._major, this._minor, {
                    steps: BBOX_STEPS,
                    units: "meters",
                    angle: this._bearing + 90,
                }).geometry,
                polesEnclosed(origin, this.meridianExtent()),
            ),
        };
    }

    /**
     * Distance in metres from the origin to the boundary along the meridian
     * through it.
     *
     * An ellipse's polar radius at polar angle `t` from the major axis is
     * `ab / √((a·sin t)² + (b·cos t)²)`. With the major axis at `bearing`, the
     * meridian is at `t = -bearing`, and the radius is even in `t` — the major
     * axis pointing north (bearing 0) puts the major axis on the meridian, and
     * pointing east (bearing 90) puts the minor axis there.
     */
    private meridianExtent(): number {
        const t = turf.degreesToRadians(this._bearing);
        return (
            (this._major * this._minor) /
            Math.hypot(this._major * Math.sin(t), this._minor * Math.cos(t))
        );
    }

    /** Return `{ major, minor, bearing }` for the wire format. */
    toProperties(): object {
        return {
            major: this._major,
            minor: this._minor,
            bearing: this._bearing,
        };
    }
}
