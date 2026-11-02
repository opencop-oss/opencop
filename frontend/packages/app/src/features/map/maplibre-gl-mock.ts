// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

// Stub for maplibre-gl in unit tests.
// The real module creates a Worker Blob at load time, which crashes in happy-dom.
export class LngLat {
    readonly lng: number;
    readonly lat: number;

    constructor(lng: number, lat: number) {
        this.lng = lng;
        this.lat = lat;
    }

    toArray(): [number, number] {
        return [this.lng, this.lat];
    }
}

type LngLatLike = [number, number] | LngLat | { lng: number; lat: number };

export class MercatorCoordinate {
    x: number;
    y: number;
    z: number;

    constructor(x: number, y: number, z: number) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    // Identity projection: mercator x/y are treated as lng/lat.
    toLngLat() {
        return new LngLat(this.x, this.y);
    }

    toAltitude() {
        return this.z;
    }

    static fromLngLat(lngLat: LngLatLike, altitude = 0) {
        const [lng, lat] = Array.isArray(lngLat)
            ? lngLat
            : "toArray" in lngLat
              ? lngLat.toArray()
              : [lngLat.lng, lngLat.lat];
        return new MercatorCoordinate(lng, lat, altitude);
    }
}
