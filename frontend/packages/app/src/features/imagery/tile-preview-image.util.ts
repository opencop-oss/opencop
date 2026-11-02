// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/** London at zoom 10 — shared preview location for raster (URL substitution) and vector (MapLibre render) thumbnails. */
export const PREVIEW_CENTER: [number, number] = [-0.13, 51.51];
export const PREVIEW_ZOOM = 10;
export const PREVIEW_TILE = { x: 511, y: 340, yTms: 683 } as const;

export function buildRasterPreviewUrl(url: string): string {
    return url
        .replaceAll("{z}", String(PREVIEW_ZOOM))
        .replaceAll("{x}", String(PREVIEW_TILE.x))
        .replaceAll("{y-tms}", String(PREVIEW_TILE.yTms))
        .replaceAll("{-y}", String(PREVIEW_TILE.yTms))
        .replaceAll("{y}", String(PREVIEW_TILE.y));
}
