// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { MercatorCoordinate } from "maplibre-gl";

export type Bounds = [minX: number, minY: number, maxX: number, maxY: number];

export function wrapLongitude(longitude: number): number {
    return ((((longitude + 180) % 360) + 360) % 360) - 180;
}

function projectBounds([minLng, minLat, maxLng, maxLat]: Bounds): Bounds {
    const min = MercatorCoordinate.fromLngLat([minLng, minLat]);
    const minX = min.x;
    const minY = min.y;

    const max = MercatorCoordinate.fromLngLat([maxLng, maxLat]);
    const maxX = max.x;
    const maxY = max.y;

    return [Math.min(minX, maxX), Math.min(minY, maxY), Math.max(minX, maxX), Math.max(minY, maxY)];
}

function unprojectBounds([minX, minY, maxX, maxY]: Bounds): Bounds {
    const a = new MercatorCoordinate(minX, minY).toLngLat();
    const b = new MercatorCoordinate(maxX, maxY).toLngLat();
    return [
        Math.min(a.lng, b.lng),
        Math.min(a.lat, b.lat),
        Math.max(a.lng, b.lng),
        Math.max(a.lat, b.lat),
    ];
}

/**
 * Expand bounds by an expansion factor in mercator space, projected back to lng lat.
 *
 * @param param0 The bounds.
 * @param factor
 * @returns Expanded lng lat bounds.
 */
export function padBounds(bounds: Bounds, factor: number): Bounds {
    const [minX, minY, maxX, maxY] = projectBounds(bounds);

    const dx = (maxX - minX) * factor;
    const dy = (maxY - minY) * factor;

    return unprojectBounds([minX - dx, minY - dy, maxX + dx, maxY + dy]);
}

export function computeArea(bounds: Bounds): number {
    const [minX, minY, maxX, maxY] = projectBounds(bounds);

    return (maxX - minX) * (maxY - minY);
}

export function areBoundsContained(
    [minLng, minLat, maxLng, maxLat]: Bounds,
    [cMinLng, cMinLat, cMaxLng, cMaxLat]: Bounds,
): boolean {
    return cMinLng <= minLng && maxLng <= cMaxLng && cMinLat <= minLat && maxLat <= cMaxLat;
}

export interface SourceObjectDatum<Source> {
    readonly sourceObject: Source;
}

export function getAlwaysVisible<Source, Derived extends SourceObjectDatum<Source>>(
    alwaysVisible: readonly Source[] | undefined,
    derivedObjects: readonly Derived[] | undefined,
    getIdentity?: (source: Source) => string | undefined,
): Derived[] | undefined {
    return alwaysVisible
        ? derivedObjects?.filter((d) =>
              alwaysVisible.some((candidate) => {
                  if (candidate === d.sourceObject) return true;
                  const identity = getIdentity?.(candidate);
                  return identity !== undefined && identity === getIdentity?.(d.sourceObject);
              }),
          )
        : undefined;
}
