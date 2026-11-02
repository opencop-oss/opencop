// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { chunk } from "lodash";
import {
    hexaToRgba,
    interleave,
    label,
    path,
    point,
    polygon,
    type Primitive,
} from "@opencop/scene";
import { ringSelfIntersection } from "./polygon.model";
import { useWatchboxVisibilityStore } from "./watchbox-visibility.store";
import type { WatchboxObject } from "./watchbox.store";

export const WATCHBOX_ENABLED_DASH: [number, number] = [10, 10];
export const WATCHBOX_DISABLED_DASH: [number, number] = [2, 6];
export const WATCHBOX_ENABLED_FILL_COLOR = hexaToRgba("#ffa2003d");
export const WATCHBOX_ENABLED_STROKE_COLOR = hexaToRgba("#ffa200");
export const WATCHBOX_DISABLED_FILL_COLOR = hexaToRgba("#7180963d");
export const WATCHBOX_DISABLED_STROKE_COLOR = hexaToRgba("#718096");
export const WATCHBOX_STROKE_WIDTH = 4;
const KINK_FILL_COLOR = hexaToRgba("#ff0000dc");
const KINK_STROKE_COLOR = hexaToRgba("#ffffffdc");
const KINK_STROKE_WIDTH = 2;
const KINK_RADIUS = 8;

/** A watchbox being drawn has no saved state yet, so it draws as enabled. */
function isEnabled(object: WatchboxObject): boolean {
    return object.watchbox?.enabled ?? true;
}

/** Every watchbox is drawn, except disabled ones while the hide-disabled setting is on. */
export function isWatchboxVisible(object: WatchboxObject): boolean {
    return !useWatchboxVisibilityStore.getState().hideDisabled || isEnabled(object);
}

/**
 * How a watchbox looks: a dashed polygon, orange while enabled and grey while
 * disabled, labelled with its name when `labelled`. The same look draws the saved
 * watchboxes and the one being drawn, so a new watchbox looks the same before and
 * after it is saved. A draft whose outline crosses itself also shows a red marker at
 * the crossing.
 */
export function watchboxPrimitives(object: WatchboxObject, labelled: boolean): Primitive[] {
    const ring = closed(object.polygon);
    if (ring.length < 4) return [];
    const enabled = isEnabled(object);
    const positions = interleave(ring);

    const primitives: Primitive[] = [
        path({
            id: "outline",
            geometry: { positions },
            style: {
                strokeColor: enabled
                    ? WATCHBOX_ENABLED_STROKE_COLOR
                    : WATCHBOX_DISABLED_STROKE_COLOR,
                strokeWidth: WATCHBOX_STROKE_WIDTH,
                strokeDash: enabled ? WATCHBOX_ENABLED_DASH : WATCHBOX_DISABLED_DASH,
            },
        }),
    ];
    // A fill needs a third vertex (four positions with the closing one).
    if (ring.length >= 8)
        primitives.push(
            polygon({
                id: "fill",
                geometry: { positions },
                style: {
                    fillColor: enabled ? WATCHBOX_ENABLED_FILL_COLOR : WATCHBOX_DISABLED_FILL_COLOR,
                },
            }),
        );
    // Only drafts are checked: a saved watchbox is drawn as stored, even a legacy one that crosses itself.
    const kink = object.watchbox ? null : ringSelfIntersection(chunk(ring, 2));
    if (kink)
        primitives.push(
            point({
                id: "self-intersection",
                geometry: { positions: [kink[0], kink[1], 0] },
                style: {
                    fillColor: KINK_FILL_COLOR,
                    strokeColor: KINK_STROKE_COLOR,
                    strokeWidth: KINK_STROKE_WIDTH,
                    radius: KINK_RADIUS,
                },
            }),
        );
    if (labelled && object.watchbox?.name)
        primitives.push(
            label({
                id: "label",
                geometry: { positions: [...centroid(ring), 0] },
                style: { text: object.watchbox.name },
            }),
        );
    return primitives;
}

/** The ring with its first position repeated at the end, if it isn't already. */
function closed(ring: readonly number[]): number[] {
    const n = ring.length;
    if (n < 2 || (ring[0] === ring[n - 2] && ring[1] === ring[n - 1])) return [...ring];
    return [...ring, ring[0], ring[1]];
}

/** The mean of a closed ring's distinct vertices. */
function centroid(ring: readonly number[]): [number, number] {
    const count = ring.length / 2 - 1;
    let lng = 0;
    let lat = 0;
    for (let i = 0; i < count; i++) {
        lng += ring[2 * i];
        lat += ring[2 * i + 1];
    }
    return [lng / count, lat / count];
}
