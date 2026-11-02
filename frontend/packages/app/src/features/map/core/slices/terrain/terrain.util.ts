// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { TerrainMeshTileConfig } from "@opencop/api";

/**
 * Resolve the DeckGL elevation decoder object from a TerrainMeshTileConfig.
 * If the BE provided an explicit `decoderConfig`, use it directly.
 * Otherwise derive defaults from the `encoding` enum.
 */
export function resolveElevationDecoder(config: TerrainMeshTileConfig): {
    rScaler: number;
    gScaler: number;
    bScaler: number;
    offset: number;
} {
    if (config.decoderConfig) {
        return config.decoderConfig;
    }
    switch (config.encoding) {
        case "TERRAIN_RGB":
            return { rScaler: 6553.6, gScaler: 25.6, bScaler: 0.1, offset: -10000 };
        case "TERRARIUM":
            return { rScaler: 256, gScaler: 1, bScaler: 1 / 256, offset: -32768 };
        default:
            return { rScaler: 6553.6, gScaler: 25.6, bScaler: 0.1, offset: -10000 };
    }
}
