// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useShallow } from "zustand/react/shallow";
import { useMapStore } from "../../map.store";
import type { TerrainActions, TerrainState } from "./terrain.slice";

export const useTerrainState = <T>(selector: (state: TerrainState) => T): T =>
    useMapStore(selector);

export const useTerrainActions = (): TerrainActions =>
    useMapStore(
        useShallow((state) => ({
            isTerrainEnabled: state.isTerrainEnabled,
            enableTerrain: state.enableTerrain,
            disableTerrain: state.disableTerrain,
            setDrape: state.setDrape,
            setDepthCompare: state.setDepthCompare,
            resetTerrain: state.resetTerrain,
        })),
    );
