// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import * as turf from "@turf/turf";
import { MercatorVector } from "@opencop/tools";

/** How much of the box the text may fill, leaving a margin inside the border. */
export const INSET = 0.9;

type Corner = [lng: number, lat: number];

function cornersOf(positions: readonly number[]): Corner[] | null {
    if (positions.length < 8) return null;
    return [
        [positions[0], positions[1]],
        [positions[2], positions[3]],
        [positions[4], positions[5]],
        [positions[6], positions[7]],
    ];
}

/**
 * The rectangle's own width and height in metres, measured along its edges
 * rather than along north and east — the frame the text is laid out in, since
 * the text turns with the box.
 *
 * The draw grammar lays the first edge down first, so corners 0 and 1 are
 * always that edge and 3 and 2 their opposites across the box.
 */
export function boxFrame(positions: readonly number[]): { width: number; height: number } | null {
    const corners = cornersOf(positions);
    if (!corners) return null;

    const width = turf.distance(corners[0], corners[1], { units: "meters" });
    const height = turf.distance(corners[1], corners[2], { units: "meters" });
    if (width <= 0 || height <= 0) return null;

    return { width, height };
}

/**
 * The rectangle grown away from its first edge so its own height is at least
 * `required` metres. The width is left alone — it is the operator's, and the
 * only dimension the text does not move.
 *
 * Only ever grows: the operator's drag sets a floor that more text can push
 * past but never pulls back.
 *
 * Moved in mercator space, not by ground distance. deck draws the box as
 * straight mercator edges, and a degree of latitude covers less mercator the
 * further from the equator it sits — so growing by metres would shear the
 * rectangle into a trapezoid everywhere but the equator.
 */
export function grownToHeight(positions: readonly number[], required: number): number[] | null {
    const corners = cornersOf(positions);
    const frame = boxFrame(positions);
    if (!corners || !frame || required <= frame.height) return null;

    const scale = required / frame.height;
    // Corner 3 sits opposite corner 0, and corner 2 opposite corner 1.
    const [near0, near1, far1, far0] = corners.map((corner) => MercatorVector.fromLngLat(corner));
    const pushed = (near: MercatorVector, far: MercatorVector) =>
        near.clone().add(far.clone().subtract(near).scale(scale)) as MercatorVector;

    return [
        ...corners[0],
        ...corners[1],
        ...pushed(near1, far1).toLngLat().slice(0, 2),
        ...pushed(near0, far0).toLngLat().slice(0, 2),
    ];
}
