// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { RasterTileConfig, WeatherFeatureEntry } from "@opencop/api";
import { buildMapTileConfigPatch } from "./tile-config.util";

const rasterConfig: RasterTileConfig = {
    kind: "RASTER",
    tileSize: 256,
    minZoom: null,
    maxZoom: null,
};

describe("buildMapTileConfigPatch", () => {
    it("omits the weather extension when features are undefined", () => {
        expect(buildMapTileConfigPatch("WEATHER", rasterConfig, undefined)).toEqual({
            type: "WEATHER",
            tile: rasterConfig,
        });
    });

    it("includes the weather extension when features are provided", () => {
        const features: WeatherFeatureEntry[] = [
            { feature: "PRECIPITATION", url: "https://example.com/precip" },
        ];

        expect(buildMapTileConfigPatch("WEATHER", rasterConfig, features)).toEqual({
            type: "WEATHER",
            tile: rasterConfig,
            extension: {
                extensionType: "WEATHER",
                features,
            },
        });
    });
});
