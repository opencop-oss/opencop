// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MapTile } from "./tiles";

/**
 * LocalStorage key for persisting the user's selected tile ID.
 */
export const SELECTED_TILE_KEY = "opencop-selected-tile-id";

/**
 * Constructs the full style URL for a tile, handling token substitution.
 * Replaces common token placeholders like {key}, {token}, {api_key} with the actual token.
 * Uses replaceAll() to handle URLs with multiple occurrences of the same placeholder.
 */
export function getStyleUrl(tile: MapTile): string {
    if (!tile.token) return tile.url;
    return tile.url
        .replaceAll("{key}", tile.token)
        .replaceAll("{token}", tile.token)
        .replaceAll("{api_key}", tile.token)
        .replaceAll("{apikey}", tile.token)
        .replaceAll("{access_token}", tile.token);
}
