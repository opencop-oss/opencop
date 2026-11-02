// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { LngLat, MercatorCoordinate } from "maplibre-gl";
import * as turf from "@turf/turf";
import { Reactive } from "../Reactive.ts";

/**
 * Vertex count a parametric geometry rasterizes its curve to, both for the
 * `bbox` it serializes and for the ring its layer draws.
 *
 * The two must agree for a box to bound what the user sees, so this is the one
 * value both sides read: `@opencop/layers` cannot import it (the packages are
 * deliberately independent), so the app passes it to `CircleLayer` and
 * `EllipseLayer` as their `steps` prop. It matches turf's own default, which is
 * why the layers' fallback default is the same number.
 */
export const BBOX_STEPS = 64;

/** Which poles a shape's interior covers. */
export interface EnclosedPoles {
    north: boolean;
    south: boolean;
}

/**
 * Which poles a shape centred at `origin` covers, given how far it reaches
 * along the meridian through that centre.
 *
 * One distance serves both directions because the shapes measured this way are
 * centrally symmetric — a circle reaches its radius everywhere, and an
 * ellipse's polar radius is equal at any bearing and its opposite.
 *
 * @param origin The shape's centre.
 * @param meridianExtent Distance in metres from `origin` to the boundary at
 * bearing 0 (equally, 180).
 */
export function polesEnclosed(origin: Position, meridianExtent: number): EnclosedPoles {
    const [lng, lat] = origin;
    return {
        north: turf.distance([lng, lat], [lng, 90], { units: "meters" }) < meridianExtent,
        south: turf.distance([lng, lat], [lng, -90], { units: "meters" }) < meridianExtent,
    };
}

/**
 * RFC 7946 §5 `bbox` for a parametric geometry, measured off the polygon ring
 * it rasterizes to.
 *
 * Shapes carrying their size in {@link Geometry.toProperties} rather than in
 * their coordinates serialize to a `Point`, so a consumer measuring those
 * coordinates alone sees zero area. `bbox` is how GeoJSON expresses "this
 * covers more ground than its coordinates do", and `turf.bbox` returns a
 * present `bbox` in preference to walking coordinates — so consumers get the
 * real extent without special-casing these types.
 *
 * A covered pole is never a ring vertex, so per-vertex extremes crop the cap
 * around it and describe a latitude band the shape overshoots. Ring geometry
 * alone cannot say whether that happened — the caller knows its own parameters
 * and states it via {@link polesEnclosed}. The surviving edges still come from
 * the ring, which is why a shape covering one pole keeps its true extent on the
 * other side.
 *
 * Assumes a ring with **continuous** longitudes, which is what `turf.circle`
 * and `turf.ellipse` emit — they run past ±180 rather than wrapping. A ring
 * using the conventional wrapped representation (vertices at +179 and -179 for
 * a shape crossing the dateline) would measure as spanning every longitude
 * between them.
 *
 * @param ring The rasterized ring.
 * @param poles Which poles the shape's interior covers.
 */
export function ringBbox(ring: Readonly<GeoJSON.Polygon>, poles: EnclosedPoles): GeoJSON.BBox {
    const [minLng, minLat, maxLng, maxLat] = turf.bbox(ring);

    if (poles.north && poles.south) return [-180, -90, 180, 90];
    if (poles.north) return [-180, minLat, 180, 90];
    if (poles.south) return [-180, -90, 180, maxLat];

    return [minLng, minLat, maxLng, maxLat];
}

/** The coordinate dimension — 2 for [lng, lat], 3 for [lng, lat, alt]. */
export type Dimension = 2 | 3;

/** A [lng, lat] or [lng, lat, alt] tuple. */
export type Position = Position2D | Position3D;
/** A [lng, lat] tuple. */
export type Position2D = [number, number];
/** A [lng, lat, alt] tuple. */
export type Position3D = [number, number, number];

/** Error thrown when geometry constraints are violated. */
export class GeometryError extends Error {
    constructor(message: string) {
        super(message);
    }

    /** Create an out-of-bounds index error. */
    static outOfbounds(): GeometryError {
        return new GeometryError("Index out of bounds");
    }

    /** Create an invalid position dimension error. */
    static dim(): GeometryError {
        return new GeometryError("Invalid position dimension.");
    }
}

/**
 * Abstract base class for all geometry primitives.
 *
 * Stores positions as a flat `number[]` with a stride of {@link dim}.
 * Subclasses add shape-specific accessors (e.g. radius for {@link Circle},
 * push/pop for {@link Polyline}).
 */
export abstract class Geometry extends Reactive {
    /** Signal keys emitted by {@link Geometry}. */
    static readonly Signals = {
        Positions: "positions",
        Style: "style",
    };

    /** The coordinate dimension (2 or 3). */
    readonly dim: Dimension;

    /** Flat array of interleaved coordinates with stride {@link dim}. */
    protected positions: number[];

    /**
     * @param positions Flat array of interleaved coordinates.
     * @param dim Coordinate dimension. Defaults to `2`.
     * @throws {@link GeometryError} if `positions.length` is not divisible by `dim`.
     */
    constructor(positions: number[], dim?: Dimension) {
        super();
        if (!this.isValidPositions(positions, dim)) throw GeometryError.dim();
        this.positions = positions;
        this.dim = dim ?? 2;
    }

    /**
     * Validate that `positions.length` is evenly divisible by the dimension.
     * @param positions Flat coordinate array.
     * @param dim Coordinate dimension. Defaults to `2`.
     */
    protected isValidPositions(positions: number[], dim?: Dimension): boolean {
        return positions.length % (dim ?? 2) === 0;
    }

    /**
     * Retrieve a {@link Position} by index.
     * @param index The zero-based position index.
     * @throws {@link GeometryError} if `index` is out of bounds.
     */
    getPos(index: number): Position;
    /**
     * Retrieve a {@link Position} by index, writing into an existing buffer.
     * @param index The zero-based position index.
     * @param out An output buffer to fill in-place.
     * @throws {@link GeometryError} if `index` is out of bounds.
     */
    getPos(index: number, out?: Position): void;
    getPos<O extends Position>(index: number, out?: O): Position | void {
        if (!(0 <= index && index < this.positions.length / this.dim))
            throw GeometryError.outOfbounds();

        const offset = index * this.dim;
        if (out) {
            for (let i = 0; i < this.dim; i++) {
                out[i] = this.positions[offset + i];
            }
        } else {
            return this.positions.slice(offset, offset + this.dim) as Position;
        }
    }

    /**
     * Set a {@link Position} at the given index.
     * @param position The position to set.
     * @param index The zero-based position index.
     * @throws {@link GeometryError} if the position dimension does not match {@link dim}.
     */
    setPos(position: Position, index: number): this {
        if (this.dim !== position.length) throw GeometryError.dim();

        const offset = index * this.dim;
        for (let i = 0; i < this.dim; i++) {
            this.positions[offset + i] = position[i];
        }

        this.dirty(Geometry.Signals.Positions);
        return this;
    }

    /** The number of positions in the geometry. */
    get numPositions(): number {
        return this.positions.length / this.dim;
    }

    /** Retrieve all positions as a readonly flat array. */
    getAllPos(): readonly number[] {
        return this.positions;
    }

    getLngLat(index: number): LngLat {
        const pos = this.getPos(index);
        return new LngLat(pos[0], pos[1]);
    }

    getMercatorCoordinate(index: number): MercatorCoordinate {
        return MercatorCoordinate.fromLngLat(
            this.getLngLat(index),
            this.dim === 3 ? this.getPos(index)[2] : undefined,
        );
    }

    /**
     * Translate the geometry in mercator space.
     * @param deltaX The change in mercator x.
     * @param deltaY The change in mercator y.
     */
    translate(deltaX: number, deltaY: number): this {
        const mercCoords: MercatorCoordinate[] = [];

        for (let i = 0; i < this.positions.length; i += this.dim) {
            mercCoords.push(
                MercatorCoordinate.fromLngLat(
                    [this.positions[i], this.positions[i + 1]],
                    this.dim === 3 ? this.positions[i + 2] : undefined,
                ),
            );
        }

        this.positions = mercCoords
            .map((pos) => {
                const merc = new MercatorCoordinate(deltaX + pos.x, deltaY + pos.y, pos.z);

                return this.dim === 3
                    ? [...merc.toLngLat().toArray(), merc.toAltitude()]
                    : merc.toLngLat().toArray();
            })
            .flat();
        this.dirty(Geometry.Signals.Positions);

        return this;
    }

    /** Clone the geometry. */
    abstract clone(): Geometry;
    /** Serialize to a GeoJSON geometry object. */
    abstract toGeoJSONGeometry(): GeoJSON.Geometry;
    /** Return parametric properties (e.g. `{ radius }` for {@link Circle}). */
    abstract toProperties(): object;
}
