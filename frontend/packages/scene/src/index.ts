// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

// Scene
export { Scene } from "./Scene";
export { SceneLayer } from "./SceneLayer";
export type {
    SceneProps,
    BatchRenderProps,
    IconRenderProps,
    TextRenderProps,
    OutlineProps,
    FilterRange,
} from "./SceneProps";
export { SceneProvider, useScene } from "./react/SceneContext";

export { primitiveId } from "./picking/picking.util";

// Batches
export type { Batch } from "./batches/Batch";
export { PolygonBatch, PolygonPrimitive } from "./batches/polygon/PolygonBatch";
export { PathBatch, PathPrimitive } from "./batches/path/PathBatch";
export { PointBatch, PointPrimitive } from "./batches/point/PointBatch";
export { IconBatch, IconPrimitive } from "./batches/icon/IconBatch";
export {
    TextBatch,
    TextPrimitive,
    FONT_SETTINGS,
    OUTLINE_WIDTH,
    OUTLINE_COLOR,
    FONT_FAMILY,
    SDF_RADIUS,
    SDF_SMOOTHING,
    LINE_HEIGHT,
} from "./batches/text/TextBatch";
// Exported so a consumer's own glyph measurement can be tested against the
// atlas the batch lays text out by. A consumer that sizes text before the frame
// it is drawn in — growing a box to hold it, say — has to measure it somewhere,
// and nothing else couples the two.
export { FontAtlas } from "./batches/text/FontAtlas";
export {
    MarchingAntsBatch,
    type MarchingAntsPrimitive,
} from "./batches/marching-ants/MarchingAntsBatch";
export {
    MarchingAntsExtension,
    type MarchingAntsExtensionOptions,
} from "./batches/marching-ants/MarchingAntsExtension";
export { AngleBatch, type AnglePrimitive } from "./batches/angle/AngleBatch";

// Primitives
export type {
    Color,
    Primitive,
    PartialPrimitive,

    // Geometry
    PrimitiveGeometry,
    PointGeometry,
    PolygonGeometry,
    PathGeometry,

    // Styles
    PrimitiveStyle,
    PointStyle,
    IconStyle,
    TextStyle,
    PathStyle,
    PolygonStyle,
    FillPattern,
    MarchingAntsStyle,
    AngleStyle,
} from "./primitive";
export {
    polygon,
    path,
    icon,
    point,
    text,
    angle,
    label,
    measurement,
    interleave,
    hexaToRgba,
    TEXT_SIZE,
    LABEL_COLOR,
    MEASUREMENT_COLOR,
    type LabelPrimitiveProps,
    type MeasurementPrimitiveProps,
} from "./primitive.util";
