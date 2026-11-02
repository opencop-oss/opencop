// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { LngLat, MercatorCoordinate } from "./maplibre-gl-mock";

describe("MercatorCoordinate.fromLngLat", () => {
    const cases: Array<[string, Parameters<typeof MercatorCoordinate.fromLngLat>[0]]> = [
        ["tuple", [12, 34]],
        ["LngLat", new LngLat(12, 34)],
        ["plain object", { lng: 12, lat: 34 }],
    ];

    it.each(cases)("accepts a %s", (_name, lngLat) => {
        const coordinate = MercatorCoordinate.fromLngLat(lngLat, 56);

        expect([coordinate.x, coordinate.y, coordinate.toAltitude()]).toEqual([12, 34, 56]);
    });
});
