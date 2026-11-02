// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { MercatorCoordinate } from "maplibre-gl";
import { Vector2 } from "@math.gl/core";

export function getMercatorVector([lng, lat]: [number, number]): Vector2 {
    const merc = MercatorCoordinate.fromLngLat([lng, lat]);
    return new Vector2(merc.x, merc.y);
}

export function getLngLat([x, y]: [number, number]): [lng: number, lat: number] {
    return new MercatorCoordinate(x, y).toLngLat().toArray();
}
