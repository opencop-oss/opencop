// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    hexaToRgba,
    TEXT_SIZE,
    type BatchRenderProps,
    type FilterRange,
    type SceneProps,
} from "@opencop/scene";
import type { RenderState } from "../scene-binding/RenderSink";
import { LABEL } from "../scene-binding/elements/element-pipeline.util";

const OUTLINE_WIDTH = 3;

/** Live filter values are 1 when included and -1 when filtered out. */
const LIVE_RANGE: FilterRange = [0, 1];

/** The scene's deck.gl props, derived from the render state. */
export function readSceneProps({
    emphasis,
    inclusion,
    text,
    terrain,
    view,
}: RenderState): SceneProps {
    const [r, g, b] = hexaToRgba(emphasis.outlineColor).map((v) => v / 255);

    const shared: BatchRenderProps = {
        parameters:
            terrain.depthCompare === "always"
                ? { depthCompare: "always", depthWriteEnabled: false }
                : undefined,
        // Historical mode filters by the playhead; map filters only apply live.
        filterRange: inclusion.historical
            ? (inclusion.playheadRange ?? undefined)
            : inclusion.filters.length > 0
              ? LIVE_RANGE
              : undefined,
        outline: {
            // Outlining is off while the selection is being edited.
            enabled: !!emphasis.selected && !emphasis.custodyEdit,
            color: [r, g, b],
            width: OUTLINE_WIDTH,
        },
    };
    // Points, icons, and text sit at terrain height; paths and polygons may drape.
    const offset = terrain.enabled ? "offset" : undefined;
    const draped = terrain.enabled ? (terrain.drape ? "drape" : "offset") : undefined;
    // Below the threshold only pinned labels draw.
    const labels = view.zoom >= text.zoomThreshold;

    return {
        polygon: { ...shared, terrainDrawMode: draped },
        path: { ...shared, terrainDrawMode: draped },
        point: { ...shared, terrainDrawMode: offset },
        angle: { ...shared, terrainDrawMode: draped },
        icon: { ...shared, terrainDrawMode: offset },
        text: {
            parameters: shared.parameters,
            terrainDrawMode: offset,
            filterRange: [
                shared.filterRange ?? LIVE_RANGE,
                labels ? [LABEL.shown, LABEL.pinned] : [LABEL.pinned, LABEL.pinned],
            ],
            // Pinned labels never declutter, and the collision pass doesn't account for terrain height.
            collisionEnabled: labels && !terrain.enabled,
            sizeScale: text.size / TEXT_SIZE,
        },
    };
}
