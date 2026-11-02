// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Color, Primitive } from "./primitive";
import type { AnglePrimitive } from "./batches/angle/AngleBatch";
import type { IconPrimitive } from "./batches/icon/IconBatch";
import type { PathPrimitive } from "./batches/path/PathBatch";
import type { PointPrimitive } from "./batches/point/PointBatch";
import type { PolygonPrimitive } from "./batches/polygon/PolygonBatch";
import type { TextPrimitive } from "./batches/text/TextBatch";

type NoType<T> = Omit<T, "type">;
type PrimitiveGeometryProps<T extends Primitive> = NoType<T["geometry"]>;
type PrimitiveStyleProps<T extends Primitive> = NoType<T["style"]>;
type PrimitiveProps<T extends Primitive> = {
    id: string;
    geometry: PrimitiveGeometryProps<T>;
    style: PrimitiveStyleProps<T>;
};

const factoryFor =
    <T extends Primitive>(
        geometry: T["geometry"]["type"],
        style: T["style"]["type"],
    ): ((props: PrimitiveProps<T>) => T) =>
    (props) =>
        ({
            ...props,
            geometry: {
                type: geometry,
                ...props.geometry,
            },
            style: {
                type: style,
                ...props.style,
            },
        }) as T;

export const polygon = factoryFor<PolygonPrimitive>("polygon", "polygon");
export const path = factoryFor<PathPrimitive>("path", "path");
export const icon = factoryFor<IconPrimitive>("point", "icon");
export const point = factoryFor<PointPrimitive>("point", "point");
export const text = factoryFor<TextPrimitive>("point", "text");
export const angle = factoryFor<AnglePrimitive>("point", "angle");

export const TEXT_SIZE = 15;
export const LABEL_COLOR: Color = [255, 255, 255, 255];

export type LabelPrimitiveProps = {
    id: string;
    geometry: PrimitiveGeometryProps<TextPrimitive>;
    style: Partial<
        Pick<
            PrimitiveStyleProps<TextPrimitive>,
            "offset" | "baseline" | "anchor" | "angle" | "billboard"
        >
    > & { text: string };
};

export const label = (props: LabelPrimitiveProps): TextPrimitive => ({
    ...props,
    geometry: {
        type: "point",
        ...props.geometry,
    },
    style: {
        type: "text",
        color: LABEL_COLOR,
        size: TEXT_SIZE,
        angle: 0,
        billboard: true,
        ...props.style,
    },
});

export const MEASUREMENT_COLOR: Color = [200, 200, 200, 255];

export type MeasurementPrimitiveProps = {
    id: string;
    geometry: LabelPrimitiveProps["geometry"];
    style: Omit<LabelPrimitiveProps["style"], "billboard">;
};

export const measurement = (props: MeasurementPrimitiveProps): TextPrimitive => ({
    ...props,
    geometry: {
        type: "point",
        ...props.geometry,
    },
    style: {
        type: "text",
        color: MEASUREMENT_COLOR,
        size: TEXT_SIZE,
        billboard: false,
        ...props.style,
    },
});

/** Flat `dim`-dimensional positions with `element` appended to each. */
export function interleave(array: readonly number[], element: number = 0, dim = 2): number[] {
    const result: number[] = [];
    for (let i = 0; i < array.length; i += dim) result.push(...array.slice(i, i + dim), element);
    return result;
}

/**
 * Maps HEXA or HEX to RGBA.
 * @param hexa #RRGGBBAA or #RRGGBB string.
 * @param opacity If alpha is not provided in the string, from 0 to 1.
 */
export function hexaToRgba(
    hexa: string,
    opacity: number = 1,
): [r: number, g: number, b: number, a: number] {
    const code = hexa.slice(1);

    const decimal = (hex: string) => parseInt(hex, 16);
    return [
        code.substring(0, 2),
        code.substring(2, 4),
        code.substring(4, 6),
        code.substring(6, 8) || (opacity * 255).toString(16),
    ].map(decimal) as [number, number, number, number];
}
