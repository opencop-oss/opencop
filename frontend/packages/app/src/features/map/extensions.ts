// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { _TerrainExtension as TerrainExtension } from "@deck.gl/extensions";
import { MarchingAntsExtension } from "@opencop/scene";

/**
 * Shared deck.gl extension instances.
 *
 * Deck.gl extensions are stateless per-instance — per-layer state is managed
 * by the framework's extension system. Sharing a single instance across layers
 * prevents redundant shader recompilation (deck.gl uses identity checks on
 * extension instances to determine if shaders need rebuilding).
 */

export const TERRAIN_EXTENSION = new TerrainExtension();

/**
 * Animated marching-ants path outline (dashSize=6, gapSize=4 relative to the 2px stroke
 * width). Replaces `PathStyleExtension` entirely rather than layering on top of it — no
 * separate `getDashArray` attribute is needed.
 */
export const MARCHING_ANTS_EXTENSION = new MarchingAntsExtension({ dashSize: 6, gapSize: 4 });

/**
 * Shared PathLayer props for the marching-ants outlines — the live trace draft
 * and the boundary of the area it selected. The two are drawn by different
 * layers at different times and must stay visually identical.
 */
export const MARCHING_ANTS_PATH_STYLE = {
    getColor: [255, 255, 255, 204] as [number, number, number, number],
    getWidth: 2,
    widthUnits: "pixels" as const,
    extensions: [MARCHING_ANTS_EXTENSION],
    pickable: false,
};

/** Shared ScatterplotLayer props for edit-handle vertices (grab dots). */
export const EDIT_HANDLE_STYLE = {
    getFillColor: [34, 211, 238, 51] as [number, number, number, number],
    getLineColor: [34, 211, 238, 179] as [number, number, number, number],
    getLineWidth: 2,
    stroked: true,
    getRadius: 6.5,
    radiusUnits: "pixels" as const,
    lineWidthUnits: "pixels" as const,
};
