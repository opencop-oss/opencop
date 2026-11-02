// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ElementMapper, MapperSelector, MapperState } from "@opencop/contributions";
import type { TextBoxElement } from "@opencop/elements";
import {
    FONT_FAMILY,
    hexaToRgba,
    interleave,
    path,
    polygon,
    text,
    TEXT_SIZE,
} from "@opencop/scene";
import type { Primitive } from "@opencop/scene";
import { MercatorVector } from "@opencop/tools";
import { radiansToDegrees } from "@turf/turf";
import { boxFrame } from "./box-geometry.util";
import { wrapToBox, type WrappedText } from "./box-text.util";
import { TEXTBOX_TYPE } from "./textbox-tools";

/** Earth's circumference at the equator, metres. */
const EARTH_CIRCUMFERENCE_METERS = 40_075_016.686;

/**
 * Web Mercator tile size deck.gl measures zoom against: the world spans
 * `TILE_SIZE * 2 ** zoom` pixels. Use deck.gl's 512-pixel tile size.
 */
const TILE_SIZE = 512;

/** The latitude Web Mercator is defined up to, where the projection is square. */
const MAX_LATITUDE = 85.051129;

/** Entries kept before the wrap cache drops its oldest. */
const WRAP_CACHE_LIMIT = 500;

const wrapCache = new Map<string, WrappedText>();

/**
 * The box's content, broken to its width.
 *
 * Measured in metres against the box's own metre width, so the result is
 * zoom-invariant: it changes when the box is resized or the text edited, never
 * as the camera moves. Memoised because the mapper re-runs on every zoom
 * change and the measuring is per character.
 */
function wrappedFor(element: TextBoxElement): WrappedText {
    const style = element.getStyle();
    const frame = boxFrame(element.geometry.getAllPos());
    if (!frame) return { text: style.getText(), height: 0 };

    const key = `${element.key}|${style.getTextSize()}|${frame.width.toFixed(1)}|${style.getText()}`;

    const hit = wrapCache.get(key);
    if (hit) return hit;

    const result = wrapToBox(style.getText(), frame.width, style.getTextSize(), FONT_FAMILY);
    // Bounded, not LRU: `Map` iterates in insertion order, so this drops the
    // oldest entry rather than the least used. A view holding more boxes than
    // the limit therefore evicts each entry before the next pass reads it and
    // gets no hits at all — the bound is there to cap memory, and the memo pays
    // for itself at ordinary box counts.
    if (wrapCache.size >= WRAP_CACHE_LIMIT) {
        const [oldest] = wrapCache.keys();
        wrapCache.delete(oldest);
    }
    wrapCache.set(key, result);
    return result;
}

/**
 * The rotation that lays the content along the box's first edge, so the text
 * turns with the box.
 *
 * Measured in mercator space rather than from a compass bearing: the box is
 * drawn as straight mercator edges, and over a box this size a great-circle
 * bearing diverges from the edge actually on screen. Mercator's y axis runs
 * down where the shader's runs up, which measuring off the vertical
 * (`atan2(x, y)`) accounts for.
 */
export function textAngle(positions: readonly number[]): number {
    if (positions.length < 8) return 0;

    const edge = MercatorVector.fromLngLat([positions[2], positions[3]]).subtract(
        MercatorVector.fromLngLat([positions[0], positions[1]]),
    );
    if (edge.magnitude() === 0) return 0;

    // `Vector2.verticalAngle()`, which a 3-component MercatorVector has no equivalent of.
    const along = radiansToDegrees(Math.atan2(edge.x, edge.y)) - 90;
    // Folded into (-180, 180]; a box turned half a turn reads upside down on purpose.
    const wrapped = ((along % 360) + 360) % 360;
    return wrapped > 180 ? wrapped - 360 : wrapped;
}

/**
 * Pixels one metre covers at `zoom`, on the parallel through `latitude`.
 *
 * The scene sizes text in pixels, so a size the operator set in metres has to
 * be converted for every viewport it is drawn in. The box itself is geographic
 * and scales on its own, so converting the text the same way is what keeps the
 * two the same size relative to each other at every zoom.
 */
export function pixelsPerMeter(zoom: number, latitude: number): number {
    // Clamped to Web Mercator's own limit. `Math.cos` of 90° is 6e-17 rather
    // than zero, so a guard on the cosine never fires and a box at the pole
    // would be handed a text size of around 1e14 pixels.
    const clamped = Math.min(Math.max(latitude, -MAX_LATITUDE), MAX_LATITUDE);
    const cos = Math.cos((clamped * Math.PI) / 180);
    return (TILE_SIZE * 2 ** zoom) / (EARTH_CIRCUMFERENCE_METERS * cos);
}

/**
 * The scale the scene applies to every text primitive, from the operator's
 * label size. Guarded against a zero or missing setting, which would otherwise
 * divide the content away entirely.
 *
 * Dividing this back out is a workaround for the scale being a property of the
 * whole text batch rather than of a primitive — see `MapperState.text`. If the
 * scene gains a per-primitive opt-out, this and the selector's
 * dependency on the label size both go.
 */
function labelScale(labelSize: number): number {
    const scale = labelSize / TEXT_SIZE;
    return Number.isFinite(scale) && scale > 0 ? scale : 1;
}

/** The mean of the box's corners, which for a rectangle is its centre. */
function centre(positions: readonly number[]): [lng: number, lat: number] {
    let lng = 0;
    let lat = 0;
    for (let i = 0; i < 8; i += 2) {
        lng += positions[i];
        lat += positions[i + 1];
    }
    return [lng / 4, lat / 4];
}

/** What the content's pixel size is derived from. */
export type TextBoxMapperState = {
    readonly zoom: number;
    /** The operator's label size, which the scene scales every text primitive by. */
    readonly labelSize: number;
};

/**
 * Deliberately undebounced, unlike the tactical-graphic mapper's 200 ms.
 *
 * The content's pixel size is its metre size times `pixelsPerMeter(zoom)`, and
 * the box it sits in is geographic, so the two only stay the same size relative
 * to each other while they are recomputed together. A delay would leave the
 * text at the previous zoom's size for the length of it — visibly adrift from
 * its own box during a scroll-zoom, which is the thing the metre sizing exists
 * to avoid. Re-mapping is a wrap read from cache plus three primitives.
 */
class TextBoxSelector implements MapperSelector<TextBoxMapperState> {
    select(state: MapperState): TextBoxMapperState {
        return { zoom: state.view.zoom, labelSize: state.text.size };
    }

    equalityFn(next: TextBoxMapperState, prev: TextBoxMapperState): boolean {
        return next.zoom === prev.zoom && next.labelSize === prev.labelSize;
    }
}

/**
 * Draws a textbox: its fill, its outline, and the content laid flat inside it.
 *
 * The content is the object rather than a label about it, so this mapper
 * declares no `textSetting` — the operator's per-type text toggles in Map
 * Settings do not reach it, and turning map labels off leaves a textbox
 * readable.
 */
export class TextBoxElementMapper implements ElementMapper<TextBoxElement, TextBoxMapperState> {
    public readonly type = TEXTBOX_TYPE;
    public readonly selector = new TextBoxSelector();
    /** The box's text is the box's content; hiding it would hide the element. */
    public readonly textIsContent = true;

    public map(element: TextBoxElement, { zoom, labelSize }: TextBoxMapperState): Primitive[] {
        const ring = element.geometry.getAllPos();
        const style = element.getStyle();
        // Closed for the outline: a path over the four corners alone draws three
        // sides and leaves the fourth open. The fill is unaffected, so the gap
        // reads as one border edge missing rather than as a broken box.
        const positions = interleave(element.geometry.getClosed());

        const primitives: Primitive[] = [
            path({
                id: "path",
                geometry: { positions },
                style: {
                    strokeColor: hexaToRgba(style.getStrokeColor()),
                    strokeWidth: style.getStrokeWeight(),
                    strokeDash: style.getStrokePattern().dashArray,
                },
            }),
        ];

        // Fewer than four corners is a degenerate ring with no interior to fill.
        if (positions.length / 3 > 3) {
            primitives.push(
                polygon({
                    id: "polygon",
                    geometry: { positions },
                    style: { fillColor: hexaToRgba(style.getFillColor()) },
                }),
            );
        }

        const content = style.isTextVisible() ? wrappedFor(element).text : "";
        if (content && ring.length >= 8) {
            const [lng, lat] = centre(ring);
            primitives.push(
                text({
                    id: "text",
                    geometry: { positions: [lng, lat, 0] },
                    style: {
                        text: content,
                        // Divided by the scene's global label scale: the content
                        // is the object, so it holds the size the box was broken
                        // to rather than following the operator's label slider.
                        size:
                            (style.getTextSize() * pixelsPerMeter(zoom, lat)) /
                            labelScale(labelSize),
                        color: hexaToRgba(style.getTextColor()),
                        // Lies flat and turns with the box, rather than facing the camera.
                        billboard: false,
                        angle: textAngle(ring),
                        // The content is the object: a map label happening to sit
                        // under the box's centre must not declutter it away.
                        collides: false,
                    },
                }),
            );
        }

        return primitives;
    }
}
