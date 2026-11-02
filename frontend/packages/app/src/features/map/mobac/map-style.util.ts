// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { StyleSpecification } from "maplibre-gl";
import type { CustomMapSource } from "./custom-map-source";

export function generateMapStyle(custom: CustomMapSource): StyleSpecification {
    return {
        version: 8,
        sources: {
            [custom.getName()]: {
                type: "raster",
                tiles: [custom.getUrl()],
                tileSize: 256,
                minzoom: custom.getMinZoom(),
                maxzoom: custom.getMaxZoom(),
            },
        },
        layers: [
            {
                id: custom.getName(),
                type: "raster",
                source: custom.getName(),
            },
        ],
    };
}
