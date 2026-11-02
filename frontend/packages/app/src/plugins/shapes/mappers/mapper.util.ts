// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Vector2 } from "@math.gl/core";
import { label, type LabelPrimitiveProps, type TextPrimitive } from "@opencop/scene";
import * as turf from "@turf/turf";

/** The element's name label, or none for an unnamed element. */
export const nameLabel = (props: LabelPrimitiveProps): TextPrimitive[] =>
    props.style.text ? [label(props)] : [];

export const vector = (lng: number, lat: number): Vector2 => {
    const m = turf.toMercator([lng, lat]);
    return new Vector2(m[0], m[1]);
};

export function angleBetween(
    o: readonly [lng: number, lat: number],
    v0: readonly [lng: number, lat: number],
    v1: readonly [lng: number, lat: number],
    orientation: "ccw" | "cw",
): number {
    const b0 = turf.bearing([...o], [...v0]);
    const b1 = turf.bearing([...o], [...v1]);

    // In range (-360, 360).
    const angle = (b1 - b0 + 360) % 360;
    return orientation === "cw" ? angle : (360 - angle) % 360;
}
