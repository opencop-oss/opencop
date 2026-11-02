// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Map as MaplibreMap } from "maplibre-gl";
import Logger from "js-logger";
import type { MapTile } from "@opencop/api";
import { resolveMapStyle } from "./imagery.util";
import { PREVIEW_CENTER, PREVIEW_ZOOM } from "./tile-preview-image.util";

const logger = Logger.get("imagery");

const RENDER_TIMEOUT_MS = 8000;

/**
 * Spins up an off-screen MapLibre instance, waits for idle, and returns a
 * JPEG data URL. Returns null if the signal is aborted or the render times out.
 */
export async function renderVectorTilePreview(
    tile: MapTile,
    signal: AbortSignal,
): Promise<string | null> {
    if (signal.aborted) return null;

    return new Promise<string | null>((resolve) => {
        const container = document.createElement("div");
        container.style.cssText =
            "position:fixed;top:-9999px;left:-9999px;width:220px;height:140px;";
        document.body.appendChild(container);

        let map: MaplibreMap;
        try {
            map = new MaplibreMap({
                container,
                style: resolveMapStyle(tile),
                center: PREVIEW_CENTER,
                zoom: PREVIEW_ZOOM,
                interactive: false,
                attributionControl: false,
                canvasContextAttributes: { preserveDrawingBuffer: true },
            });
        } catch (err) {
            logger.error(
                `vector tile preview: WebGL context creation failed for tile ${tile.id}`,
                err,
            );
            // WebGL context creation failure — clean up and resolve so the queue doesn't hang.
            if (document.body.contains(container)) document.body.removeChild(container);
            resolve(null);
            return;
        }

        let done = false;
        const finish = (src: string | null) => {
            if (done) return;
            done = true;
            map.remove();
            if (document.body.contains(container)) document.body.removeChild(container);
            resolve(src);
        };

        const timeout = setTimeout(() => finish(null), RENDER_TIMEOUT_MS);

        signal.addEventListener(
            "abort",
            () => {
                clearTimeout(timeout);
                finish(null);
            },
            { once: true },
        );

        map.once("idle", () => {
            clearTimeout(timeout);
            try {
                finish(signal.aborted ? null : map.getCanvas().toDataURL("image/jpeg", 0.85));
            } catch {
                // Canvas may be tainted (CORS) — treat as a failed render.
                finish(null);
            }
        });

        // Style load errors (e.g. bad auth token, unreachable server) never produce an idle
        // event. Resolve immediately with null instead of blocking the queue for 8 seconds.
        map.once("error", () => {
            clearTimeout(timeout);
            finish(null);
        });
    });
}
