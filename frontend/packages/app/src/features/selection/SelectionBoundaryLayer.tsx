// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo, type ReactNode } from "react";
import { PathLayer } from "@deck.gl/layers";
import { MARCHING_ANTS_PATH_STYLE } from "~/features/map/extensions";
import { useRegisterLayers } from "~/features/map/DeckLayerContext";
import { DECK_PRIORITY_SELECTION_BOUNDARY } from "~/features/map/deck-layer-priorities";
import { useAreaSelectionStore } from "./area-selection.store";

/**
 * Draws the closed region the current area selection was gathered from, for as
 * long as the selection lasts.
 *
 * Selection presentation, not tool state: the tool that gathered it has
 * exited by the time this renders, and the ring survives an edit session over a
 * selected element exactly as the selection does.
 *
 * Must be rendered inside <DeckLayerProvider>.
 */
export function SelectionBoundaryLayer(): ReactNode {
    const boundary = useAreaSelectionStore((s) => s.boundary);

    const layers = useMemo(() => {
        if (!boundary || boundary.length < 2) return [];
        return [
            new PathLayer({
                id: "selection-boundary",
                // Closed: the operator selected an area, not a path.
                data: [{ path: [...boundary, boundary[0]] }],
                getPath: (d) => d.path,
                ...MARCHING_ANTS_PATH_STYLE,
            }),
        ];
    }, [boundary]);

    useRegisterLayers("selection-boundary", layers, DECK_PRIORITY_SELECTION_BOUNDARY);

    return null;
}
