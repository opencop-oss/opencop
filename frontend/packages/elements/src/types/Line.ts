// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Polyline } from "../geometry/Polyline";
import { StrokeStyle, StrokePattern } from "../style/StrokeStyle";
import { type TypeDef, type TypeDefProps } from "./TypeDef";
import { Element } from "../Element";
import type { Position } from "../geometry/Geometry";

/** Construction properties for {@link Line.create}. */
export interface LineProps extends TypeDefProps<StrokeStyle> {
    /** The line positions as {@link Position} tuples or a flat number array. */
    positions: number[] | Position[];
}

/** Default style for line elements. */
export const DefaultLineStyle = new StrokeStyle({
    color: "#0084ffff",
    weight: 3,
    pattern: StrokePattern.Solid,
});

export type LineElement = Element<Polyline, StrokeStyle>;

/**
 * Type definition for line elements.
 *
 * A line is an N-point {@link Polyline} with {@link StrokeStyle},
 * representing a polyline path on the map.
 */
export const Line = {
    type: "line",

    /** Type guard narrowing to a line element. */
    is(element: Element): element is LineElement {
        return element.type === this.type;
    },

    /**
     * Create a line from positions.
     * @param props The line properties.
     */
    create({ positions, style, meta }: LineProps): LineElement {
        const flat = Array.isArray(positions[0])
            ? (positions as Position[]).flat()
            : (positions as number[]);

        return new Element({
            ...meta,
            type: this.type,
            geometry: new Polyline(flat, 2),
            style: style ?? DefaultLineStyle.clone(),
        });
    },
} as const satisfies TypeDef<Polyline, StrokeStyle>;
