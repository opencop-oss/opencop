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
    type UpdateParameters,
} from "@deck.gl/core";
import { PathLayer, SolidPolygonLayer } from "@deck.gl/layers";
import { PathStyleExtension } from "@deck.gl/extensions";
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
import { dataDiff, diffCollection, type SubLayerArray } from "../diff.util";
import { needsRebuild, type InvalidationSpec } from "../change-detection.util";
import { CulledTextLayer } from "../culled-text-layer/CulledTextLayer";
import { getAlwaysVisible, type SourceObjectDatum } from "../culled-text-layer/culling.util";
import { defaultTextProps, forwardTextProps, type TextProps } from "../text.props";
import Logger from "js-logger";

const logger = Logger.get("layers");

/** @internal Private properties of {@link LabeledPolygonLayer}. */
type _LabeledPolygonLayerProps<DataT> = StrokeProps<DataT> &
    FillProps<DataT> &
    LabelProps<DataT> &
    TextProps<DataT> & {
        data: LayerData<DataT>;
        /**
         * Polygon vertices accessor.
         * @default () => []
         */
        getPolygon: AccessorFunction<DataT, number[] | [number, number][]>;
    };

/** Properties of {@link LabeledPolygonLayer}. */
export type LabeledPolygonLayerProps<DataT = unknown> = _LabeledPolygonLayerProps<DataT> &
    LayerProps;

const defaultProps = {
    ...defaultStrokeProps,
    ...defaultFillProps,
    ...defaultLabelProps,
    ...defaultTextProps,

    // Accessors
    getPolygon: { type: "accessor", value: () => [] },
} as const satisfies DefaultProps<LabeledPolygonLayerProps>;

const INVALIDATION_SPEC = {
    props: [],
    triggers: ["getPolygon"],
} as const satisfies InvalidationSpec;

type PolygonDatum = {
    polygon: number[];
};

type TextDatum<DataT> = SourceObjectDatum<DataT> & {
    position: [number, number];
};

export class LabeledPolygonLayer<DataT, ExtraProps extends object = object> extends CompositeLayer<
    Required<_LabeledPolygonLayerProps<DataT>> & ExtraProps
> {
    static defaultProps = defaultProps;
    static layerName = "LabeledPolygonLayer";
    static componentName = "LabeledPolygonLayer";

    declare state: {
        polygons: SubLayerArray<PolygonDatum>;
        labels: SubLayerArray<TextDatum<DataT>>;
    };

    initializeState(): void {
        this.state = {
            polygons: { data: [], diff: null },
            labels: { data: [], diff: null },
        };
    }

    updateState(params: UpdateParameters<this>): void {
        if (!needsRebuild(params, INVALIDATION_SPEC)) return;

        const {
            changeFlags: { dataChanged },
        } = params;
        if (Array.isArray(dataChanged)) {
            const { polygons, labels } = this.state;
            this.setState(
                diffCollection({ polygons, labels }, this.buildCollection.bind(this), dataChanged),
            );
        } else {
            this.setState(this.buildCollection());
        }
    }

    renderLayers(): Layer | null | LayersList {
        const { stroked, filled, text, dashed } = this.props;
        const { extensions, updateTriggers } = this.props;
        const { polygons, labels } = this.state;

        const StrokeLayer = this.getSubLayerClass("stroke", PathLayer);
        const FillLayer = this.getSubLayerClass("fill", SolidPolygonLayer);
        const LabelLayer = this.getSubLayerClass("text", CulledTextLayer);
        const wrapper = this.getSubLayerAccessor.bind(this);

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
                            getPath: updateTriggers.getPolygon,
                        },
                    }),
                    {
                        data: polygons.data,
                        _dataDiff: dataDiff(polygons.diff),
                        positionFormat: "XY",
                        getPath: (d: PolygonDatum) => d.polygon,
                        _pathType: "open",
                    },
                ),
            filled &&
                new FillLayer(
                    forwardFillProps(this.props, wrapper),
                    this.getSubLayerProps({
                        id: "fill",
                        updateTriggers: {
                            ...forwardFillTriggers(updateTriggers),
                            getPolygon: updateTriggers.getPolygon,
                        },
                    }),
                    {
                        data: polygons.data,
                        _dataDiff: dataDiff(polygons.diff),
                        positionFormat: "XY",
                        getPolygon: (d: PolygonDatum) => d.polygon,
                    },
                ),
            new LabelLayer(
                {
                    ...forwardTextProps(this.props),
                    ...forwardLabelProps(this.props, wrapper),
                    text,
                },
                this.getSubLayerProps({
                    id: "text",
                    pickable: false,
                    updateTriggers: {
                        ...forwardLabelTriggers(updateTriggers),
                        getPosition: updateTriggers.getPolygon,
                    },
                }),
                {
                    data: labels.data,
                    _dataDiff: dataDiff(labels.diff),
                    getPosition: (d: TextDatum<DataT>) => d.position,
                    alwaysVisible: getAlwaysVisible(
                        this.props.alwaysVisible,
                        labels.data,
                        this.props.getTextIdentity,
                    ),
                    getBillboard: 1,
                },
            ),
        ];
    }

    // TODO: Avoid skipping data when there are too few points; variable output
    // makes deletion prone to corrupting the layer buffers.
    // This is not currently a bug as we have _dataDiff functionality off for lines and polygons, but this needs to be revisited.
    private buildCollection(
        dataRange: { startRow?: number; endRow?: number } = {},
    ): typeof this.state {
        const { data, getPolygon } = this.props;
        const { startRow, endRow } = dataRange;
        const collection: typeof this.state = {
            polygons: { data: [], diff: null },
            labels: { data: [], diff: null },
        };

        const { iterable, objectInfo } = createIterable(data, startRow, endRow);

        for (const object of iterable) {
            objectInfo.index++;
            const polygon = getPolygon(object, objectInfo);
            const isNested = Array.isArray(polygon[0]);
            const flat: number[] = [
                ...(isNested ? (polygon as number[][]).flat() : (polygon as number[])),
            ];
            if (
                flat.length >= 4 &&
                (flat[flat.length - 2] !== flat[0] || flat[flat.length - 1] !== flat[1])
            ) {
                flat.push(flat[0], flat[1]); // Close the loop
            } else if (flat.length < 4) {
                if (import.meta.env.DEV)
                    logger.warn(`LabeledPolygonLayer: skipping polygon with fewer than 2 points.`);
                continue;
            }

            const centroid = polygonCentroid(flat);
            collection.labels.data.push(
                this.getSubLayerRow(
                    { sourceObject: object, position: centroid },
                    object,
                    objectInfo.index,
                ),
            );

            collection.polygons.data.push(
                this.getSubLayerRow({ polygon: flat }, object, objectInfo.index),
            );
        }

        return collection;
    }
}

/** Vertex-average centroid of a flat XY polygon. Skips the closing vertex if present. */
function polygonCentroid(flat: number[]): [number, number] {
    const closed =
        flat.length >= 4 && flat[0] === flat[flat.length - 2] && flat[1] === flat[flat.length - 1];
    const count = closed ? flat.length / 2 - 1 : flat.length / 2;
    let sx = 0;
    let sy = 0;
    for (let i = 0; i < count; i++) {
        sx += flat[i * 2];
        sy += flat[i * 2 + 1];
    }
    return [sx / count, sy / count];
}
