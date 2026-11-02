// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    type Accessor,
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
import { access } from "../layer.util";
import {
    defaultStrokeProps,
    forwardStrokeProps,
    forwardStrokeTriggers,
    type StrokeProps,
} from "../stroke.props";
import {
    defaultFillProps,
    forwardFillProps,
    forwardFillTriggers,
    type FillProps,
} from "../fill.prop";
import {
    defaultLabelProps,
    forwardLabelProps,
    forwardLabelTriggers,
    type LabelProps,
} from "../label.props";
import { needsRebuild, type InvalidationSpec } from "../change-detection.util";
import { dataDiff, diffCollection, type SubLayerArray } from "../diff.util";
import { CulledTextLayer } from "../culled-text-layer/CulledTextLayer";
import { defaultTextProps, forwardTextProps, type TextProps } from "../text.props";

/** @internal Private properties of {@link EllipseLayer}. */
type _EllipseLayerProps<DataT> = StrokeProps<DataT> &
    FillProps<DataT> &
    LabelProps<DataT> &
    TextProps<DataT> & {
        data: LayerData<DataT>;
        /**
         * Ellipse origin accessor.
         * @default () => [0, 0]
         */
        getPosition: AccessorFunction<DataT, Position>;
        /**
         * Semi-major axis accessor (meters).
         * @default 0
         */
        getMajor: AccessorFunction<DataT, number>;
        /**
         * Semi-minor axis accessor (meters).
         * @default 0
         */
        getMinor: AccessorFunction<DataT, number>;
        /**
         * Rotation accessor (degrees from north, positive clockwise).
         * @default 0
         */
        getAngle?: Accessor<DataT, number>;
        /**
         * Number of vertices in each ellipse polygon.
         * @default 64
         */
        steps?: number;
    };

/** Properties of {@link EllispeLayer}. */
export type EllipseLayerProps<DataT = unknown> = _EllipseLayerProps<DataT> & LayerProps;

const defaultProps = {
    ...defaultStrokeProps,
    ...defaultFillProps,
    ...defaultLabelProps,
    ...defaultTextProps,

    steps: 64,

    // Accessors
    getPosition: { type: "accessor", value: () => [0, 0] },
    getMajor: { type: "accessor", value: () => 0 },
    getMinor: { type: "accessor", value: () => 0 },
    getAngle: { type: "accessor", value: 0 },
} as const satisfies DefaultProps<EllipseLayerProps>;

const INVALIDATION_SPEC = {
    props: ["steps"],
    triggers: ["getPosition", "getMajor", "getMinor", "getAngle"],
} as const satisfies InvalidationSpec;

type PolygonData = {
    polygon: number[];
};

export class EllipseLayer<DataT, ExtraProps extends object = object> extends CompositeLayer<
    Required<_EllipseLayerProps<DataT>> & ExtraProps
> {
    static defaultProps = defaultProps;
    static layerName = "EllipseLayer";
    static componentName = "EllipseLayer";

    declare state: {
        ellipses: SubLayerArray<PolygonData>;
    };

    initializeState(): void {
        this.state = {
            ellipses: { data: [], diff: null },
        };
    }

    updateState(params: UpdateParameters<this>): void {
        if (!needsRebuild(params, INVALIDATION_SPEC)) return;

        const {
            changeFlags: { dataChanged },
        } = params;
        if (Array.isArray(dataChanged)) {
            const { ellipses } = this.state;
            this.setState(
                diffCollection({ ellipses }, this.buildCollection.bind(this), dataChanged),
            );
        } else {
            this.setState(this.buildCollection());
        }
    }

    renderLayers(): Layer | null | LayersList {
        const { stroked, filled, text, dashed } = this.props;
        const { getPosition, data, _dataDiff, dataComparator, extensions, updateTriggers } =
            this.props;
        const { ellipses } = this.state;

        const StrokeLayer = this.getSubLayerClass("stroke", PathLayer);
        const FillLayer = this.getSubLayerClass("fill", SolidPolygonLayer);
        const LabelLayer = this.getSubLayerClass("text", CulledTextLayer);
        const wrapper = this.getSubLayerAccessor.bind(this);
        const geometryTrigger = [
            updateTriggers.getPosition,
            updateTriggers.getMajor,
            updateTriggers.getMinor,
            updateTriggers.getAngle,
        ];

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
                        updateTriggers: {
                            ...forwardStrokeTriggers(updateTriggers),
                            getPath: geometryTrigger,
                        },
                    }),
                    {
                        data: ellipses.data,
                        _dataDiff: dataDiff(ellipses.diff),
                        positionFormat: "XY",
                        getPath: (d: PolygonData) => d.polygon,
                    },
                ),
            filled &&
                new FillLayer(
                    forwardFillProps(this.props, wrapper),
                    this.getSubLayerProps({
                        id: "fill",
                        updateTriggers: {
                            ...forwardFillTriggers(updateTriggers),
                            getPolygon: geometryTrigger,
                        },
                    }),
                    {
                        data: ellipses.data,
                        _dataDiff: dataDiff(ellipses.diff),
                        positionFormat: "XY",
                        getPolygon: (d: PolygonData) => d.polygon,
                    },
                ),
            new LabelLayer(
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

    private buildCollection(
        dataRange: { startRow?: number; endRow?: number } = {},
    ): typeof this.state {
        const { data, getPosition, getMajor, getMinor, getAngle, steps } = this.props;
        const { startRow, endRow } = dataRange;
        const collection: typeof this.state = { ellipses: { data: [], diff: null } };

        const { iterable, objectInfo } = createIterable(data, startRow, endRow);

        for (const object of iterable) {
            objectInfo.index++;
            const origin = getPosition(object, objectInfo);
            const major = getMajor(object, objectInfo);
            const minor = getMinor(object, objectInfo);
            const angle = access(getAngle, object, objectInfo);

            const positions = turf
                .ellipse([...origin], major, minor, { steps, units: "meters", angle: angle + 90 })
                .geometry.coordinates.flat();

            collection.ellipses.data.push(
                this.getSubLayerRow({ polygon: positions.flat() }, object, objectInfo.index),
            );
        }

        return collection;
    }
}
