// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Accessor, Color, DefaultProps } from "@deck.gl/core";
import { filterAccessorKeys, forward, type SubLayerAccessorWrapper } from "./layer.props";
import type { CulledTextLayerProps } from "./culled-text-layer/CulledTextLayer";
import type { MeasurementSystem } from "@opencop/utils";

export type { MeasurementSystem };

export type MeasurementProps<DataT = unknown> = {
    /**
     * Measurement color accessor.
     * @default [255, 255, 255, 255]
     */
    getMeasurementColor?: Accessor<DataT, Color>;
    /**
     * Measurement size accessor.
     * @default 16
     */
    getMeasurementSize?: Accessor<DataT, number>;
    /**
     * Background color accessor.
     * @default [255, 255, 255, 255]
     */
    getMeasurementBackgroundColor?: Accessor<DataT, Color>;
    /**
     * Border color accessor.
     * @default [0, 0, 0, 255]
     */
    getMeasurementBorderColor?: Accessor<DataT, Color>;
    /**
     * Border width accessor.
     * @default 0
     */
    getMeasurementBorderWidth?: Accessor<DataT, number>;
    /**
     * Measurement text override accessor, the measurement counterpart of
     * `getLabel`: return an empty string to suppress an object's measurement
     * text. Return null to keep the layer's own formatted measurement.
     * @default null
     */
    getMeasurementText?: Accessor<DataT, string | null>;
    /**
     * The measurement system used by the layer.
     */
    measurementSystem?: MeasurementSystem;
};

export const defaultMeasurementProps = {
    measurementSystem: "METRIC",

    // Accessors
    getMeasurementColor: { type: "accessor", value: [255, 255, 255, 255] },
    getMeasurementSize: { type: "accessor", value: 16 },
    getMeasurementBackgroundColor: { type: "accessor", value: [255, 255, 255, 255] },
    getMeasurementBorderColor: { type: "accessor", value: [0, 0, 0, 255] },
    getMeasurementBorderWidth: { type: "accessor", value: 0 },
    getMeasurementText: { type: "accessor", value: null },
} as const satisfies DefaultProps<MeasurementProps>;

export const MEASUREMENT_PROP_MAP = {
    getMeasurementColor: "getColor",
    getMeasurementSize: "getSize",
    getMeasurementBackgroundColor: "getBackgroundColor",
    getMeasurementBorderColor: "getBorderColor",
    getMeasurementBorderWidth: "getBorderWidth",
} as const satisfies Partial<Record<keyof MeasurementProps, keyof CulledTextLayerProps>>;

export const forwardMeasurementProps = <DataT>(
    props: Partial<MeasurementProps<DataT>>,
    wrapper?: SubLayerAccessorWrapper<DataT>,
) => forward(props, MEASUREMENT_PROP_MAP, wrapper);

export const forwardMeasurementTriggers = (
    triggers: Partial<Record<keyof MeasurementProps, boolean>>,
) => forward(triggers, filterAccessorKeys(MEASUREMENT_PROP_MAP));
