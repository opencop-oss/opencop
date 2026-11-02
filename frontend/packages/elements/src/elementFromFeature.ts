// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Element } from "./Element.ts";
import type { PartialOCFeature } from "./event.ts";
import { Circle as CircleGeometry } from "./geometry/Circle.ts";
import type { Position } from "./geometry/Geometry.ts";
import { Point as PointGeometry } from "./geometry/Point.ts";
import { Polygon as PolygonGeometry } from "./geometry/Polygon.ts";
import { Polyline } from "./geometry/Polyline.ts";
import { Ellipse as EllipseGeometry } from "./geometry/Ellipse.ts";
import { BaseStyle } from "./style/BaseStyle.ts";
import { ShapeStyle } from "./style/ShapeStyle.ts";
import { TextBoxStyle } from "./style/TextBoxStyle.ts";
import { StrokeStyle } from "./style/StrokeStyle.ts";
import { ArrowStyle } from "./style/ArrowStyle.ts";
import { PointStyle } from "./style/PointStyle.ts";
import { IconStyle } from "./style/IconStyle.ts";
import { MediaIconStyle } from "./style/MediaIconStyle.ts";

type Props = Record<string, unknown>;

function flatCoords(feature: PartialOCFeature): number[] {
    const geo = feature.geometry;
    if (geo.type === "Point") return geo.coordinates as number[];
    if (geo.type === "LineString") return (geo.coordinates as number[][]).flat();
    if (geo.type === "Polygon") return (geo.coordinates as number[][][])[0].flat();
    return [];
}

function modifiers(props: Props): Record<string, string> {
    return Object.fromEntries(
        Object.entries(props)
            .filter(([key]) => key.startsWith("modifier:"))
            .map(([key, val]) => [key.slice(9), val as string]),
    );
}

/**
 * Reconstruct an {@link Element} from a {@link PartialOCFeature} produced by
 * {@link Element.toFeature}. Returns `undefined` for unknown element types.
 */
export function elementFromFeature(feature: PartialOCFeature): Element | undefined {
    const { opencop } = feature.properties;
    const el = opencop.element as unknown as Props;
    const type = el["type"] as string;

    const meta = {
        id: opencop.id,
        objectId: opencop.objectId,
        displayName: opencop.displayName,
        source: opencop.source,
        start: opencop.timestampz.start ?? undefined,
        time: opencop.timestampz.time ?? undefined,
        stale: opencop.timestampz.stale,
        attributes: opencop.attributes as Record<string, unknown>,
        speed: el["speed"] as number | null | undefined,
        course: el["course"] as number | null | undefined,
        heading: el["heading"] as number | null | undefined,
        hae: el["hae"] as number | undefined,
    };

    const coords = flatCoords(feature);
    const point = coords as Position;

    switch (type) {
        case "symbol": {
            const sidc = el["symbol"] as string | undefined;
            return Element.create({
                ...meta,
                type,
                code: sidc,
                geometry: PointGeometry.fromProperties(point),
                // No `src`: the icon is resolved from `code` by whoever renders
                // this element, not baked in at decode time.
                style: IconStyle.fromProperties(el),
            });
        }

        case "media-icon":
            return Element.create({
                ...meta,
                type,
                geometry: PointGeometry.fromProperties(point),
                style: MediaIconStyle.fromProperties(el),
            });

        case "point":
            return Element.create({
                ...meta,
                type,
                geometry: PointGeometry.fromProperties(point),
                style: PointStyle.fromProperties(el),
            });

        case "circle":
            return Element.create({
                ...meta,
                type,
                geometry: CircleGeometry.fromProperties(point, el),
                style: ShapeStyle.fromProperties(el),
            });

        case "ellipse":
            return Element.create({
                ...meta,
                type,
                geometry: EllipseGeometry.fromProperties(point, el),
                style: ShapeStyle.fromProperties(el),
            });

        case "line":
            return Element.create({
                ...meta,
                type,
                geometry: Polyline.fromProperties(coords),
                style: StrokeStyle.fromProperties(el),
            });

        case "polygon":
            return Element.create({
                ...meta,
                type,
                geometry: PolygonGeometry.fromProperties(coords),
                style: ShapeStyle.fromProperties(el),
            });

        case "rectangle":
            return Element.create({
                ...meta,
                type,
                geometry: PolygonGeometry.fromProperties(coords),
                style: ShapeStyle.fromProperties(el),
            });

        case "textbox":
            return Element.create({
                ...meta,
                type,
                geometry: PolygonGeometry.fromProperties(coords),
                style: TextBoxStyle.fromProperties(el),
            });

        case "arrow":
            return Element.create({
                ...meta,
                type,
                geometry: Polyline.fromProperties(coords),
                style: ArrowStyle.fromProperties(el),
            });

        case "angle":
            return Element.create({
                ...meta,
                type,
                geometry: Polyline.fromProperties(coords),
                style: StrokeStyle.fromProperties(el),
            });

        case "graphic":
            return Element.create({
                ...meta,
                type,
                code: el["symbol"] as string,
                modifiers: modifiers(el),
                geometry: Polyline.fromProperties(coords),
                style: BaseStyle.fromProperties(el),
            });

        default:
            return undefined;
    }
}
