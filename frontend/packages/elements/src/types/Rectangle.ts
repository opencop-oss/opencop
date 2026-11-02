// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Polygon as PolygonGeometry } from "../geometry/Polygon";
import { ShapeStyle } from "../style/ShapeStyle";
import { StrokePattern } from "../style/StrokeStyle";
import { type TypeDef, type TypeDefProps } from "./TypeDef";
import { Element } from "../Element";
import type { Position } from "../geometry/Geometry";

/** Construction properties for {@link Rectangle.create}. */
export interface RectangleProps extends TypeDefProps<ShapeStyle> {
    /** The rectangle positions (4 vertices) as {@link Position} tuples or a flat number array. */
    positions: number[] | Position[];
}

/** Default style for rectangle elements. */
export const DefaultRectangleStyle = new ShapeStyle({
    fillColor: "#0084ff33",
    strokeColor: "#0084ffff",
    strokeWeight: 2,
    strokePattern: StrokePattern.Solid,
});

export type RectangleElement = Element<PolygonGeometry, ShapeStyle>;

/**
 * Type definition for rectangle elements.
 *
 * A rectangle is a four-vertex {@link PolygonGeometry} with {@link ShapeStyle},
 * representing an axis-aligned or rotated rectangular shape.
 */
export const Rectangle = {
    type: "rectangle",

    /** Type guard narrowing to a rectangle element. */
    is(element: Element): element is RectangleElement {
        return element.type === this.type;
    },

    /**
     * Create a rectangle element.
     * @param props The rectangle properties.
     */
    create({ positions, style, meta }: RectangleProps): RectangleElement {
        const flat = Array.isArray(positions[0])
            ? (positions as Position[]).flat()
            : (positions as number[]);

        return new Element({
            ...meta,
            type: this.type,
            geometry: new PolygonGeometry(flat, 2),
            style: style ?? DefaultRectangleStyle.clone(),
        });
    },
} as const satisfies TypeDef<PolygonGeometry, ShapeStyle>;
