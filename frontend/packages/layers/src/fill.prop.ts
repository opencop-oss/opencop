// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Accessor, Color, DefaultProps } from "@deck.gl/core";
import type { SolidPolygonLayerProps } from "@deck.gl/layers";
import { filterAccessorKeys, forward, type SubLayerAccessorWrapper } from "./layer.props";

export type FillProps<DataT = unknown> = {
    /**
     * Whether to fill the polygons.
     * @default true
     */
    filled?: boolean;
    /**
     * Fill color accessor.
     * @default [0, 0, 0, 255]
     */
    getFillColor?: Accessor<DataT, Color>;
};

export const defaultFillProps = {
    filled: true,
    getFillColor: { type: "accessor", value: [0, 0, 0, 255] },
} as const satisfies DefaultProps<FillProps>;

export const FILL_PROP_MAP = {
    filled: "filled",
    getFillColor: "getFillColor",
} as const satisfies Partial<Record<keyof FillProps, keyof SolidPolygonLayerProps>>;

export const forwardFillProps = <DataT>(
    props: Partial<FillProps<DataT>>,
    wrapper?: SubLayerAccessorWrapper,
) => forward(props, FILL_PROP_MAP, wrapper);

export const forwardFillTriggers = (triggers: Partial<Record<keyof FillProps, boolean>>) =>
    forward(triggers, filterAccessorKeys(FILL_PROP_MAP));
