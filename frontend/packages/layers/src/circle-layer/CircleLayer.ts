// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    type AccessorFunction,
    CompositeLayer,
    createIterable,
    type DefaultProps,
    type Layer,
    type LayerData,
    type LayerProps,
    type LayersList,
    type Position,
    type UpdateParameters,
} from "@deck.gl/core";
import { PathLayer, SolidPolygonLayer } from "@deck.gl/layers";
import { PathStyleExtension } from "@deck.gl/extensions";
import * as turf from "@turf/turf";
import { formatLength } from "../arrow-layer/arrow-layer.util";
import { defaultStrokeProps, forwardStrokeProps, type StrokeProps } from "../stroke.props";
import { defaultFillProps, forwardFillProps, type FillProps } from "../fill.prop";
import { defaultLabelProps, type LabelProps } from "../label.props";
import {
    defaultMeasurementProps,
    type MeasurementProps,
    type MeasurementSystem,
} from "../measurement.props";
import { dataDiff, diffCollection, type SubLayerArray } from "../diff.util";
import { needsRebuild, type InvalidationSpec } from "../change-detection.util";
import { getAlwaysVisible, type SourceObjectDatum } from "../culled-text-layer/culling.util";
import {
    defaultTextProps,
    forwardMeasuredTextTriggers,
    forwardTextProps,
    measuredTextAccessors,
    type MeasuredTextDatum,
    type TextProps,
} from "../text.props";
import { CulledTextLayer } from "../culled-text-layer/CulledTextLayer";

/** @internal Private properties of {@link CircleLayer}. */
type _CircleLayerProps<DataT> = StrokeProps<DataT> &
    FillProps<DataT> &
    LabelProps<DataT> &
    MeasurementProps<DataT> &
    TextProps<DataT> & {
        data: LayerData<DataT>;
        /**
         * Circle origin accessor.
         * @default () => [0, 0]
         */
        getPosition: AccessorFunction<DataT, Position>;
        /**
         * Circle radius accessor (meters).
         * @default 0
         */
        getRadius: AccessorFunction<DataT, number>;
        /**
         * Number of vertices in each circle polygon.
         * @default 64
         */
        steps?: number;
        /**
         * Formatter for radius measurement. Omit for default formatter.
         *
         * @param radius The radius of the circle in meters.
         * @param system The desired measurement system.
         * @returns The formatted string.
         */
        formatRadius?: (radius: number, system: MeasurementSystem) => string;
    };

/** Properties of {@link CircleLayer}. */
export type CircleLayerProps<DataT = unknown> = _CircleLayerProps<DataT> & LayerProps;

const defaultProps = {
    ...defaultMeasurementProps,
    ...defaultStrokeProps,
    ...defaultFillProps,
    ...defaultLabelProps,
    ...defaultTextProps,

    steps: 64,
    formatRadius: formatLength,

    // Accessors
    getPosition: { type: "accessor", value: () => [0, 0] },
    getRadius: { type: "accessor", value: () => 0 },
} as const satisfies DefaultProps<CircleLayerProps>;

const INVALIDATION_SPEC = {
    props: ["steps", "measurementSystem"],
    triggers: ["getPosition", "getRadius"],
} as const satisfies InvalidationSpec;

type PolygonDatum = {
    polygon: number[];
};
type CircleTextDatum<DataT> = MeasuredTextDatum & SourceObjectDatum<DataT>;

export class CircleLayer<DataT, ExtraProps extends object = object> extends CompositeLayer<
    Required<_CircleLayerProps<DataT>> & ExtraProps
> {
    static defaultProps = defaultProps;
    static layerName = "CircleLayer";
    static componentName = "CircleLayer";

    declare state: {
        circles: SubLayerArray<PolygonDatum>;
        text: SubLayerArray<CircleTextDatum<DataT>>;
    };

    initializeState(): void {
        this.state = {
            circles: { data: [], diff: null },
            text: { data: [], diff: null },
        };
    }

    updateState(params: UpdateParameters<this>): void {
        if (!needsRebuild(params, INVALIDATION_SPEC)) return;

        const {
            changeFlags: { dataChanged },
        } = params;
        if (Array.isArray(dataChanged)) {
            const { circles, text } = this.state;
            this.setState(
                diffCollection({ circles, text }, this.buildCollection.bind(this), dataChanged),
            );
        } else {
            this.setState(this.buildCollection());
        }
    }

    renderLayers(): Layer | null | LayersList {
        const {
            stroked,
            filled,
            dashed,
            text: textEnabled,
            extensions,
            updateTriggers,
        } = this.props;
        const { circles, text } = this.state;

        const StrokeLayer = this.getSubLayerClass("stroke", PathLayer);
        const FillLayer = this.getSubLayerClass("fill", SolidPolygonLayer);
        const TextLayer = this.getSubLayerClass("text", CulledTextLayer);
        const wrapper = this.getSubLayerAccessor.bind(this);
        const geometryTrigger = [updateTriggers.getPosition, updateTriggers.getRadius];

        return [
            stroked &&
                new StrokeLayer(
                    forwardStrokeProps(this.props, wrapper),
                    this.getSubLayerProps({
                        id: "stroke",
                        extensions: [
                            ...(extensions ?? []),
                            ...(dashed ? [new PathStyleExtension({ dash: true })] : []),
                        ],
                    }),
                    {
                        data: circles.data,
                        _dataDiff: dataDiff(circles.diff),
                        positionFormat: "XY",
                        getPath: (d: PolygonDatum) => d.polygon,
                    },
                ),
            filled &&
                new FillLayer(
                    forwardFillProps(this.props, wrapper),
                    this.getSubLayerProps({
                        id: "fill",
                    }),
                    {
                        data: circles.data,
                        _dataDiff: dataDiff(circles.diff),
                        positionFormat: "XY",
                        getPolygon: (d: PolygonDatum) => d.polygon,
                    },
                ),
            new TextLayer(
                {
                    ...forwardTextProps(this.props),
                    text: textEnabled,
                },
                this.getSubLayerProps({
                    id: "text",
                    pickable: false,
                    updateTriggers: {
                        ...forwardMeasuredTextTriggers(updateTriggers),
                        getText: [
                            ...geometryTrigger,
                            updateTriggers.getLabel,
                            updateTriggers.getMeasurementText,
                        ],
                        getPosition: geometryTrigger,
                        getAngle: geometryTrigger,
                    },
                }),
                {
                    data: text.data,
                    _dataDiff: dataDiff(text.diff),
                    ...measuredTextAccessors(this.props, wrapper),
                    alwaysVisible: getAlwaysVisible(
                        this.props.alwaysVisible,
                        text.data,
                        this.props.getTextIdentity,
                    ),
                },
            ),
        ];
    }

    private buildCollection(
        dataRange: { startRow?: number; endRow?: number } = {},
    ): typeof this.state {
        const { data, getRadius, getPosition, formatRadius, steps, measurementSystem } = this.props;
        const { startRow, endRow } = dataRange;
        const collection: typeof this.state = {
            circles: { data: [], diff: null },
            text: { data: [], diff: null },
        };

        const degPerStep = 360 / (steps - 1);
        const stepsPer90 = 90 / degPerStep;
        const stepsPer45 = 45 / degPerStep;

        const { iterable, objectInfo } = createIterable(data, startRow, endRow);

        for (const object of iterable) {
            objectInfo.index++;
            const radius = getRadius(object, objectInfo);
            const origin = getPosition(object, objectInfo);

            const positions = turf
                .circle([...origin], radius, { steps, units: "meters" })
                .geometry.coordinates.flat();

            const text = formatRadius(radius, measurementSystem);
            for (let i = 0; i < 4; i++) {
                collection.text.data.push(
                    this.getSubLayerRow(
                        {
                            sourceObject: object,
                            kind: "measurement",
                            text,
                            angle: 45 + 90 * i,
                            position: positions[Math.floor(stepsPer45 + stepsPer90 * i)] as [
                                number,
                                number,
                            ],
                        },
                        object,
                        objectInfo.index,
                    ),
                );
            }

            collection.text.data.push(
                this.getSubLayerRow(
                    {
                        sourceObject: object,
                        kind: "label",
                        position: [origin[0], origin[1]],
                    },
                    object,
                    objectInfo.index,
                ),
            );

            collection.circles.data.push(
                this.getSubLayerRow({ polygon: positions.flat() }, object, objectInfo.index),
            );
        }

        return collection;
    }
}
