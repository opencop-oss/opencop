// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { CustomMapSource } from "./custom-map-source";
import { generateMapStyle } from "./map-style.util";

describe("generateMapStyle", () => {
    it("emits only the configured raster source and layer", () => {
        const custom = CustomMapSource.fromXML(`
            <customMapSource>
                <name>Local Raster</name>
                <url>https://tiles.example.invalid/{$z}/{$x}/{$y}.png</url>
                <tileType>png</tileType>
                <tileUpdate>None</tileUpdate>
                <minZoom>2</minZoom>
                <maxZoom>12</maxZoom>
            </customMapSource>
        `);

        expect(custom).toBeDefined();
        expect(generateMapStyle(custom!)).toEqual({
            version: 8,
            sources: {
                "Local Raster": {
                    type: "raster",
                    tiles: ["https://tiles.example.invalid/{z}/{x}/{y}.png"],
                    tileSize: 256,
                    minzoom: 2,
                    maxzoom: 12,
                },
            },
            layers: [
                {
                    id: "Local Raster",
                    type: "raster",
                    source: "Local Raster",
                },
            ],
        });
    });
});
