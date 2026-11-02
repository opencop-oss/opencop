// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Accessor, AccessorContext, Color, DefaultProps, Unit } from "@deck.gl/core";
import type { PathLayerProps } from "@deck.gl/layers";
import { filterAccessorKeys, forward, type SubLayerAccessorWrapper } from "./layer.props";
import { access } from "./layer.util";

export type StrokeProps<DataT = unknown> = {
    /**
     * Whether stroke is enabled.
     * @default true
     */
    stroked?: boolean;
    /**
     * Whether dashed strokes are enabled. Turn this off to save on shader attributes.
     * @default true
     */
    dashed?: boolean;
    /**
     * The units of the line width, one of `'meters'`, `'common'`, and `'pixels'`
     * @default 'pixels'
     */
    strokeWidthUnits?: Unit;
    /**
     * Stroke width multiplier.
     * @default 1
     */
    strokeWidthScale?: number;
    /**
     * The minimum stroke width in pixels. This prop can be used to prevent the path from getting too thin when zoomed out.
     * @default 0
     */
    strokeWidthMinPixels?: number;
    /**
     * The maximum stroke width in pixels. This prop can be used to prevent the path from getting too thick when zoomed in.
     * @default Number.MAX_SAFE_INTEGER
     */
    strokeWidthMaxPixels?: number;
    /**
     * Type of joint. If `true`, draw round joints. Otherwise draw miter joints.
     * @default false
     */
    jointRounded?: boolean;
    /**
     * Type of caps. If `true`, draw round caps. Otherwise draw square caps.
     * @default false
     */
    capRounded?: boolean;
    /**
     * The maximum extent of a joint in ratio to the stroke width. Only works if `jointRounded` is `false`.
     * @default 4
     */
    miterLimit?: number;
    /**
     * If `true`, extrude the path in screen space (width always faces the camera).
     * If `false`, the width always faces up (z).
     * @default false
     */
    strokeBillboard?: boolean;
    /**
     * Stroke color accessor.
     * @default [0, 0, 0, 255]
     */
    getStrokeColor?: Accessor<DataT, Color>;
    /**
     * Stroke width accessor.
     * @default 1
     */
    getStrokeWidth?: Accessor<DataT, number>;
    /**
     * Dash array accessor.
     * @default [0, 0]
     */
    getDashArray?: Accessor<DataT, [number, number]>;
};

export const defaultStrokeProps = {
    stroked: true,
    dashed: true,
    strokeWidthUnits: "pixels",
    strokeWidthScale: 1,
    strokeWidthMinPixels: 0,
    strokeWidthMaxPixels: Number.MAX_SAFE_INTEGER,
    jointRounded: false,
    capRounded: false,
    miterLimit: 4,
    strokeBillboard: false,
    getStrokeColor: { type: "accessor", value: [0, 0, 0, 255] },
    getStrokeWidth: { type: "accessor", value: 1 },
    getDashArray: { type: "accessor", value: [0, 0] },
} as const satisfies DefaultProps<StrokeProps>;

export const STROKE_PROP_MAP = {
    strokeWidthUnits: "widthUnits",
    strokeWidthScale: "widthScale",
    strokeWidthMinPixels: "widthMinPixels",
    strokeWidthMaxPixels: "widthMaxPixels",
    jointRounded: "jointRounded",
    capRounded: "capRounded",
    miterLimit: "miterLimit",
    strokeBillboard: "billboard",
    getStrokeColor: "getColor",
    getDashArray: "getDashArray",
    getStrokeWidth: "getWidth",
} as const satisfies Partial<
    Record<keyof StrokeProps, keyof (PathLayerProps & { getDashArray: unknown })>
>;

export const forwardStrokeProps = <DataT>(
    props: Partial<StrokeProps<DataT>>,
    wrapper?: SubLayerAccessorWrapper,
) => forward(props, STROKE_PROP_MAP, wrapper);

export const forwardStrokeTriggers = (triggers: Partial<Record<keyof StrokeProps, boolean>>) =>
    forward(triggers, filterAccessorKeys(STROKE_PROP_MAP));

export type StrokeDatum = Partial<{
    path: number[];
    color: Color;
    width: number;
    dashArray: [number, number];
}>;

export const getStrokeDatum = <T>(
    props: Required<Pick<StrokeProps<T>, "getStrokeWidth" | "getDashArray" | "getStrokeColor">>,
    object: T,
    objectInfo: AccessorContext<T>,
): StrokeDatum => ({
    color: access(props.getStrokeColor, object, objectInfo),
    width: access(props.getStrokeWidth, object, objectInfo),
    dashArray: access(props.getDashArray, object, objectInfo),
});

export const strokeDatumAccessors = () =>
    ({
        getPath: (d) => d.path ?? [],
        getColor: (d) => d.color ?? [0, 0, 0, 0],
        getWidth: (d) => d.width ?? 0,
        getDashArray: (d) => d.dashArray ?? [0, 0],
    }) as const satisfies Partial<PathLayerProps<StrokeDatum>> & {
        getDashArray: Accessor<StrokeDatum, [number, number]>;
    };
