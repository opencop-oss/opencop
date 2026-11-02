// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Circle as CircleGeometry } from "../geometry/Circle";
import { ShapeStyle } from "../style/ShapeStyle";
import { StrokePattern } from "../style/StrokeStyle";
import { type TypeDef, type TypeDefProps } from "./TypeDef";
import { Element } from "../Element";
import type { Position2D } from "../geometry/Geometry";

/** Construction properties for {@link Circle.create}. */
export interface CircleProps extends TypeDefProps<ShapeStyle> {
    /** The center position. */
    origin: Position2D;
    /** The radius in meters. */
    radius: number;
}

/** Default style for circle elements. */
export const DefaultCircleStyle = new ShapeStyle({
    fillColor: "#0084ff33",
    strokeColor: "#0084ffff",
    strokeWeight: 2,
    strokePattern: StrokePattern.Solid,
});

export type CircleElement = Element<CircleGeometry, ShapeStyle>;

/**
 * Type definition for circle elements.
 *
 * A circle is a {@link CircleGeometry} with {@link ShapeStyle}, representing
 * a filled circle defined by a center point and radius.
 */
export const Circle = {
    type: "circle",

    /** Type guard narrowing to a circle element. */
    is(element: Element): element is CircleElement {
        return element.type === this.type;
    },

    /**
     * Create a circle element.
     * @param props The circle properties.
     */
    create({ origin, radius, style, meta }: CircleProps): CircleElement {
        return new Element({
            ...meta,
            type: this.type,
            geometry: new CircleGeometry(origin, radius),
            style: style ?? DefaultCircleStyle.clone(),
        });
    },
} as const satisfies TypeDef<CircleGeometry, ShapeStyle>;
