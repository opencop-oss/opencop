// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Accessor, AccessorFunction, Color, DefaultProps, Unit } from "@deck.gl/core";
import type { IconLayerProps } from "@deck.gl/layers";
import { filterAccessorKeys, forward, type SubLayerAccessorWrapper } from "./layer.props";

/**
 * Auto-packing icon definition.
 */
export type UnpackedIcon = {
    /** Url to fetch the icon. */
    url: string;
    /** Unique identifier of the icon. Icons of the same id are only fetched once. Falls back to `url`. */
    id?: string;
    /** Width of the icon. */
    width: number;
    /** Height of the icon. */
    height: number;
    /** Horizontal position of the icon anchor. Default: half width. */
    anchorX?: number;
    /** Vertical position of the icon anchor. Default: half height. */
    anchorY?: number;
    /** Whether the icon is a transparency mask. If `true`, the color from {@link IconProps.getIconColor getIconColor} is applied. */
    mask?: boolean;
};

export type IconProps<DataT = unknown> = {
    /**
     * Whether icons are enabled.
     * @default true
     */
    icon?: boolean;
    /**
     * If `true`, the icon always faces the camera. Otherwise the icon faces up (z).
     * @default true
     */
    iconBillboard?: boolean;
    /**
     * The units of the icon size, one of `'meters'`, `'common'`, and `'pixels'`.
     * @default 'pixels'
     */
    iconSizeUnits?: Unit;
    /**
     * Icon size multiplier.
     * @default 1
     */
    iconSizeScale?: number;
    /**
     * The minimum size in pixels. When using non-pixel {@link IconProps.iconSizeUnits iconSizeUnits},
     * this prop can be used to prevent the icon from getting too small when zoomed out.
     * @default 0
     */
    iconSizeMinPixels?: number;
    /**
     * The maximum size in pixels. When using non-pixel {@link IconProps.iconSizeUnits iconSizeUnits},
     * this prop can be used to prevent the icon from getting too big when zoomed in.
     * @default Number.MAX_SAFE_INTEGER
     */
    iconSizeMaxPixels?: number;
    /**
     * The dimension to scale the image against.
     * @default "height"
     */
    iconSizeBasis?: "height" | "width";
    /**
     * Discard pixels whose opacity is below this threshold. A discarded pixel creates a "hole"
     * in the icon that is not considered part of the object.
     * @default 0.05
     */
    iconAlphaCutoff?: number;
    /**
     * Icon definition accessor. Returns the icon id (when using a pre-packed `iconAtlas` +
     * `iconMapping`) or an {@link UnpackedIcon} (auto-packing mode).
     */
    getIcon?: AccessorFunction<DataT, string> | AccessorFunction<DataT, UnpackedIcon>;
    /**
     * Icon color accessor (applied when the icon is a transparency mask).
     * @default [0, 0, 0, 255]
     */
    getIconColor?: Accessor<DataT, Color>;
    /**
     * Icon size accessor.
     * @default 1
     */
    getIconSize?: Accessor<DataT, number>;
    /**
     * Icon rotation accessor, in degrees.
     * @default 0
     */
    getIconAngle?: Accessor<DataT, number>;
    /**
     * Icon pixel offset accessor.
     * @default [0, 0]
     */
    getIconPixelOffset?: Accessor<DataT, [number, number]>;
};

export const defaultIconProps = {
    icon: true,
    iconBillboard: true,
    iconSizeUnits: "pixels",
    iconSizeScale: 1,
    iconSizeMinPixels: 0,
    iconSizeMaxPixels: Number.MAX_SAFE_INTEGER,
    iconSizeBasis: "height",
    iconAlphaCutoff: 0.05,
    getIcon: { type: "accessor", value: () => "" },
    getIconColor: { type: "accessor", value: [0, 0, 0, 255] },
    getIconSize: { type: "accessor", value: 1 },
    getIconAngle: { type: "accessor", value: 0 },
    getIconPixelOffset: { type: "accessor", value: [0, 0] },
} as const satisfies DefaultProps<IconProps>;

export const ICON_PROP_MAP = {
    iconBillboard: "billboard",
    iconSizeUnits: "sizeUnits",
    iconSizeScale: "sizeScale",
    iconSizeMinPixels: "sizeMinPixels",
    iconSizeMaxPixels: "sizeMaxPixels",
    iconSizeBasis: "sizeBasis",
    iconAlphaCutoff: "alphaCutoff",
    getIcon: "getIcon",
    getIconColor: "getColor",
    getIconSize: "getSize",
    getIconAngle: "getAngle",
    getIconPixelOffset: "getPixelOffset",
} as const satisfies Partial<Record<keyof IconProps, keyof IconLayerProps>>;

export const forwardIconProps = <DataT>(
    props: Partial<IconProps<DataT>>,
    wrapper?: SubLayerAccessorWrapper<DataT>,
) => forward(props, ICON_PROP_MAP, wrapper);

export const forwardIconTriggers = (triggers: Partial<Record<keyof IconProps, boolean>>) =>
    forward(triggers, filterAccessorKeys(ICON_PROP_MAP));
