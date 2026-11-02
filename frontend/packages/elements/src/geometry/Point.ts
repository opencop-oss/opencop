// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Geometry, type Dimension, type Position } from "./Geometry";

/**
 * A single-position geometry.
 *
 * Used as the spatial primitive for element types that occupy a single
 * location (e.g. `"point"`, `"symbol"`).
 */
export class Point extends Geometry {
    /**
     * Reconstruct a point geometry from its serialized position.
     * Point geometries do not persist any additional parametric properties.
     */
    static fromProperties(position: Position): Point {
        return new Point([...position] as Position, position.length === 3 ? 3 : 2);
    }

    /**
     * @param position The [lng, lat] or [lng, lat, alt] position.
     * @param dim Coordinate dimension. Defaults to `2`.
     */
    constructor(position: Position, dim?: Dimension) {
        super(position, dim);
    }

    /** Retrieve the position as a {@link Position} tuple. */
    getPosition(): Position {
        return [...this.positions] as Position;
    }

    /**
     * Set the position.
     * @param position The new position.
     */
    setPosition(position: Position): this {
        return super.setPos(position, 0);
    }

    /** Clone the point geometry. */
    clone(): Point {
        return new Point(this.getPosition(), this.dim);
    }

    /** Serialize to a GeoJSON `Point`. */
    toGeoJSONGeometry(): GeoJSON.Geometry {
        return {
            type: "Point",
            coordinates: this.getPosition(),
        };
    }

    /** No parametric properties for a point. */
    toProperties(): object {
        return {};
    }
}
