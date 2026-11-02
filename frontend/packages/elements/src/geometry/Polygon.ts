// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import * as turf from "@turf/turf";
import { Geometry, GeometryError, type Dimension, type Position } from "./Geometry";

/**
 * A closed polygon geometry.
 *
 * Stored **open** internally (first !== last). The closing vertex is
 * appended automatically during serialization per GeoJSON RFC 7946.
 */
export class Polygon extends Geometry {
    private _area?: number;
    private _centroid?: Position;

    /**
     * Reconstruct a polygon geometry from its serialized positions.
     * The constructor re-opens any closed ring emitted by {@link toGeoJSONGeometry}.
     */
    static fromProperties(positions: number[], dim: Dimension = 2): Polygon {
        return new Polygon([...positions], dim);
    }

    /**
     * @param positions Flat array of interleaved coordinates (open or closed ring).
     * @param dim Coordinate dimension. Defaults to `2`.
     */
    constructor(positions: number[], dim?: Dimension) {
        super(positions, dim);
        // Strip closing vertex if provided — store open internally.
        this.positions = this.openRing(this.positions);
    }

    /**
     * Remove the closing vertex if first === last.
     * @param positions Flat coordinate array.
     */
    private openRing(positions: number[]): number[] {
        if (positions.length < this.dim * 2) return positions;

        const first = positions.slice(0, this.dim);
        const last = positions.slice(positions.length - this.dim);

        if (first.every((v, i) => v === last[i])) {
            return positions.slice(0, positions.length - this.dim);
        }

        return positions;
    }

    /**
     * Append a closing vertex to create a closed ring.
     * @param positions Flat coordinate array (open).
     */
    closeRing(positions: number[]): number[] {
        return positions.concat(positions.slice(0, this.dim));
    }

    /**
     * Append a {@link Position} to the ring.
     * @param position The position to append.
     * @throws {@link GeometryError} if the position dimension does not match.
     */
    push(position: Position): this {
        if (position.length !== this.dim) throw GeometryError.dim();
        this.positions.push(...position);
        this._area = undefined;
        this._centroid = undefined;
        this.dirty(Geometry.Signals.Positions);
        return this;
    }

    /**
     * Remove and return the last {@link Position} from the ring.
     * @returns The removed position, or `undefined` if the polygon is empty.
     */
    pop(): Position | undefined {
        if (this.positions.length < this.dim) return undefined;
        const pos = this.positions.splice(this.positions.length - this.dim, this.dim) as Position;
        this._area = undefined;
        this._centroid = undefined;
        this.dirty(Geometry.Signals.Positions);
        return pos;
    }

    /** @override Invalidate area/centroid caches when a single position changes. */
    setPos(position: Position, index: number): this {
        this._area = undefined;
        this._centroid = undefined;
        return super.setPos(position, index);
    }

    /** @override Invalidate area/centroid caches when translating. */
    translate(deltaX: number, deltaY: number): this {
        this._area = undefined;
        this._centroid = undefined;
        return super.translate(deltaX, deltaY);
    }

    /**
     * Replace all positions at once (open ring — do not include closing vertex).
     * @param positions An array of {@link Position} tuples.
     * @throws {@link GeometryError} if any position dimension does not match.
     */
    setAllPos(positions: Position[]): this;
    /**
     * Replace all positions at once (open ring — do not include closing vertex).
     * @param positions A flat number array of interleaved coordinates.
     * @throws {@link GeometryError} if the array length is not divisible by {@link dim}.
     */
    setAllPos(positions: number[]): this;
    setAllPos(positions: Position[] | number[]): this {
        if (Array.isArray(positions[0])) {
            (positions as Position[]).forEach((pos) => {
                if (pos.length !== this.dim) throw GeometryError.dim();
            });
            this.positions = this.openRing((positions as Position[]).flat());
        } else {
            if ((positions as number[]).length % this.dim !== 0) throw GeometryError.dim();
            this.positions = this.openRing(positions as number[]);
        }

        this._area = undefined;
        this._centroid = undefined;
        this.dirty(Geometry.Signals.Positions);
        return this;
    }

    /**
     * Compute the area of the polygon in square meters. Cached until positions change.
     * @param recompute Force recomputation even if cached.
     */
    getArea(recompute?: boolean): number {
        if (this._area === undefined || recompute) {
            this._area = turf.area(this.toGeoJSONGeometry());
        }
        return this._area;
    }

    getClosed(): number[] {
        return this.closeRing([...this.getAllPos()]);
    }

    /**
     * Compute the centroid of the polygon. Cached until positions change.
     * @param recompute Force recomputation even if cached.
     */
    getCentroid(recompute?: boolean): Position {
        if (!this._centroid || recompute) {
            this._centroid = turf.centroid(this.toGeoJSONGeometry()).geometry
                .coordinates as Position;
        }
        return this._centroid;
    }

    /** Clone the polygon geometry. */
    clone(): Polygon {
        return new Polygon([...this.positions], this.dim);
    }

    /**
     * Serialize to a GeoJSON `Polygon`.
     *
     * Closes the ring and pads to 4 positions (3 unique + closing) if needed
     * to satisfy the RFC 7946 minimum.
     */
    toGeoJSONGeometry(): GeoJSON.Geometry {
        const closed = this.closeRing(this.positions);
        const exterior: number[][] = [];

        for (let i = 0; i < closed.length; i += this.dim) {
            exterior.push(closed.slice(i, i + this.dim));
        }

        // RFC 7946 requires minimum 4 positions (3 unique + closing).
        // Pad with first vertex if needed.
        while (exterior.length < 4) {
            exterior.splice(exterior.length - 1, 0, exterior[0]);
        }

        return {
            type: "Polygon",
            coordinates: [exterior],
        };
    }

    /** No parametric properties for a polygon. */
    toProperties(): object {
        return {};
    }
}
