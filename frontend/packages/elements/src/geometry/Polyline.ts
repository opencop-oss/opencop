// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Geometry, GeometryError, type Dimension, type Position } from "./Geometry";
import * as turf from "@turf/turf";

/**
 * A variable-length line geometry (GeoJSON `LineString`).
 *
 * Used as the spatial primitive for element types composed of ordered
 * vertices (e.g. `"line"`, `"arrow"`, `"angle"`).
 */
export class Polyline extends Geometry {
    /**
     * Reconstruct a polyline geometry from its serialized positions.
     * Derived values emitted by {@link toProperties} are recomputed from the restored coordinates.
     */
    static fromProperties(positions: number[], dim: Dimension = 2): Polyline {
        return new Polyline([...positions], dim);
    }

    /**
     * @param positions Flat array of interleaved coordinates.
     * @param dim Coordinate dimension. Defaults to `2`.
     */
    constructor(positions: number[], dim?: Dimension) {
        super(positions, dim);
    }

    /**
     * Append a {@link Position} to the end.
     * @param position The position to append.
     * @throws {@link GeometryError} if the position dimension does not match.
     */
    push(position: Position): this {
        if (position.length !== this.dim) throw GeometryError.dim();
        this.positions.push(...position);
        this.dirty(Geometry.Signals.Positions);
        return this;
    }

    /**
     * Remove and return the last {@link Position}.
     * @returns The removed position, or `undefined` if the polyline is empty.
     */
    pop(): Position | undefined {
        if (this.positions.length < this.dim) return undefined;
        const pos = this.positions.splice(this.positions.length - this.dim, this.dim) as Position;
        this.dirty(Geometry.Signals.Positions);
        return pos;
    }

    /**
     * Replace all positions at once.
     * @param positions An array of {@link Position} tuples.
     * @throws {@link GeometryError} if any position dimension does not match.
     */
    setAllPos(positions: Position[]): this;
    /**
     * Replace all positions at once.
     * @param positions A flat number array of interleaved coordinates.
     * @throws {@link GeometryError} if the array length is not divisible by {@link dim}.
     */
    setAllPos(positions: number[]): this;
    setAllPos(positions: Position[] | number[]): this {
        if (Array.isArray(positions[0])) {
            (positions as Position[]).forEach((pos) => {
                if (pos.length !== this.dim) throw GeometryError.dim();
            });
            this.positions = (positions as Position[]).flat();
        } else {
            if ((positions as number[]).length % this.dim !== 0) throw GeometryError.dim();
            this.positions = positions as number[];
        }

        this.dirty(Geometry.Signals.Positions);
        return this;
    }

    /** Clone the polyline geometry. */
    clone(): Polyline {
        return new Polyline([...this.positions], this.dim);
    }

    /** Serialize to a GeoJSON `LineString`. */
    toGeoJSONGeometry(): GeoJSON.Geometry {
        const coordinates: number[][] = [];
        for (let i = 0; i < this.positions.length; i += this.dim) {
            coordinates.push(this.positions.slice(i, i + this.dim));
        }

        return {
            type: "LineString",
            coordinates,
        };
    }

    /** No parametric properties for a polyline. */
    toProperties(): object {
        return {
            range:
                this.positions.length === 2 * this.dim
                    ? turf.distance(this.getPos(0), this.getPos(1))
                    : 0,
            bearing:
                this.positions.length === 2 * this.dim
                    ? turf.bearing(this.getPos(0), this.getPos(1))
                    : 0,
        };
    }
}
