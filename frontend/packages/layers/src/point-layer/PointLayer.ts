// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    type Accessor,
    type AccessorFunction,
    CompositeLayer,
    type DefaultProps,
    type Layer,
    type LayerData,
    type LayerProps,
    type LayersList,
    type Position,
    type Unit,
} from "@deck.gl/core";
import { ScatterplotLayer } from "@deck.gl/layers";
import { omit } from "lodash";
import { defaultStrokeProps, forwardStrokeProps, type StrokeProps } from "../stroke.props";
import { defaultFillProps, forwardFillProps, type FillProps } from "../fill.prop";
import {
    defaultLabelProps,
    forwardLabelProps,
    forwardLabelTriggers,
    type LabelProps,
} from "../label.props";
import { defaultTextProps, forwardTextProps, type TextProps } from "../text.props";
import { CulledTextLayer } from "../culled-text-layer/CulledTextLayer";

/** @internal Private properties of {@link PointLayer}. */
type _PointLayerProps<DataT> = Omit<
    StrokeProps<DataT> & FillProps<DataT> & LabelProps<DataT> & TextProps<DataT>,
    "dashed"
> & {
    data: LayerData<DataT>;

    /**
     * Point position accessor.
     * @default () => [0, 0]
     */
    getPosition: AccessorFunction<DataT, Position>;
    /**
     * Point radius accessor.
     * @default 1
     */
    getRadius?: Accessor<DataT, number>;
    /**
     * Units for radius — `"pixels"`, `"meters"`, or `"common"`.
     * @default "pixels"
     */
    radiusUnits?: Unit;
    /**
     * Multiplier applied to all radii.
     * @default 1
     */
    radiusScale?: number;
    /**
     * Minimum radius in pixels.
     * @default 0
     */
    radiusMinPixels?: number;
    /**
     * Maximum radius in pixels.
     * @default Number.MAX_SAFE_INTEGER
     */
    radiusMaxPixels?: number;
};

/** Properties of {@link PointLayer}. */
export type PointLayerProps<DataT = unknown> = _PointLayerProps<DataT> & LayerProps;

const defaultProps = {
    ...omit(defaultStrokeProps, "dashed"),
    ...defaultFillProps,
    ...defaultLabelProps,
    ...defaultTextProps,

    radiusUnits: "pixels",
    radiusScale: 1,
    radiusMinPixels: 0,
    radiusMaxPixels: Number.MAX_SAFE_INTEGER,

    // Accessors
    getPosition: { type: "accessor", value: () => [0, 0] },
    getRadius: { type: "accessor", value: 1 },
    getLabelPixelOffset: { type: "accessor", value: [0, 0] },
} as const satisfies DefaultProps<PointLayerProps>;

export class PointLayer<DataT, ExtraProps extends object = object> extends CompositeLayer<
    Required<_PointLayerProps<DataT>> & ExtraProps
> {
    static defaultProps = defaultProps;
    static layerName = "PointLayer";
    static componentName = "PointLayer";

    renderLayers(): Layer | null | LayersList {
        const { text } = this.props;
        const {
            getPosition,
            getStrokeColor,
            getRadius,
            radiusUnits,
            radiusScale,
            radiusMinPixels,
            radiusMaxPixels,

            data,
            _dataDiff,
            dataComparator,
            extensions,
            updateTriggers,
        } = this.props;

        const ScatterLayer = this.getSubLayerClass("scatter", ScatterplotLayer);
        const TextLayer = this.getSubLayerClass("text", CulledTextLayer);
        const wrapper = this.getSubLayerAccessor.bind(this);

        return [
            new ScatterLayer(
                {
                    ...forwardFillProps(this.props, wrapper),
                    ...forwardStrokeProps(this.props, wrapper),
                    getLineColor: this.getSubLayerAccessor(getStrokeColor),
                    getPosition: this.getSubLayerAccessor(getPosition),
                    getRadius: this.getSubLayerAccessor(getRadius),
                    radiusUnits,
                    radiusScale,
                    radiusMinPixels,
                    radiusMaxPixels,
                },
                this.getSubLayerProps({
                    id: "scatter",
                    extensions,
                    updateTriggers: {
                        getPosition: updateTriggers.getPosition,
                        getRadius: updateTriggers.getRadius,
                        getFillColor: updateTriggers.getFillColor,
                        getLineColor: updateTriggers.getStrokeColor,
                        getLineWidth: updateTriggers.getStrokeWidth,
                    },
                }),
                {
                    data,
                    _dataDiff,
                    dataComparator,
                },
            ),
            new TextLayer(
                {
                    ...forwardTextProps(this.props),
                    ...forwardLabelProps(this.props, wrapper),
                    getPosition: this.getSubLayerAccessor(getPosition),
                    text,
                },
                this.getSubLayerProps({
                    id: "text",
                    pickable: false,
                    updateTriggers: {
                        ...forwardLabelTriggers(updateTriggers),
                        getPosition: updateTriggers.getPosition,
                        getPixelOffset: updateTriggers.getLabelPixelOffset,
                    },
                }),
                {
                    data,
                    _dataDiff,
                    dataComparator,
                    alwaysVisible: this.props.alwaysVisible,
                    getBillboard: 1,
                },
            ),
        ];
    }
}
