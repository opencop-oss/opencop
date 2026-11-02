// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Vector3 } from "@math.gl/core";

export type LngLatLike =
    | { lng: number; lat: number; alt?: number }
    | [lng: number, lat: number, alt?: number];

export class MercatorVector extends Vector3 {
    static fromLngLat(lngLat: LngLatLike): MercatorVector;
    static fromLngLat(lng: number, lat: number, alt?: number): MercatorVector;
    static fromLngLat(arg0: LngLatLike | number, arg1?: number, arg2?: number): MercatorVector {
        const [lng, lat] =
            typeof arg0 === "number"
                ? [arg0, arg1!]
                : Array.isArray(arg0)
                  ? arg0
                  : [arg0.lng, arg0.lat];

        return new MercatorVector(
            (180 + lng) / 360,
            (180 - (180 / Math.PI) * Math.log(Math.tan(Math.PI / 4 + (lat * Math.PI) / 360))) / 360,
            arg2 ?? 0,
        );
    }

    toLngLat(): [lng: number, lat: number, alt: number] {
        return [
            this.x * 360 - 180,
            (360 / Math.PI) * Math.atan(Math.exp(((180 - this.y * 360) * Math.PI) / 180)) - 90,
            this.z,
        ];
    }
}
