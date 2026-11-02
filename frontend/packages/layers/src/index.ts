// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

// Layer classes
export { formatAngle, type AngleUnit, type AngleOrientation } from "./angle-layer/angle-layer.util";
export { ArrowLayer, type ArrowLayerProps } from "./arrow-layer/ArrowLayer";
export { formatLength } from "./arrow-layer/arrow-layer.util";
export { CircleLayer, type CircleLayerProps } from "./circle-layer/CircleLayer";
export { EllipseLayer, type EllipseLayerProps } from "./ellipse-layer/EllipseLayer";
export {
    LabeledPathLayer,
    type LabeledPathLayerProps,
} from "./labeled-path-layer/LabeledPathLayer";
export {
    LabeledPolygonLayer,
    type LabeledPolygonLayerProps,
} from "./labeled-polygon-layer/LabeledPolygonLayer";
export {
    IconElementLayer,
    type IconElementLayerProps,
} from "./icon-element-layer/IconElementLayer";
export { RecyclingIconLayer } from "./recycling-icon-layer/RecyclingIconLayer";
export { RecyclingTextLayer } from "./recycling-text-layer/RecyclingTextLayer";
export { PointLayer, type PointLayerProps } from "./point-layer/PointLayer";
export {
    DiffableTextLayer,
    DiffableMultiIconLayer as _DiffableMultiIconLayer,
} from "./diffable-text-layer/DiffableTextLayer";
export { BinaryTextLayer } from "./binary-text-layer/BinaryTextLayer";
export { LayerDataset, type DatasetSnapshot } from "./LayerDataset";

// Shared prop shapes
export type { LabelProps } from "./label.props";
export type { MeasurementProps, MeasurementSystem } from "./measurement.props";
export {
    sourceLabel,
    sourceMeasurement,
    sourceTextVisibility,
    sourceTextVisibilityUpdateTriggers,
    hoverProps,
    textProps,
    terrainProps,
    FONT_FAMILY,
    SDF_RADIUS,
    SDF_SMOOTHING,
    TEXT_OUTLINE_WIDTH_PX,
} from "./element-layer-props";
export type { RenderContext, HostRenderContext, DataFilterOption } from "./render-context";
export type { FilterProps } from "./filter.props";

export type { TextProps } from "./text.props";
export {
    RasterOverlayLayer,
    inferWmtsMaxZoom,
    isKvpWmtsUrl,
    resolveTileUrl,
    wmsBaseUrl,
    type RasterOverlayLayerProps,
} from "./raster-overlay-layer/RasterOverlayLayer";

export {
    OutlineExtension,
    type OutlineExtensionProps,
    type OutlineExtensionOptions,
} from "./outline-extension/OutlineExtension";
export { TextCollisionExtension } from "./text-collision-extension/text-collision-extension";
export {
    TerrainOutlineExtension,
    type TerrainOutlineExtensionProps,
    type TerrainOutlineExtensionOptions,
} from "./terrain-outline-extension/TerrainOutlineExtension";
