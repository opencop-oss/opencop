// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Point as PointGeometry } from "../geometry/Point";
import { PointStyle } from "../style/PointStyle";
import { type TypeDef, type TypeDefProps } from "./TypeDef";
import { Element } from "../Element";
import type { Position2D } from "../geometry/Geometry";

/** Construction properties for {@link Point.create}. */
export interface PointProps extends TypeDefProps<PointStyle> {
    /** The point position. */
    position: Position2D;
}

/** Default style for point elements. */
export const DefaultPointStyle = new PointStyle({
    color: "#ff0000b3",
    radius: 6,
});

export type PointElement = Element<PointGeometry, PointStyle>;

/**
 * Type definition for simple point elements.
 *
 * A point is a {@link PointGeometry} with {@link PointStyle}, rendered as a
 * colored circle at a single location.
 */
export const Point = {
    type: "point",

    /** Type guard narrowing to a point element. */
    is(element: Element): element is PointElement {
        return element.type === this.type;
    },

    /**
     * Create a point element.
     * @param props The point properties.
     */
    create({ position, style, meta }: PointProps): PointElement {
        return new Element({
            ...meta,
            type: this.type,
            geometry: new PointGeometry(position),
            style: style ?? DefaultPointStyle.clone(),
        });
    },
} as const satisfies TypeDef<PointGeometry, PointStyle>;
