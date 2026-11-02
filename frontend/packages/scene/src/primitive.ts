// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { IconLoader } from "./batches/icon/IconAtlas";

/** RGBA color with each component in the range 0–255. */
export type Color = readonly [r: number, g: number, b: number, a: number];

/** Polygon geometry defined by a flattened list of 3D positions. */
export type PolygonGeometry = {
    type: "polygon";
    /**
     * Flattened [longitude, latitude, altitude] positions.
     *
     * @example [lng0, lat0, alt0, lng1, lat1, alt1, ...]
     */
    positions: number[];
};

/** Path geometry defined by a flattened list of 3D positions. */
export type PathGeometry = {
    type: "path";
    /**
     * Flattened [longitude, latitude, altitude] positions.
     *
     * @example [lng0, lat0, alt0, lng1, lat1, alt1, ...]
     */
    positions: number[];
};

/** Point geometry defined by a longitude and latitude. */
export type PointGeometry = {
    type: "point";
    /** [longitude, latitude, alt] position. */
    positions: [lng: number, lat: number, alt: number];
};

/** Geometry supported by a {@link Primitive}. */
export type PrimitiveGeometry = PolygonGeometry | PathGeometry | PointGeometry;

/** Visual properties for a polygon. */
export type PolygonStyle = {
    type: "polygon";
    fillColor: Color;
    /**
     * Image tiled across the fill at a constant screen size. Its alpha masks the
     * fill; `fillColor` tints it.
     */
    pattern?: FillPattern;
};

/** A fill pattern image. */
export type FillPattern = {
    /** Identifier for the pattern image. */
    id: string;
    /** URL of the pattern image or an {@link IconLoader}. */
    source: string | IconLoader;
    /**
     * Meters each pattern pixel covers on the ground. Default 1. To keep a
     * constant screen size, match the view's meters per pixel.
     */
    scale?: number;
};

/** Visual properties for a path. */
export type PathStyle = {
    type: "path";
    strokeColor: Color;
    /** Stroke width in pixels. */
    strokeWidth: number;
    /** Dash pattern as [dash size, gap size], in pixels. */
    strokeDash?: [dashSize: number, gapSize: number];
};

/** Visual properties for a point. */
export type PointStyle = {
    type: "point";
    fillColor: Color;
    strokeColor: Color;
    /** Stroke width in pixels. */
    strokeWidth: number;
    /** Point radius in pixels. */
    radius: number;
};

/** Visual properties for an icon. */
export type IconStyle = {
    type: "icon";
    /** Identifier for the icon resource. */
    id: string;
    /** URL of the icon image or an {@link IconLoader}. */
    source: string | IconLoader;
    /** Icon width in pixels. */
    width: number;
    /** Icon height in pixels. */
    height: number;
    /** Rotation angle in degrees. */
    angle?: number;
    /** Optional icon size multiplier. */
    size?: number;
    /** Optional tint color. */
    color?: Color;
    billboard: boolean;
    /** Optional offset in pixels. */
    offset?: [x: number, y: number];
    mask?: boolean;
};

export type TextStyle = {
    type: "text";
    text: string;
    size: number;
    color: Color;
    billboard?: boolean;
    angle?: number;
    offset?: [x: number, y: number];
    anchor?: "start" | "middle" | "end";
    baseline?: "top" | "center" | "bottom";
    /**
     * Whether this text may be decluttered away by the collision pass. Default
     * true, as a label should be.
     *
     * The pass tests one point — the text's anchor — so a block of text is kept
     * or dropped whole by whatever owns that single texel. That is reasonable
     * for a one-line label pinned to a point, and wrong for text that *is* an
     * object: set false and it always draws. It still occludes labels beneath
     * it either way.
     */
    collides?: boolean;
};

export type MarchingAntsStyle = {
    type: "marching-ants";
};

export type AngleStyle = {
    type: "angle";
    strokeColor: Color;
    strokeWidth: number;
    radius: number;
    angles: [start: number, end: number];
};

/** Style supported by a {@link Primitive}. */
export type PrimitiveStyle =
    | PolygonStyle
    | PathStyle
    | PointStyle
    | IconStyle
    | TextStyle
    | MarchingAntsStyle
    | AngleStyle;

/**
 * A renderable primitive consisting of geometry and its visual style.
 *
 * The geometry and style types can be narrowed independently through the
 * generic parameters.
 */
export type Primitive<
    G extends PrimitiveGeometry = PrimitiveGeometry,
    S extends PrimitiveStyle = PrimitiveStyle,
> = {
    id: string;
    geometry: G;
    style: S;

    /** One value per data filter channel of the primitive's batch. */
    filter?: number[];
    /** Whether the primitive is drawn with a screen-space outline. */
    outline?: boolean;
};

export type PartialPrimitiveGeometry = Partial<PrimitiveGeometry> & {
    type: PrimitiveGeometry["type"];
};
export type PartialPrimitiveStyle = Partial<PrimitiveStyle> & { type: PrimitiveStyle["type"] };

export type PartialPrimitive<
    G extends PartialPrimitiveGeometry = PartialPrimitiveGeometry,
    S extends PartialPrimitiveStyle = PartialPrimitiveStyle,
> = {
    id: string;
    geometry?: G;
    style?: S;
    filter?: number[];
    outline?: boolean;
};

export type PartialGeometryOf<P extends Primitive> = Partial<P["geometry"]> & {
    type: P["geometry"]["type"];
};
export type PartialStyleOf<P extends Primitive> = Partial<P["style"]> & {
    type: P["style"]["type"];
};
export type PartialPrimitiveOf<P extends Primitive> = {
    id: string;
    geometry?: PartialGeometryOf<P>;
    style?: PartialStyleOf<P>;
    filter?: number[];
    outline?: boolean;
};
