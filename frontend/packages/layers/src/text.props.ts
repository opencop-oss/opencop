// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Accessor, AccessorFunction, Color, DefaultProps, Unit } from "@deck.gl/core";
import {
    defaultFontSettings,
    forward,
    type FontSettings,
    type SubLayerAccessorWrapper,
} from "./layer.props";
import type { DiffableTextLayerProps } from "./diffable-text-layer/DiffableTextLayer";
import { access } from "./layer.util";
import type { LabelProps } from "./label.props";
import type { MeasurementProps } from "./measurement.props";
import type { CulledTextLayerProps } from "./culled-text-layer/CulledTextLayer";

export type TextProps<DataT = unknown> = {
    /** Whether text is enabled. */
    text: boolean;
    /**
     * Text size multiplier.
     * @default 1
     */
    textSizeScale?: number;
    /**
     * The units of the size, one of `'meters'`, `'common'`, and `'pixels'`.
     * @default 'pixels'
     */
    textSizeUnits?: Unit;
    /**
     * The minimum size in pixels. When using non-pixel `sizeUnits`, this prop can be used to prevent the icon from getting too small when zoomed out.
     * @default 0
     */
    textSizeMinPixels?: number;
    /**
     * The maximum size in pixels. When using non-pixel `sizeUnits`, this prop can be used to prevent the icon from getting too big when zoomed in.
     * @default Number.MAX_SAFE_INTEGER
     */
    textSizeMaxPixels?: number;
    /** Whether to render background for the text blocks.
     * @default false
     */
    textBackground?: boolean;
    /** The border radius of the background.
     * If a number is supplied, it is the same border radius in pixel for all corners.
     * If an array of 4 is supplied, it is interpreted as `[bottom_right_corner, top_right_corner, bottom_left_corner, top_left_corner]` border radius in pixel.
     * @default 0
     */
    textBackgroundBorderRadius?: number | Readonly<[number, number, number, number]>;
    /**
     * The padding around the text to position the background. Only effective if content box is unset.
     * If an array of 2 is supplied, it is interpreted as `[padding_x, padding_y]` in pixels.
     * If an array of 4 is supplied, it is interpreted as `[padding_left, padding_top, padding_right, padding_bottom]` in pixels.
     * @default [0, 0, 0, 0]
     */
    textBackgroundPadding?: Readonly<[number, number]> | Readonly<[number, number, number, number]>;
    /**
     * Specifies a list of characters to include in the font. If set to 'auto', will be automatically generated from the data set.
     * @default (ASCII characters 32-128)
     */
    characterSet?: "auto" | string[] | Set<string>;
    /** CSS font family
     * @default 'Monaco, monospace'
     */
    fontFamily?: FontSettings["fontFamily"];
    /** CSS font weight
     * @default 'normal'
     */
    fontWeight?: FontSettings["fontWeight"];
    /** A unitless number that will be multiplied with the current text size to set the line height.
     * @default 1.0
     */
    lineHeight?: number;
    /**
     * Width of outline around the text, relative to the text size. Only effective if `fontSettings.sdf` is `true`.
     * @default 0
     */
    textOutlineWidth?: number;
    /**
     * Color of outline around the text, in `[r, g, b, [a]]`. Each channel is a number between 0-255 and `a` is 255 if not supplied.
     * @default [0, 0, 0, 255]
     */
    textOutlineColor?: Color;
    /**
     * Advance options for fine tuning the appearance and performance of the generated shared `fontAtlas`.
     */
    fontSettings?: FontSettings;
    /**
     * Available options are `break-all` and `break-word`. A valid `maxWidth` has to be provided to use `wordBreak`.
     * @default 'break-word'
     */
    wordBreak?: "break-word" | "break-all";
    /**
     * A unitless number that will be multiplied with the current text size to set the width limit of a string.
     * If specified, when the text is longer than the width limit, it will be wrapped into multiple lines using
     * the strategy of `wordBreak`.
     * @default -1
     */
    maxWidth?: number;
    /**
     * Minimum visible region of the content box in screen pixels. If the visible width or height is smaller than the specified cutoff, the corresponding text is hidden completely.
     * This prop can be used to set the minimum length of clipped texts to improve readability.
     * @default [0, 0]
     */
    contentCutoffPixels?: [width: number, height: number];

    /**
     * Align the text horizontally to the visible region of the content box.
     * @default 'none'
     */
    contentAlignHorizontal?: "start" | "end" | "center" | "none";

    /**
     * Align the text vertically to the visible region of the content box.
     * @default 'none'
     */
    contentAlignVertical?: "start" | "end" | "center" | "none";

    // TODO: Define clearer base layer api for plugins
    //
    // There are many props that need to be passed to layers for core map functionality (hovering, selecting, zoom culling, etc)
    // and we don't want plugin authors to have to worry about all these props just to get a new layer to work.
    // Need to define a set of base layer props that all composite layers take in for core functionality to remain intact.
    zoomThreshold?: number;
    alwaysVisible?: DataT[];
    /** Stable identity used to match interaction objects to derived rows. */
    getTextIdentity?: (d: DataT) => string | undefined;
    culled?: boolean;
};

export const defaultTextProps = {
    text: true,
    textSizeScale: 1,
    textSizeUnits: "pixels",
    textSizeMinPixels: 0,
    textSizeMaxPixels: Number.MAX_SAFE_INTEGER,

    textBackground: false,
    textBackgroundBorderRadius: { type: "object", value: 0 },
    textBackgroundPadding: { type: "array", value: [0, 0, 0, 0] },

    characterSet: "auto",
    fontFamily: defaultFontSettings.fontFamily,
    fontWeight: defaultFontSettings.fontWeight,
    lineHeight: 1.0,
    textOutlineWidth: { type: "number", value: 0, min: 0 },
    textOutlineColor: { type: "color", value: [0, 0, 0, 0] },
    fontSettings: { type: "object", value: {}, compare: 1 },

    wordBreak: "break-word",
    maxWidth: { type: "number", value: -1 },
    contentCutoffPixels: { type: "array", value: [0, 0] },
    contentAlignHorizontal: "none",
    contentAlignVertical: "none",
    zoomThreshold: 5,
    alwaysVisible: [],
    getTextIdentity: { type: "function", value: () => undefined },
    culled: true,
} as const satisfies DefaultProps<TextProps>;

export const TEXT_PROP_MAP = {
    textSizeScale: "sizeScale",
    textSizeUnits: "sizeUnits",
    textSizeMinPixels: "sizeMinPixels",
    textSizeMaxPixels: "sizeMaxPixels",
    textBackground: "background",
    textBackgroundBorderRadius: "backgroundBorderRadius",
    textBackgroundPadding: "backgroundPadding",
    characterSet: "characterSet",
    fontFamily: "fontFamily",
    fontWeight: "fontWeight",
    lineHeight: "lineHeight",
    textOutlineWidth: "outlineWidth",
    textOutlineColor: "outlineColor",
    fontSettings: "fontSettings",
    wordBreak: "wordBreak",
    maxWidth: "maxWidth",
    contentCutoffPixels: "contentCutoffPixels",
    contentAlignHorizontal: "contentAlignHorizontal",
    contentAlignVertical: "contentAlignVertical",
    zoomThreshold: "zoomThreshold",
    alwaysVisible: "alwaysVisible",
    culled: "culled",
} as const satisfies Partial<Record<keyof TextProps, keyof CulledTextLayerProps>>;

export const forwardTextProps = <DataT>(props: Partial<TextProps<DataT>>) =>
    forward(props, TEXT_PROP_MAP);

export type TextKind = "label" | "measurement";

export type MeasuredTextDatum = {
    kind: TextKind;
    text?: string; // measurements pre-populate; labels resolve getLabel from source
    position: readonly [number, number];
    angle?: number; // in-plane rotation; text with an angle lies flat (non-billboard)
    anchor?: "start" | "middle" | "end"; // horizontal anchor; defaults to "middle"
};

const dispatch = <DataT, T>(
    wrapper: SubLayerAccessorWrapper<DataT>,
    label: Accessor<DataT, T>,
    measurement: Accessor<DataT, T>,
): AccessorFunction<MeasuredTextDatum, T> => {
    const l = wrapper(label);
    const m = wrapper(measurement);
    return (d, info) => {
        const acc = d.kind === "label" ? l : m;
        return access(acc, d as DataT, info);
    };
};

export function measuredTextAccessors<DataT>(
    props: Required<MeasurementProps<DataT> & LabelProps<DataT>>,
    wrapper: SubLayerAccessorWrapper<DataT>,
): Partial<DiffableTextLayerProps<MeasuredTextDatum>> {
    const labelText = wrapper(props.getLabel);
    const measurementText = wrapper(props.getMeasurementText);
    return {
        getPosition: (d) => d.position,
        getText: (d, info) =>
            d.kind === "label"
                ? (labelText as AccessorFunction<MeasuredTextDatum, string>)(d, info)
                : (access(measurementText, d as DataT, info) ?? d.text ?? ""),
        getSize: dispatch(wrapper, props.getLabelSize, props.getMeasurementSize),
        getColor: dispatch(wrapper, props.getLabelColor, props.getMeasurementColor),
        getBackgroundColor: dispatch(
            wrapper,
            props.getLabelBackgroundColor,
            props.getMeasurementBackgroundColor,
        ),
        getBorderColor: dispatch(
            wrapper,
            props.getLabelBorderColor,
            props.getMeasurementBorderColor,
        ),
        getBorderWidth: dispatch(
            wrapper,
            props.getLabelBorderWidth,
            props.getMeasurementBorderWidth,
        ),
        getPixelOffset: dispatch(wrapper, props.getLabelPixelOffset, [0, 0]),
        getAngle: (d) => d.angle ?? 0,
        getTextAnchor: (d) => d.anchor ?? "middle",
        // Measured text lies flat on map, labels are billboarded.
        getBillboard: (d) => (d.angle != null ? 0 : 1),
    };
}

export const forwardMeasuredTextTriggers = (
    triggers: Partial<Record<keyof LabelProps | keyof MeasurementProps, boolean>>,
) => ({
    getSize: [triggers.getLabelSize, triggers.getMeasurementSize],
    getColor: [triggers.getLabelColor, triggers.getMeasurementColor],
    getBackgroundColor: [triggers.getLabelBackgroundColor, triggers.getMeasurementBackgroundColor],
    getBorderWidth: [triggers.getLabelBorderWidth, triggers.getMeasurementBorderWidth],
    getBorderColor: [triggers.getLabelBorderColor, triggers.getMeasurementBorderColor],
    getPixelOffset: triggers.getLabelPixelOffset,
});
