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
    type Position,
    type Unit,
    type UpdateParameters,
} from "@deck.gl/core";
import { IconLayer } from "@deck.gl/layers";
import { degreesToRadians } from "@turf/turf";
import { renderToStaticMarkup } from "react-dom/server";
import { MdArrowDropUp } from "react-icons/md";
import {
    defaultLabelProps,
    forwardLabelProps,
    forwardLabelTriggers,
    type LabelProps,
} from "../label.props";
import type { UnpackedIcon } from "../icon.props";
import { dataDiff, diffCollection, type SubLayerArray } from "../diff.util";
import { needsRebuild, type InvalidationSpec } from "../change-detection.util";
import { access } from "../layer.util";
import { CulledTextLayer } from "../culled-text-layer/CulledTextLayer";
import { getAlwaysVisible, type SourceObjectDatum } from "../culled-text-layer/culling.util";
import { defaultTextProps, forwardTextProps, type TextProps } from "../text.props";
import { RecyclingIconLayer } from "../recycling-icon-layer/RecyclingIconLayer";

export function iconToDataUrl(element: React.ReactNode): string {
    const svg = renderToStaticMarkup(element);
    return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`;
}

const ARROW_SIZE = 32;
const ARROW_URL = iconToDataUrl(MdArrowDropUp({ size: ARROW_SIZE, color: "white" }));

/**
 * Drawn when no `getIcon` is supplied — a 1×1 transparent pixel.
 *
 * A layer with no icon accessor is a wiring mistake, not a data condition, so
 * this is a visible-nowhere placeholder rather than an error: a missing icon
 * must never take the whole map down. What a *missing or unrenderable code*
 * looks like is the caller's decision (`@opencop/symbology` has its own
 * "unknown" placeholder for SIDCs), not this layer's.
 */
const MISSING_ICON: UnpackedIcon = {
    url: "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==",
    width: 1,
    height: 1,
    id: "__missing_icon__",
};

/** @internal Private properties of {@link IconElementLayer}. */
type _IconElementLayerProps<DataT> = LabelProps<DataT> &
    TextProps<DataT> & {
        data: LayerData<DataT>;

        /**
         * If `true`, the icon always faces the camera. Otherwise the icon faces up (z).
         * @default true
         */
        billboard?: boolean;
        /**
         * Units for icon size — `"pixels"`, `"meters"`, or `"common"`.
         * @default "pixels"
         */
        sizeUnits?: Unit;
        /**
         * Icon size multiplier.
         * @default 1
         */
        sizeScale?: number;
        /**
         * Minimum icon size in pixels.
         * @default 0
         */
        sizeMinPixels?: number;
        /**
         * Maximum icon size in pixels.
         * @default Number.MAX_SAFE_INTEGER
         */
        sizeMaxPixels?: number;

        /**
         * Whether the course arrow is rendered.
         * @default true
         */
        course?: boolean;
        /**
         * Course arrow size multiplier.
         * @default 1
         */
        arrowSizeScale?: number;
        /**
         * Minimum course arrow size in pixels.
         * @default 0
         */
        arrowSizeMinPixels?: number;
        /**
         * Maximum course arrow size in pixels.
         * @default Number.MAX_SAFE_INTEGER
         */
        arrowSizeMaxPixels?: number;

        /**
         * Icon accessor — resolves a datum to the image this layer draws.
         *
         * The layer's only scheme-specific input, supplied by the caller: the
         * layer resolves no codes and knows no icon scheme of its own. Each icon
         * source binds its own resolver, which is what lets an OSS capability keep
         * rendering with the symbology stack absent.
         */
        getIcon: AccessorFunction<DataT, UnpackedIcon>;
        /**
         * Icon position accessor.
         * @default () => [0, 0]
         */
        getPosition: AccessorFunction<DataT, Position>;
        /**
         * Course (bearing) accessor, in degrees.
         * @default () => 0
         */
        getCourse?: AccessorFunction<DataT, number | undefined>;
        /**
         * Icon size accessor.
         * @default 0
         */
        getSize?: Accessor<DataT, number>;
        /**
         * Course arrow size accessor.
         * @default 0
         */
        getArrowSize?: Accessor<DataT, number>;
        /**
         * Course arrow color accessor.
         * @default [200, 200, 200, 255]
         */
        getArrowColor?: Accessor<DataT, Color>;
    };

export type IconElementLayerProps<DataT = unknown> = _IconElementLayerProps<DataT> & LayerProps;

const defaultProps = {
    ...defaultLabelProps,
    ...defaultTextProps,

    // These elements are interactive on the map; course arrows carry the
    // standard light-grey. Both are the same for every caller (MIL-STD symbols,
    // media icons), so they live here rather than being repeated per factory.
    pickable: true,

    // Icon
    billboard: true,
    sizeUnits: "pixels",
    sizeScale: 1,
    sizeMinPixels: 0,
    sizeMaxPixels: Number.MAX_SAFE_INTEGER,

    // Course arrow
    course: true,
    arrowSizeScale: 1,
    arrowSizeMinPixels: 0,
    arrowSizeMaxPixels: Number.MAX_SAFE_INTEGER,

    // Accessors
    getPosition: { type: "accessor", value: () => [0, 0] },
    getIcon: { type: "accessor", value: () => MISSING_ICON },
    getCourse: { type: "accessor", value: () => 0 },
    getSize: { type: "accessor", value: 0 },
    getArrowSize: { type: "accessor", value: 0 },
    getArrowColor: { type: "accessor", value: [200, 200, 200, 255] },
} as const satisfies DefaultProps<IconElementLayerProps>;

const INVALIDATION_SPEC = {
    props: ["course", "sizeScale"],
    triggers: ["getIcon", "getSize", "getCourse", "getPosition", "getArrowColor"],
} as const satisfies InvalidationSpec;

type IconDatum<DataT> = UnpackedIcon & { size: number } & SourceObjectDatum<DataT>;

type CourseDatum = { offset: [number, number]; angle: number; color: Color };

/** The diffable sub-layer datasets — the part of state {@link IconElementLayer.buildCollection} produces. */
type IconCourseCollection<DataT> = {
    icons: SubLayerArray<IconDatum<DataT>>;
    courses: SubLayerArray<CourseDatum>;
};

/**
 * A diffable icon composite: icons, optional course arrows, and optional labels.
 *
 * **Scheme-agnostic.** It draws whatever `getIcon` returns and never inspects a
 * code or an image reference, so what an icon looks like is the caller's
 * business — symbology draws MIL-STD symbols through it and the media-icons
 * subject draws imported images, neither depending on the other. The caller
 * binds the resolver:
 *
 * ```ts
 * // MIL-STD symbols
 * getIcon: (d) => Renderer.render({ sidc: d.getCode() ?? "" }).source
 * // media icons
 * getIcon: (d) => resolveMediaIcon(MediaIcon.getHref(d))
 * ```
 *
 * Because the resolvers are memoized, calling `getIcon` per element per rebuild
 * is cheap and returns a stable reference the icon atlas can dedupe on.
 */
export class IconElementLayer<DataT, ExtraProps extends object = object> extends CompositeLayer<
    Required<_IconElementLayerProps<DataT>> & ExtraProps
> {
    static defaultProps = defaultProps;
    static layerName = "IconElementLayer";
    static componentName = "IconElementLayer";

    declare state: {
        icons: SubLayerArray<IconDatum<DataT>>;
        courses: SubLayerArray<CourseDatum>;
    };

    initializeState(): void {
        this.state = {
            icons: { data: [], diff: null },
            courses: { data: [], diff: null },
        };
    }

    updateState(params: UpdateParameters<this>): void {
        if (!needsRebuild(params, INVALIDATION_SPEC)) return;

        const {
            changeFlags: { dataChanged },
        } = params;
        if (Array.isArray(dataChanged)) {
            const { icons, courses } = this.state;
            this.setState(
                diffCollection({ icons, courses }, this.buildCollection.bind(this), dataChanged),
            );
        } else {
            this.setState(this.buildCollection());
        }
    }

    renderLayers(): Layer | null | LayersList {
        const { text, course } = this.props;
        const {
            billboard,
            sizeUnits,
            sizeScale,
            sizeMinPixels,
            sizeMaxPixels,
            arrowSizeScale,
            arrowSizeMinPixels,
            arrowSizeMaxPixels,
            getPosition,
            getSize,
            getArrowSize,
            updateTriggers,
        } = this.props;

        const { icons, courses } = this.state;

        const MilIconLayer = this.getSubLayerClass("icons", RecyclingIconLayer);
        const CourseLayer = this.getSubLayerClass("courses", IconLayer);
        const LabelLayer = this.getSubLayerClass("text", CulledTextLayer);
        const wrapper = this.getSubLayerAccessor.bind(this);

        return [
            course &&
                new CourseLayer(
                    {
                        getSize: this.getSubLayerAccessor(getArrowSize),
                        sizeScale: arrowSizeScale,
                        sizeMinPixels: arrowSizeMinPixels,
                        sizeMaxPixels: arrowSizeMaxPixels,
                    },
                    this.getSubLayerProps({
                        id: "courses",
                        pickable: false,
                        updateTriggers: {
                            getPosition: updateTriggers.getPosition,
                            getPixelOffset: updateTriggers.getCourse,
                            getAngle: updateTriggers.getCourse,
                            getSize: updateTriggers.getArrowSize,
                            getColor: updateTriggers.getArrowColor,
                        },
                    }),
                    {
                        data: courses.data,
                        _dataDiff: dataDiff(courses.diff),
                        getIcon: () => "arrow",
                        getColor: (d: CourseDatum) => d.color,
                        getPosition: this.getSubLayerAccessor(getPosition),
                        getPixelOffset: (d: CourseDatum) => d.offset,
                        getAngle: (d: CourseDatum) => d.angle,
                        billboard: false,
                        iconMapping: {
                            arrow: {
                                x: 0,
                                y: 0,
                                width: ARROW_SIZE,
                                height: ARROW_SIZE,
                                mask: true,
                            },
                        },
                        iconAtlas: ARROW_URL,
                    },
                ),
            new MilIconLayer(
                {
                    getSize: this.getSubLayerAccessor(getSize),

                    billboard,
                    sizeUnits,
                    sizeScale,
                    sizeMinPixels,
                    sizeMaxPixels,
                    sizeBasis: "width",
                },
                this.getSubLayerProps({
                    // RecyclingIconLayer bounds this never-evicting auto-packed
                    // atlas by rebuilding its IconManager in place under pressure.
                    id: "icons",
                    updateTriggers: {
                        getIcon: updateTriggers.getIcon,
                        getPosition: updateTriggers.getPosition,
                        getSize: updateTriggers.getSize,
                    },
                }),
                {
                    data: icons.data,
                    _dataDiff: dataDiff(icons.diff),
                    getIcon: (d: IconDatum<DataT>) => d,
                    getPosition: this.getSubLayerAccessor(getPosition),
                },
            ),
            new LabelLayer(
                {
                    ...forwardTextProps(this.props),
                    ...forwardLabelProps(this.props, wrapper),
                    getPosition: this.getSubLayerAccessor(getPosition),
                    getPixelOffset: (d: IconDatum<DataT>) => [
                        0,
                        d.size * 0.75 * (this.props.textSizeScale ?? 1),
                    ],
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
                    data: icons.data,
                    _dataDiff: dataDiff(icons.diff),
                    alwaysVisible: getAlwaysVisible(
                        this.props.alwaysVisible,
                        icons.data,
                        this.props.getTextIdentity,
                    ),
                    getBillboard: 1,
                },
            ),
        ];
    }

    private buildCollection(
        dataRange: { startRow?: number; endRow?: number } = {},
    ): IconCourseCollection<DataT> {
        const { data, getIcon, getSize, getCourse, getArrowColor, course, sizeScale } = this.props;
        const { startRow, endRow } = dataRange;
        const collection: IconCourseCollection<DataT> = {
            icons: { data: [], diff: null },
            courses: { data: [], diff: null },
        };
        const { iterable, objectInfo } = createIterable(data, startRow, endRow);

        for (const object of iterable) {
            objectInfo.index++;
            const size = access(getSize, object, objectInfo);
            const icon = getIcon(object, objectInfo);

            collection.icons.data.push(
                this.getSubLayerRow(
                    { ...icon, size, sourceObject: object },
                    object,
                    objectInfo.index,
                ),
            );

            // Initially this omitted course data when the source object's "course" field was nullish.
            // This was causing a bug with deletion.
            //
            // In order for each diffable layer to work, they rely on the fact that each datum emits a constant number of objects.
            // The prior behavior violated this rule, and resulted in corrupt buffers.
            //
            // Instead of omitting, we now pass a transparent arrow.
            //
            // Keep this behavior until the Layer API makes its constant-row-count
            // contract explicit for plugins.
            let courseDeg = getCourse(object, objectInfo);
            let arrowColor = access(getArrowColor, object, objectInfo);
            if (course) {
                if (courseDeg === undefined)
                    arrowColor = [arrowColor[0], arrowColor[1], arrowColor[2], 0];
                courseDeg ??= 0;
                collection.courses.data.push(
                    this.getSubLayerRow(
                        {
                            color: arrowColor,
                            offset: [
                                size *
                                    1.25 *
                                    sizeScale *
                                    Math.cos(degreesToRadians(90 - courseDeg)),
                                -size *
                                    1.25 *
                                    sizeScale *
                                    Math.sin(degreesToRadians(90 - courseDeg)),
                            ],
                            angle: -courseDeg,
                        },
                        object,
                        objectInfo.index,
                    ),
                );
            }
        }

        return collection;
    }
}
