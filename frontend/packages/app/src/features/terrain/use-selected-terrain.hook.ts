// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useQuery } from "@tanstack/react-query";
import { tileQueries } from "../imagery/imagery.queries";
import { useSelectedTerrainState } from "./selected-terrain.store";

export function useSelectedTerrain() {
    const terrainId = useSelectedTerrainState((s) => s.terrainId);
    const textureId = useSelectedTerrainState((s) => s.textureId);

    const { data: terrain, isLoading: isTerrainLoading } = useQuery({
        ...tileQueries.detail(terrainId ?? ""),
        enabled: !!terrainId,
    });
    const { data: texture, isLoading: isTextureLoading } = useQuery({
        ...tileQueries.detail(textureId ?? ""),
        enabled: !!textureId,
    });

    return {
        terrain,
        texture,
        isTerrainLoading,
        isTextureLoading,
    };
}
