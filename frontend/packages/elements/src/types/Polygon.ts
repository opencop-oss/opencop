// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Polygon as PolygonGeometry } from "../geometry/Polygon";
import { ShapeStyle } from "../style/ShapeStyle";
import { StrokePattern } from "../style/StrokeStyle";
import { type TypeDef, type TypeDefProps } from "./TypeDef";
import { Element } from "../Element";
import type { Position } from "../geometry/Geometry";

/** Construction properties for {@link Polygon.create}. */
export interface PolygonProps extends TypeDefProps<ShapeStyle> {
    /** The polygon positions as {@link Position} tuples or a flat number array. */
    positions: number[] | Position[];
}

/** Default style for polygon elements. */
export const DefaultPolygonStyle = new ShapeStyle({
    fillColor: "#0084ff33",
    strokeColor: "#0084ffff",
    strokeWeight: 2,
    strokePattern: StrokePattern.Solid,
});

export type PolygonElement = Element<PolygonGeometry, ShapeStyle>;

/**
 * Type definition for polygon elements.
 *
 * A polygon is a {@link PolygonGeometry} with {@link ShapeStyle}, representing
 * a closed filled shape defined by an arbitrary number of vertices.
 */
export const Polygon = {
    type: "polygon",

    /** Type guard narrowing to a polygon element. */
    is(element: Element): element is PolygonElement {
        return element.type === this.type;
    },

    /**
     * Create a polygon element.
     * @param props The polygon properties.
     */
    create({ positions, style, meta }: PolygonProps): PolygonElement {
        const flat = Array.isArray(positions[0])
            ? (positions as Position[]).flat()
            : (positions as number[]);

        return new Element({
            ...meta,
            type: this.type,
            geometry: new PolygonGeometry(flat, 2),
            style: style ?? DefaultPolygonStyle.clone(),
        });
    },
} as const satisfies TypeDef<PolygonGeometry, ShapeStyle>;
