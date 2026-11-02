// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

export { Element, defaultStale, isNeverStale, neverStale } from "./Element.ts";
export type { ElementClass, ElementProps } from "./Element.ts";
export {
    elementKeyToRef,
    elementRefToKey,
    elementRefsEqual,
    getElementHistoricalKey,
    getElementKey,
    localEventSource,
    parseElementKey,
    toElementRef,
} from "./identity.ts";
export type { ElementIdentity, ElementRef } from "./identity.ts";
export { Reactive } from "./Reactive.ts";
export type { DraftObject } from "./draft-object.ts";

// Geometry
export { BBOX_STEPS, Geometry, GeometryError } from "./geometry/Geometry.ts";
export type { Dimension, Position, Position2D, Position3D } from "./geometry/Geometry.ts";
export { Point as PointGeometry } from "./geometry/Point.ts";
export { Polyline as PolylineGeometry } from "./geometry/Polyline.ts";
export { Polygon as PolygonGeometry } from "./geometry/Polygon.ts";
export { Circle as CircleGeometry } from "./geometry/Circle.ts";
export { Ellipse as EllipseGeometry } from "./geometry/Ellipse.ts";

// Style
export { Style } from "./style/Style.ts";
export { StrokeStyle, StrokePattern } from "./style/StrokeStyle.ts";
export type { StrokeStyleProps } from "./style/StrokeStyle.ts";
export { ShapeStyle } from "./style/ShapeStyle.ts";
export type { ShapeStyleProps } from "./style/ShapeStyle.ts";
export { TextBoxStyle, DefaultTextBoxStyle, MAX_TEXT_LENGTH } from "./style/TextBoxStyle.ts";
export type { TextBoxStyleProps } from "./style/TextBoxStyle.ts";
export { PointStyle } from "./style/PointStyle.ts";
export type { PointStyleProps } from "./style/PointStyle.ts";
export { IconStyle } from "./style/IconStyle.ts";
export type { IconStyleProps } from "./style/IconStyle.ts";
export { MediaIconStyle } from "./style/MediaIconStyle.ts";
export type { MediaIconStyleProps } from "./style/MediaIconStyle.ts";
export { ArrowStyle } from "./style/ArrowStyle.ts";
export type { ArrowStyleProps } from "./style/ArrowStyle.ts";

// Types (TypeDef namespaces)
export type { TypeDef, TypeDefProps, ElementMeta, ElementOf } from "./types/TypeDef.ts";
export { Arrow } from "./types/Arrow.ts";
export type { ArrowElement } from "./types/Arrow.ts";
export { Graphic } from "./types/Graphic.ts";
export type { GraphicElement } from "./types/Graphic.ts";
export { Angle } from "./types/Angle.ts";
export type { AngleElement } from "./types/Angle.ts";
export { Line } from "./types/Line.ts";
export type { LineElement } from "./types/Line.ts";
export { Rectangle } from "./types/Rectangle.ts";
export type { RectangleElement } from "./types/Rectangle.ts";
export { TextBox } from "./types/TextBox.ts";
export type { TextBoxElement } from "./types/TextBox.ts";
export { Symbol } from "./types/Symbol.ts";
export type { SymbolElement } from "./types/Symbol.ts";
export { MediaIcon } from "./types/MediaIcon.ts";
export type { MediaIconElement, MediaIconProps } from "./types/MediaIcon.ts";
export { Point } from "./types/Point.ts";
export type { PointElement } from "./types/Point.ts";
export { Circle } from "./types/Circle.ts";
export type { CircleElement } from "./types/Circle.ts";
export { Ellipse } from "./types/Ellipse.ts";
export type { EllipseElement } from "./types/Ellipse.ts";
export { Polygon } from "./types/Polygon.ts";
export type { PolygonElement } from "./types/Polygon.ts";

// Event types
export * from "./event.ts";

// Local persistence
export { elementFromFeature } from "./elementFromFeature.ts";

// Random generators
export {
    randomPointStyle,
    randomStrokeStyle,
    randomShapeStyle,
    randomArrowStyle,
    randomSymbolStyle,
    randomPoint,
    randomLine,
    randomCircle,
    randomEllipse,
    randomAngle,
    randomArrow,
    randomPolygon,
    randomRectangle,
    randomSymbol,
} from "./random.ts";
