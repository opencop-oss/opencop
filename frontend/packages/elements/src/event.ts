// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Feature, Geometry } from "geojson";

/** Wire-format stroke style name for OC events. */
export type OCStrokeStyle = "solid" | "outlined" | "dashed" | "dotted";

/** Wire-format shape data common to filled element types. */
export interface ShapeData {
    /** The interior fill color. */
    fillColor: string;
    /** The outline stroke color. */
    strokeColor: string;
    /** The outline weight in pixels. */
    strokeWeight: number;
    /** The outline dash pattern name. */
    strokeStyle: OCStrokeStyle;
    /** The area in square meters (optional, may be string for display). */
    area?: number | string;
}

/** Base wire-format element with a type discriminator. */
export interface OCElement {
    /** The element type key. */
    type: string;
    speed?: number | null;
    course?: number | null;
    heading?: number | null;
}

/** Wire-format line data common to stroked element types. */
export interface OCLineData {
    /** The stroke weight in pixels. */
    strokeWeight: number;
    /** The stroke color. */
    strokeColor: string;
    /** The stroke dash pattern name. */
    strokeStyle: OCStrokeStyle;
}

/** Wire-format arrow element. */
export interface OCArrowElement extends OCElement, OCLineData {
    type: "arrow";
    /** The range in kilometers. */
    range: number;
    /** The bearing in degrees from north. */
    bearing: number;
}

/** Wire-format line element. */
export interface OCLineElement extends OCElement, OCLineData {
    type: "line";
}

/** Wire-format point element. */
export interface OCPointElement extends OCElement {
    type: "point";
    /** The fill color. */
    fillColor: string;
}

/** Wire-format military symbol element. */
export interface OCSymbolElement extends OCElement {
    type: "symbol";
    /** The MIL-STD-2525 SIDC code. */
    symbol: string;
}

/** Wire-format media icon: a point drawn as an imported image. */
export interface OCMediaIconElement extends OCElement {
    type: "media-icon";
    /** The image's location — an `opencop:url:` reference, an `http(s)://` or `data:` URL, or a relative path. */
    href: string;
}

/** Wire-format ellipse element. */
export interface OCEllipseElement extends OCElement, ShapeData {
    type: "ellipse";
    /** The bearing in degrees from north. */
    bearing: number;
    /** The semi-major axis length in meters. */
    major: number;
    /** The semi-minor axis length in meters. */
    minor: number;
}

/** Wire-format polygon element. */
export interface OCPolygonElement extends OCElement, ShapeData {
    type: "polygon";
    /** The centroid [lng, lat]. */
    center: [number, number];
}

/** Wire-format rectangle element. */
export interface OCRectangleElement extends OCElement, ShapeData {
    type: "rectangle";
    /** The centroid [lng, lat]. */
    center: [number, number];
}

/** Wire-format angle element. */
export interface OCAngleElement extends OCElement, OCLineData {
    type: "angle";
}

/** Wire-format circle element. */
export interface OCCircleElement extends OCElement, ShapeData {
    type: "circle";
    /** The radius in meters. */
    radius: number;
}

/** Discriminated union of all wire-format element types. */
export type OCBaseElement =
    | OCArrowElement
    | OCLineElement
    | OCPointElement
    | OCSymbolElement
    | OCMediaIconElement
    | OCEllipseElement
    | OCPolygonElement
    | OCRectangleElement
    | OCAngleElement
    | OCCircleElement;

/** A full OpenCOP event payload. */
export interface OCEvent {
    /** Key-value attributes (e.g. SIDC metadata). */
    readonly attributes: {
        readonly [key: string]: string | number;
    };
    /** Free-text remarks. */
    readonly remarks?: string;
    /** Protocol version. */
    readonly version: string;
    /** Human-readable display name. */
    readonly displayName: string;
    /** Shared object id across versions. */
    readonly objectId: string;
    /** The event kind discriminator. */
    readonly kind: string;
    /** The source that created this event. */
    source: string;
    /** Protocol extensions enabled on this event. */
    readonly extensions: string[];
    /** The element data payload. */
    element: OCBaseElement;
    /** Contact information entries. */
    readonly contacts: {
        [key: string]: string;
    }[];
    /** Timestamps for event lifecycle. */
    readonly timestampz: {
        /** When the event was observed. */
        time?: number;
        /** When the event started. */
        start?: number;
        /** When the event becomes stale. */
        stale?: number;
    };
}

/** A GeoJSON Feature carrying a full {@link OCEvent} payload. */
export type OCFeature = Feature<Geometry, { opencop: OCEvent }>;

/**
 * A GeoJSON Feature carrying a partial {@link OCEvent} payload.
 * Used for locally-created elements that may not have all event fields.
 */
export type PartialOCFeature = Feature<
    Geometry,
    {
        opencop: Partial<OCEvent> & {
            objectId: string;
            source: string;
            id: string;
            displayName: string;
            element: OCBaseElement;
            timestampz: {
                time?: number | null;
                start?: number | null;
                stale?: number | null;
            };
        };
    }
>;
