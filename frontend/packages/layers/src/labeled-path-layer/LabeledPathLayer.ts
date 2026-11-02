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
import { PathLayer } from "@deck.gl/layers";
import { PathStyleExtension } from "@deck.gl/extensions";
import * as turf from "@turf/turf";
import { omit } from "lodash";
import {
    defaultStrokeProps,
    forwardStrokeProps,
    forwardStrokeTriggers,
    type StrokeProps,
} from "../stroke.props";
import {
    defaultLabelProps,
    forwardLabelProps,
    forwardLabelTriggers,
    type LabelProps,
} from "../label.props";
import { dataDiff, diffCollection, type SubLayerArray } from "../diff.util";
import { needsRebuild, type InvalidationSpec } from "../change-detection.util";
import { mercatorMidpoint } from "@opencop/utils";
import { getAlwaysVisible, type SourceObjectDatum } from "../culled-text-layer/culling.util";
import { defaultTextProps, forwardTextProps, type TextProps } from "../text.props";
import { CulledTextLayer } from "../culled-text-layer/CulledTextLayer";
import Logger from "js-logger";

const logger = Logger.get("layers");

/** @internal Private properties of {@link LabeledPathLayer} */
type _LabeledPathLayerProps<DataT> = Omit<
    StrokeProps<DataT> & LabelProps<DataT> & TextProps<DataT>,
    "labelBillboard" | "stroked"
> & {
    data: LayerData<DataT>;
    /**
     * Path accessor — flat `[lng, lat, lng, lat, ...]` or nested `[[lng, lat], ...]`.
     * @default () => []
     */
    getPath: AccessorFunction<DataT, number[] | [number, number][]>;
    /**
     * Place a label on the midpoint of segment `i` iff `i % textPlacementMod === 0`.
     * @default 2
     */
    textPlacementMod?: number;
};

/** Properties of {@link LabeledPathLayer}. */
export type LabeledPathLayerProps<DataT = unknown> = _LabeledPathLayerProps<DataT> & LayerProps;

const defaultProps = {
    ...omit(defaultStrokeProps, "stroked"),
    ...defaultLabelProps,
    ...defaultTextProps,

    textPlacementMod: 2,

    // Accessors
    getPath: { type: "accessor", value: () => [] },
} as const satisfies DefaultProps<LabeledPathLayerProps>;

const INVALIDATION_SPEC = {
    props: ["textPlacementMod"],
    triggers: ["getPath"],
} as const satisfies InvalidationSpec;

type PathDatum = {
    path: number[];
};

type TextDatum<DataT> = SourceObjectDatum<DataT> & {
    position: [number, number];
    angle: number;
};

export class LabeledPathLayer<DataT, ExtraProps extends object = object> extends CompositeLayer<
    Required<_LabeledPathLayerProps<DataT>> & ExtraProps
> {
    static defaultProps = defaultProps;
    static layerName = "LabeledPathLayer";
    static componentName = "LabeledPathLayer";

    declare state: {
        paths: SubLayerArray<PathDatum>;
        labels: SubLayerArray<TextDatum<DataT>>;
    };

    initializeState(): void {
        this.state = {
            paths: { data: [], diff: null },
            labels: { data: [], diff: null },
        };
    }

    updateState(params: UpdateParameters<this>): void {
        if (!needsRebuild(params, INVALIDATION_SPEC)) return;

        const {
            changeFlags: { dataChanged },
        } = params;
        if (Array.isArray(dataChanged)) {
            const { paths, labels } = this.state;
            this.setState(
                diffCollection({ paths, labels }, this.buildCollection.bind(this), dataChanged),
            );
        } else {
            this.setState(this.buildCollection());
        }
    }

    renderLayers(): Layer | null | LayersList {
        const { text, dashed, extensions, updateTriggers } = this.props;
        const { paths, labels } = this.state;

        const StrokeLayer = this.getSubLayerClass("stroke", PathLayer);
        const TextLayer = this.getSubLayerClass("text", CulledTextLayer);
        const wrapper = this.getSubLayerAccessor.bind(this);

        return [
            new StrokeLayer(
                forwardStrokeProps(this.props, wrapper),
                this.getSubLayerProps({
                    id: "paths",
                    extensions: [
                        ...(extensions ?? []),
                        ...(dashed ? [new PathStyleExtension({ dash: true })] : []),
                    ],
                    updateTriggers: {
                        ...forwardStrokeTriggers(updateTriggers),
                        getPath: updateTriggers.getPath,
                    },
                }),
                {
                    data: paths.data,
                    _dataDiff: dataDiff(paths.diff),
                    positionFormat: "XY",
                    getPath: (d: PathDatum) => d.path,
                },
            ),
            new TextLayer(
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
                        getPosition: updateTriggers.getPath,
                        getAngle: updateTriggers.getPath,
                    },
                }),
                {
                    data: labels.data,
                    _dataDiff: dataDiff(labels.diff),
                    getPosition: (d: TextDatum<DataT>) => d.position,
                    getAngle: (d: TextDatum<DataT>) => d.angle,
                    billboard: false,
                    alwaysVisible: getAlwaysVisible(
                        this.props.alwaysVisible,
                        labels.data,
                        this.props.getTextIdentity,
                    ),
                },
            ),
        ];
    }

    // TODO: Emit a constant number of objects per datum so deleting data
    // cannot corrupt the layer buffers.
    // This isn't currently a bug because we have _dataDiff off for lines and polygons, but needs to be revisited.
    private buildCollection(
        dataRange: { startRow?: number; endRow?: number } = {},
    ): typeof this.state {
        const { data, getPath, textPlacementMod } = this.props;
        const { startRow, endRow } = dataRange;
        const collection: typeof this.state = {
            paths: { data: [], diff: null },
            labels: { data: [], diff: null },
        };

        const { iterable, objectInfo } = createIterable(data, startRow, endRow);

        for (const object of iterable) {
            objectInfo.index++;
            const path = getPath(object, objectInfo);
            const isNested = Array.isArray(path[0]);
            const flat: number[] = [
                ...(isNested ? (path as number[][]).flat() : (path as number[])),
            ];
            if (flat.length < 4) {
                if (import.meta.env.DEV)
                    logger.warn(`LabeledPathLayer: skipping path with fewer than 2 points.`);
                continue;
            }

            for (let i = 2; i < flat.length; i += 2) {
                const segmentIndex = i / 2;
                if (segmentIndex % textPlacementMod === 0) {
                    const start: [number, number] = [flat[i - 2], flat[i - 1]];
                    const end: [number, number] = [flat[i], flat[i + 1]];
                    const position = mercatorMidpoint(start, end);
                    let angle = -turf.rhumbBearing(start, end) + 90;
                    if (Math.abs(angle) > 90) angle += 180;

                    collection.labels.data.push(
                        this.getSubLayerRow(
                            { sourceObject: object, position, angle },
                            object,
                            objectInfo.index,
                        ),
                    );
                }
            }

            collection.paths.data.push(
                this.getSubLayerRow({ path: flat }, object, objectInfo.index),
            );
        }

        return collection;
    }
}
