// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { getStyleUrl, type TerrainMeshTileConfig } from "@opencop/api";
import { useQuery } from "@tanstack/react-query";
import { useEffect } from "react";
import { tileQueries } from "~/features/imagery/imagery.queries";
import { useTileReconcile } from "~/features/imagery/use-tile-reconcile.hook";
import { getTileConfig } from "~/features/imagery/tile-config.util";
import { useMapManager } from "~/features/map/MapManagerContext";
import { resolveElevationDecoder } from "../map/core/slices/terrain/terrain.util";
import { useTerrainActions } from "../map/core/slices/terrain/terrain.hook";
import { useSelectedTerrain } from "./use-selected-terrain.hook";
import { useSelectedTerrainActions } from "./selected-terrain.store";
import { useMapStore } from "../map/core/map.store";

/**
 * Terrain and texture are resolved by id rather than filtered from a list, so the predicate accepts
 * every offered tile. Membership in the list is the whole test.
 */
const ANY_TILE = () => true;

/** Restores terrain state on mount and selected terrain change. */
export function TerrainRestorer() {
    const mapManager = useMapManager();
    const { data: tiles = [] } = useQuery(tileQueries.list());
    const { reconcile } = useSelectedTerrainActions();
    useTileReconcile(ANY_TILE, reconcile);
    const { terrain, texture } = useSelectedTerrain();

    const { enableTerrain, resetTerrain } = useTerrainActions();
    useEffect(() => {
        if (!terrain || !texture) {
            if (useMapStore.getState().terrainConfig) {
                resetTerrain();
                mapManager.setBasemapVisibility(true);
            }
            return;
        }

        const config = getTileConfig(terrain) as TerrainMeshTileConfig;
        const elevationUrl = getStyleUrl(terrain);
        const decoder = resolveElevationDecoder(config);
        const textureUrl = getStyleUrl(texture);

        enableTerrain({
            elevationUrl,
            textureUrl,
            meshMaxError: config.meshMaxError ?? 4,
            elevationDecoder: decoder,
        });
        mapManager.setBasemapVisibility(false);
    }, [tiles, mapManager, enableTerrain, terrain, texture, resetTerrain]);

    return null;
}
