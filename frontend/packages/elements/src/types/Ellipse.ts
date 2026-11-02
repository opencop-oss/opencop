// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Ellipse as EllipseGeometry } from "../geometry/Ellipse";
import { ShapeStyle } from "../style/ShapeStyle";
import { StrokePattern } from "../style/StrokeStyle";
import { type TypeDef, type TypeDefProps } from "./TypeDef";
import { Element } from "../Element";
import type { Position2D } from "../geometry/Geometry";

/** Construction properties for {@link Ellipse.create}. */
export interface EllipseProps extends TypeDefProps<ShapeStyle> {
    /** The center position. */
    origin: Position2D;
    /** The semi-major axis length in meters. */
    major: number;
    /** The semi-minor axis length in meters. */
    minor: number;
    /** Rotation angle in degrees from north. */
    bearing?: number;
}

/** Default style for ellipse elements. */
export const DefaultEllipseStyle = new ShapeStyle({
    fillColor: "#0084ff33",
    strokeColor: "#0084ffff",
    strokeWeight: 2,
    strokePattern: StrokePattern.Solid,
});

export type EllipseElement = Element<EllipseGeometry, ShapeStyle>;

/**
 * Type definition for ellipse elements.
 *
 * An ellipse is an {@link EllipseGeometry} with {@link ShapeStyle}, representing
 * a filled ellipse defined by center, semi-axes, and bearing.
 */
export const Ellipse = {
    type: "ellipse",

    /** Type guard narrowing to an ellipse element. */
    is(element: Element): element is EllipseElement {
        return element.type === this.type;
    },

    /**
     * Create an ellipse element.
     * @param props The ellipse properties.
     */
    create({ origin, major, minor, bearing, style, meta }: EllipseProps): EllipseElement {
        return new Element({
            ...meta,
            type: this.type,
            geometry: new EllipseGeometry(origin, major, minor, bearing),
            style: style ?? DefaultEllipseStyle.clone(),
        });
    },
} as const satisfies TypeDef<EllipseGeometry, ShapeStyle>;
