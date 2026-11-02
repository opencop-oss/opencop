// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Polygon as PolygonGeometry } from "../geometry/Polygon";
import { DefaultTextBoxStyle, TextBoxStyle } from "../style/TextBoxStyle";
import { type TypeDef, type TypeDefProps } from "./TypeDef";
import { Element } from "../Element";
import type { Position } from "../geometry/Geometry";

/** Construction properties for {@link TextBox.create}. */
export interface TextBoxProps extends TypeDefProps<TextBoxStyle> {
    /** The box positions (4 vertices) as {@link Position} tuples or a flat number array. */
    positions: number[] | Position[];
}

export type TextBoxElement = Element<PolygonGeometry, TextBoxStyle>;

/**
 * A rectangle whose {@link TextBoxStyle} carries the content drawn inside it.
 * Its own type, not a rectangle variant: the tool registry resolves one
 * descriptor per element type.
 */
export const TextBox = {
    type: "textbox",

    /** Restarts a ring whose first edge runs west at the opposite edge, so its text reads upright. */
    uprightPositions(positions: readonly number[]): number[] {
        if (positions.length < 8 || positions[2] >= positions[0]) return [...positions];
        return [...positions.slice(4, 8), ...positions.slice(0, 4)];
    },

    /** Type guard narrowing to a textbox element. */
    is(element: Element): element is TextBoxElement {
        return element.type === this.type;
    },

    /**
     * Create a textbox element.
     * @param props The textbox properties.
     */
    create({ positions, style, meta }: TextBoxProps): TextBoxElement {
        const flat = Array.isArray(positions[0])
            ? (positions as Position[]).flat()
            : (positions as number[]);

        return new Element({
            ...meta,
            type: this.type,
            geometry: new PolygonGeometry(flat, 2),
            style: style ?? DefaultTextBoxStyle.clone(),
        });
    },
} as const satisfies TypeDef<PolygonGeometry, TextBoxStyle>;
