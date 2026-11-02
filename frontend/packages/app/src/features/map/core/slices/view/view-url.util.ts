// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { clamp } from "lodash";
import type { MapUrlEncoder, MapUrlDecoder } from "../../map-url-codec.util";

export const VIEW_URL_KEYS = ["lat", "lng", "z", "p", "b"] as const;
export type ViewUrlKey = (typeof VIEW_URL_KEYS)[number];

export interface PersistedViewUrlState {
    longitude: number;
    latitude: number;
    zoom: number;
    pitch: number;
    bearing: number;
}

const LAT_DP = 6;
const LNG_DP = 6;
const ZOOM_DP = 2;
const ANGLE_DP = 1;

const LAT_MIN = -90;
const LAT_MAX = 90;
const LNG_MIN = -180;
const LNG_MAX = 180;
/** The map's fully zoomed-out floor. */
export const ZOOM_MIN = 0;
/** The closest zoom reachable, so also the natural upper bound for any UI control
 * that lets an operator pick a zoom level. */
export const ZOOM_MAX = 22;
const PITCH_MIN = 0;
const PITCH_MAX = 85;

/** Normalize bearing to (-180, 180], matching MapLibre's `getBearing()` convention. */
const wrapBearing = (n: number) => 180 - ((((180 - n) % 360) + 360) % 360);

const round = (n: number, dp: number) => {
    const f = 10 ** dp;
    return Math.round(n * f) / f;
};

export const encodeViewUrl: MapUrlEncoder<PersistedViewUrlState> = (state) => {
    const out: Partial<Record<ViewUrlKey, string | null>> = {};

    if (typeof state.latitude === "number" && Number.isFinite(state.latitude)) {
        out.lat = String(round(clamp(state.latitude, LAT_MIN, LAT_MAX), LAT_DP));
    }
    if (typeof state.longitude === "number" && Number.isFinite(state.longitude)) {
        out.lng = String(round(clamp(state.longitude, LNG_MIN, LNG_MAX), LNG_DP));
    }
    if (typeof state.zoom === "number" && Number.isFinite(state.zoom)) {
        out.z = String(round(clamp(state.zoom, ZOOM_MIN, ZOOM_MAX), ZOOM_DP));
    }
    if (typeof state.pitch === "number" && Number.isFinite(state.pitch)) {
        out.p = String(round(clamp(state.pitch, PITCH_MIN, PITCH_MAX), ANGLE_DP));
    }
    if (typeof state.bearing === "number" && Number.isFinite(state.bearing)) {
        out.b = String(round(wrapBearing(state.bearing), ANGLE_DP));
    }

    return out;
};

export const decodeViewUrl: MapUrlDecoder<PersistedViewUrlState> = (params) => {
    const out: Partial<PersistedViewUrlState> = {};

    const readNumber = (key: ViewUrlKey): number | null => {
        const raw = params.get(key);
        if (raw === null || raw === "") return null;
        const n = Number(raw);
        return Number.isFinite(n) ? n : null;
    };

    const lat = readNumber("lat");
    if (lat !== null) out.latitude = round(clamp(lat, LAT_MIN, LAT_MAX), LAT_DP);

    const lng = readNumber("lng");
    if (lng !== null) out.longitude = round(clamp(lng, LNG_MIN, LNG_MAX), LNG_DP);

    const z = readNumber("z");
    if (z !== null) out.zoom = round(clamp(z, ZOOM_MIN, ZOOM_MAX), ZOOM_DP);

    const p = readNumber("p");
    if (p !== null) out.pitch = round(clamp(p, PITCH_MIN, PITCH_MAX), ANGLE_DP);

    const b = readNumber("b");
    if (b !== null) out.bearing = round(wrapBearing(b), ANGLE_DP);

    return out;
};
