// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Accessor, AccessorFunction, Color, DefaultProps } from "@deck.gl/core";
import { filterAccessorKeys, forward, type SubLayerAccessorWrapper } from "./layer.props";
import type { CulledTextLayerProps } from "./culled-text-layer/CulledTextLayer";

export type LabelProps<DataT = unknown> = {
    /**
     * Label accessor.
     * @default ""
     */
    getLabel?: AccessorFunction<DataT, string>;
    /**
     * Label color accessor.
     * @default [255, 255, 255, 255]
     */
    getLabelColor?: Accessor<DataT, Color>;
    /**
     * Label size accessor.
     * @default 16
     */
    getLabelSize?: Accessor<DataT, number>;
    /**
     * Background color accessor.
     * @default [255, 255, 255, 255]
     */
    getLabelBackgroundColor?: Accessor<DataT, Color>;
    /**
     * Border color accessor.
     * @default [0, 0, 0, 255]
     */
    getLabelBorderColor?: Accessor<DataT, Color>;
    /**
     * Border width accessor.
     * @default 0
     */
    getLabelBorderWidth?: Accessor<DataT, number>;
    /**
     * Label offset from the anchor position, [x, y] in pixels
     * @default [0, 0]
     */
    getLabelPixelOffset?: Accessor<DataT, Readonly<[number, number]>>;
};

export const defaultLabelProps = {
    getLabel: { type: "accessor", value: () => "" },
    getLabelSize: { type: "accessor", value: 0 },
    getLabelColor: { type: "accessor", value: [255, 255, 255, 255] },
    getLabelBackgroundColor: { type: "accessor", value: [255, 255, 255, 255] },
    getLabelBorderColor: { type: "accessor", value: [0, 0, 0, 255] },
    getLabelBorderWidth: { type: "accessor", value: 0 },
    getLabelPixelOffset: { type: "accessor", value: [0, 0] },
} as const satisfies DefaultProps<LabelProps>;

export const LABEL_PROP_MAP = {
    getLabel: "getText",
    getLabelSize: "getSize",
    getLabelColor: "getColor",
    getLabelBackgroundColor: "getBackgroundColor",
    getLabelBorderColor: "getBorderColor",
    getLabelBorderWidth: "getBorderWidth",
    getLabelPixelOffset: "getPixelOffset",
} as const satisfies Partial<Record<keyof LabelProps, keyof CulledTextLayerProps>>;

export const forwardLabelProps = <DataT>(
    props: Partial<LabelProps<DataT>>,
    wrapper?: SubLayerAccessorWrapper<DataT>,
) => forward(props, LABEL_PROP_MAP, wrapper);

export const forwardLabelTriggers = (triggers: Partial<Record<keyof LabelProps, boolean>>) =>
    forward(triggers, filterAccessorKeys(LABEL_PROP_MAP));
