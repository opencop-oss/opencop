// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    type Accessor,
    type AccessorFunction,
    type Color,
    CompositeLayer,
    createIterable,
    type DefaultProps,
    type Layer,
    type LayerData,
    type LayerProps,
    type LayersList,
    type Unit,
    type UpdateParameters,
} from "@deck.gl/core";
import { IconLayer, PathLayer } from "@deck.gl/layers";
import * as turf from "@turf/turf";
import { PathStyleExtension } from "@deck.gl/extensions";
import { MercatorCoordinate } from "maplibre-gl";
import { formatAngle } from "../angle-layer/angle-layer.util";
import { formatLength } from "./arrow-layer.util";
import { omit } from "lodash";
import {
    defaultStrokeProps,
    forwardStrokeProps,
    forwardStrokeTriggers,
    type StrokeProps,
} from "../stroke.props";
import { defaultLabelProps, type LabelProps } from "../label.props";
import {
    defaultMeasurementProps,
    type MeasurementProps,
    type MeasurementSystem,
} from "../measurement.props";
import { dataDiff, diffCollection, type SubLayerArray } from "../diff.util";
import { needsRebuild, type InvalidationSpec } from "../change-detection.util";
import { getMercatorVector } from "../geometry.util";
import { mercatorMidpoint } from "@opencop/utils";
import { CulledTextLayer } from "../culled-text-layer/CulledTextLayer";
import { getAlwaysVisible, type SourceObjectDatum } from "../culled-text-layer/culling.util";
import {
    defaultTextProps,
    forwardMeasuredTextTriggers,
    forwardTextProps,
    measuredTextAccessors,
    type MeasuredTextDatum,
    type TextProps,
} from "../text.props";
import type { AngleUnit } from "../angle-layer/angle-layer.util";

const ARROW_SIZE = 32;
/** Vertical position of the chevron tip in icon-pixel space. Match anchorY. */
const ARROW_TIP_Y = 2;
const ARROW_URL = `data:image/svg+xml;utf8,${encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${ARROW_SIZE} ${ARROW_SIZE}" width="${ARROW_SIZE}" height="${ARROW_SIZE}"><path d="M4 14 L${ARROW_SIZE / 2} ${ARROW_TIP_Y} L28 14" stroke="white" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" fill="none"/></svg>`,
)}`;

/** @internal Private properties of {@link ArrowLayer}. */
type _ArrowLayerProps<DataT> = Omit<
    StrokeProps<DataT> & LabelProps<DataT> & MeasurementProps<DataT> & TextProps<DataT>,
    "stroked"
> & {
    data: LayerData<DataT>;
    /**
     * The units of the head icon size, one of `'meters'`, `'common'`, and `'pixels'`.
     * @default 'pixels'
     */
    headSizeUnits?: Unit;
    /**
     * Head size multiplier.
     * @default 1
     */
    headSizeScale?: number;
    /**
     * The minimum size in pixels. When using non-pixel {@link _ArrayLayerProps.headSizeUnits headSizeUnits},
     * this prop can be used to prevent the head icon from getting too small when zoomed out.
     * @default 0
     */
    headSizeMinPixels?: number;
    /**
     * The maximum size in pixels. When using non-pixel {@link _ArrayLayerProps.headSizeUnits headSizeUnits},
     * this prop can be used to prevent the head icon from getting too big when zoomed in.
     * @default Number.MAX_SAFE_INTEGER
     */
    headSizeMaxPixels?: number;
    /**
     * The unit of measurement for bearing.
     * @default "degrees"
     */
    bearingUnit?: AngleUnit;
    /**
     * Function to format the bearing measurement. Omit for default formatting
     *
     * @param bearing The bearing in degrees.
     * @param unit The desired unit.
     * @returns The formatted string.
     */
    formatBearing?: (bearing: number, unit: AngleUnit) => string;
    /**
     * Function to format the length of the arrow. Omit for defaulting formatting.
     *
     * @param length The length of the arrow in meters.
     * @param system The desired system of measurement.
     * @returns The formatted string.
     */
    formatLength?: (length: number, system: MeasurementSystem) => string;
    /**
     * Start of arrow accessor.
     * @default () => [0, 0]
     */
    getStart: AccessorFunction<DataT, [number, number]>;
    /**
     * End of arrow accessor.
     * @default () => [0, 0]
     */
    getEnd: AccessorFunction<DataT, [number, number]>;
    /**
     * Color of arrow head accessor.
     * @default [0, 0, 0, 0]
     */
    getHeadColor?: Accessor<DataT, Color>;
    /**
     * Size of arrow head accessor.
     * @default 0
     */
    getHeadSize?: Accessor<DataT, number>;
};

export type ArrowLayerProps<DataT = unknown> = _ArrowLayerProps<DataT> & LayerProps;

const defaultProps = {
    ...omit(defaultStrokeProps, "stroked"),
    ...defaultMeasurementProps,
    ...defaultLabelProps,
    ...defaultTextProps,

    bearingUnit: "degrees",

    headSizeUnits: "pixels",
    headSizeScale: 1,
    headSizeMinPixels: 0,
    headSizeMaxPixels: Number.MAX_SAFE_INTEGER,

    formatBearing: formatAngle,
    formatLength: formatLength,

    // Accessors
    getStart: { type: "accessor", value: () => [0, 0] },
    getEnd: { type: "accessor", value: () => [0, 0] },
    getHeadColor: { type: "accessor", value: [0, 0, 0, 0] },
    getHeadSize: { type: "accessor", value: 0 },
} as const satisfies DefaultProps<ArrowLayerProps>;

const INVALIDATION_SPEC = {
    props: ["measurementSystem", "bearingUnit"],
    triggers: ["getStart", "getEnd"],
} as const satisfies InvalidationSpec;

type StrokeDatum = {
    path: number[];
};

type IconDatum = {
    position: [number, number];
    angle: number;
};
type ArrowTextDatum<DataT> = MeasuredTextDatum & SourceObjectDatum<DataT>;

export class ArrowLayer<DataT, ExtraProps extends object = object> extends CompositeLayer<
    Required<_ArrowLayerProps<DataT>> & ExtraProps
> {
    static defaultProps = defaultProps;
    static layerName = "ArrowLayer";
    static componentName = "ArrowLayer";

    declare state: {
        lines: SubLayerArray<StrokeDatum>;
        heads: SubLayerArray<IconDatum>;
        text: SubLayerArray<ArrowTextDatum<DataT>>;
    };

    initializeState(): void {
        this.state = {
            lines: { data: [], diff: null },
            heads: { data: [], diff: null },
            text: { data: [], diff: null },
        };
    }

    updateState(params: UpdateParameters<this>): void {
        if (!needsRebuild(params, INVALIDATION_SPEC)) return;

        const {
            changeFlags: { dataChanged },
        } = params;
        if (Array.isArray(dataChanged)) {
            const { lines, heads, text } = this.state;
            this.setState(
                diffCollection(
                    { lines, heads, text },
                    this.buildCollection.bind(this),
                    dataChanged,
                ),
            );
        } else {
            this.setState(this.buildCollection());
        }
    }

    renderLayers(): Layer | null | LayersList {
        const { dashed, text: textEnabled, extensions, updateTriggers } = this.props;
        const { headSizeUnits, headSizeScale, headSizeMinPixels, headSizeMaxPixels } = this.props;
        const { getHeadColor, getHeadSize } = this.props;

        const { lines, heads, text } = this.state;

        const LineLayer = this.getSubLayerClass("lines", PathLayer);
        const HeadLayer = this.getSubLayerClass("heads", IconLayer);
        const TextLayer = this.getSubLayerClass("text", CulledTextLayer);
        const wrapper = this.getSubLayerAccessor.bind(this);
        const geometryTrigger = [updateTriggers.getStart, updateTriggers.getEnd];

        return [
            new LineLayer(
                forwardStrokeProps(this.props, wrapper),
                this.getSubLayerProps({
                    id: "lines",
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
                    data: lines.data,
                    _dataDiff: dataDiff(lines.diff),
                    positionFormat: "XY",
                    getPath: (d: StrokeDatum) => d.path,
                },
            ),
            new HeadLayer(
                {
                    getColor: this.getSubLayerAccessor(getHeadColor),
                    getSize: this.getSubLayerAccessor(getHeadSize),

                    sizeUnits: headSizeUnits,
                    sizeScale: headSizeScale,
                    sizeMinPixels: headSizeMinPixels,
                    sizeMaxPixels: headSizeMaxPixels,
                },
                this.getSubLayerProps({
                    id: "heads",
                    updateTriggers: {
                        getColor: updateTriggers.getHeadColor,
                        getSize: updateTriggers.getHeadSize,
                        getAngle: geometryTrigger,
                    },
                }),
                {
                    data: heads.data,
                    _dataDiff: dataDiff(heads.diff),
                    getIcon: () => "arrowHead",
                    getPosition: (d: IconDatum) => d.position,
                    getAngle: (d: IconDatum) => d.angle,
                    iconMapping: {
                        arrowHead: {
                            x: 0,
                            y: 0,
                            anchorX: ARROW_SIZE / 2,
                            anchorY: ARROW_TIP_Y,
                            width: ARROW_SIZE,
                            height: ARROW_SIZE,
                            mask: true,
                        },
                    },
                    iconAtlas: ARROW_URL,
                    billboard: false,
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
                    getBillboard: 0,
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
        const {
            data,
            getStart,
            getEnd,
            bearingUnit,
            measurementSystem,
            formatBearing,
            formatLength,
        } = this.props;
        const { startRow, endRow } = dataRange;
        const collection: typeof this.state = {
            lines: { data: [], diff: null },
            heads: { data: [], diff: null },
            text: { data: [], diff: null },
        };

        const { iterable, objectInfo } = createIterable(data, startRow, endRow);

        for (const object of iterable) {
            objectInfo.index++;

            const start = getStart(object, objectInfo);
            const end = getEnd(object, objectInfo);

            const bearing = turf.bearing(start, end);

            const rhumb = turf.rhumbBearing(start, end);
            let textAngle = -rhumb + 90;
            if (Math.abs(textAngle) >= 90) textAngle += 180;

            const length = turf.distance(start, end, { units: "meters" });
            const formattedBearing = formatBearing(bearing, bearingUnit);
            const formattedLength = formatLength(length, measurementSystem);
            const startMerc = getMercatorVector(start);
            const endMerc = getMercatorVector(end);
            const toEnd = endMerc.clone().subtract(startMerc);

            // Put length label at 1/6 of line, bearing label at 5/6 of line.
            const lengthVec = toEnd
                .clone()
                .scale(1 / 6)
                .add(startMerc);
            const bearingVec = toEnd
                .clone()
                .scale(5 / 6)
                .add(startMerc);
            const [lengthPos, bearingPos] = [lengthVec, bearingVec].map((vec) =>
                new MercatorCoordinate(vec.x, vec.y).toLngLat().toArray(),
            );

            const labelPosition = mercatorMidpoint(start, end);

            collection.text.data.push(
                ...(
                    [
                        {
                            kind: "measurement",
                            text: formattedLength,
                            position: lengthPos,
                            angle: textAngle,
                            anchor: "start",
                        },
                        {
                            kind: "measurement",
                            text: formattedBearing,
                            position: bearingPos,
                            angle: textAngle,
                            anchor: "end",
                        },
                        { kind: "label", position: labelPosition, angle: textAngle },
                    ] as MeasuredTextDatum[]
                ).map((row) =>
                    this.getSubLayerRow({ ...row, sourceObject: object }, object, objectInfo.index),
                ),
            );

            collection.heads.data.push(
                this.getSubLayerRow(
                    {
                        position: end,
                        angle: -rhumb,
                    },
                    object,
                    objectInfo.index,
                ),
            );

            collection.lines.data.push(
                this.getSubLayerRow(
                    {
                        path: [...start, ...end],
                    },
                    object,
                    objectInfo.index,
                ),
            );
        }

        return collection;
    }
}
