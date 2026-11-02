// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo } from "react";
import type { LayerExtension } from "@deck.gl/core";
import { ScatterplotLayer } from "@deck.gl/layers";
import { TerrainOutlineExtension } from "@opencop/layers";
import type { ControlPoint } from "@opencop/tools";
import { getTool } from "~/features/contributions/tool-registry";
import { useRegisterLayers } from "./DeckLayerContext";
import { DECK_PRIORITY_EDIT_HANDLES } from "./deck-layer-priorities";
import { EDIT_HANDLE_STYLE } from "./extensions";
import { useTerrainState } from "./core/slices/terrain/terrain.hook";
import { useInteraction } from "./interaction-runtime/session/interaction.store";
import { sessionControlPoints } from "./scene-binding/session/session-render";

// Shared so every rebuild hands deck the same extension instance.
const HANDLE_TERRAIN_EXTENSIONS = [
    new TerrainOutlineExtension({ outline: false }),
] as LayerExtension[];

/**
 * The running session's edit handles: the control points of the same edit model
 * the drag loop hit-tests, so a handle is drawn exactly where it can be grabbed.
 * Over terrain they float at its height. The id changes with terrain because
 * deck.gl only initializes a layer's extensions when it creates the layer.
 */
export function EditHandlesLayer() {
    const session = useInteraction((s) => s.session);
    const rev = useInteraction((s) => s.rev);
    const terrain = useTerrainState((s) => !!s.terrainConfig);

    const layers = useMemo(() => {
        const points = sessionControlPoints(session, getTool);
        if (points.length === 0) return [];
        return [
            new ScatterplotLayer<ControlPoint, { terrainDrawMode?: "offset" }>({
                id: terrain ? "edit-handles-terrain" : "edit-handles",
                data: points,
                getPosition: (d) => d.position,
                pickable: false,
                ...(terrain
                    ? { extensions: HANDLE_TERRAIN_EXTENSIONS, terrainDrawMode: "offset" as const }
                    : null),
                ...EDIT_HANDLE_STYLE,
                updateTriggers: { getPosition: rev },
            }),
        ];
    }, [session, rev, terrain]);

    useRegisterLayers("edit-handles", layers, DECK_PRIORITY_EDIT_HANDLES);
    return null;
}
