// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { apiFetch, type FetchOptions } from "../client";

export type GeocodingOperation = "SEARCH";

export interface GeocoderSummary {
    integrationId: string;
    integrationLabel: string;
    geocoderId: string;
    label: string;
    description: string;
    operations: GeocodingOperation[];
}

export type PlaceType =
    | "country"
    | "region"
    | "county"
    | "locality"
    | "neighbourhood"
    | "postalcode"
    | "street"
    | "address"
    | "venue"
    | "other";

export interface GeoPoint {
    lat: number;
    lon: number;
}

export interface GeoBoundingBox {
    minLat: number;
    minLon: number;
    maxLat: number;
    maxLon: number;
}

export interface GeocodedPlace {
    id: string;
    name: string;
    label: string;
    placeType: PlaceType;
    category?: string;
    position: GeoPoint;
    bbox?: GeoBoundingBox;
}

export interface GeocodingSearchResponse {
    integrationId: string;
    geocoderId: string;
    /** Credits the provider's licence requires; display wherever these results are shown. */
    attribution: string[];
    results: GeocodedPlace[];
}

export interface SearchGeocoderParams {
    integrationId: string;
    geocoderId: string;
    text: string;
    /** 1 to 20; omit for the server default of 10. */
    limit?: number;
    /** Focus point, typically the map centre. Send both or neither. */
    lat?: number;
    lon?: number;
    /** BCP 47 tag; omit to let the server fall back to `Accept-Language`, then its own default. */
    lang?: string;
}

const GEOCODERS = "/geocoders";

/** Every geocoder this deployment enables and has active; empty when none is offered. */
export async function getGeocoders(options?: FetchOptions): Promise<GeocoderSummary[]> {
    return apiFetch<GeocoderSummary[]>(GEOCODERS, { signal: options?.signal });
}

export async function searchGeocoder(
    params: SearchGeocoderParams,
    options?: FetchOptions,
): Promise<GeocodingSearchResponse> {
    const query = new URLSearchParams({ text: params.text });
    if (params.limit !== undefined) query.set("limit", String(params.limit));
    if (params.lat !== undefined) query.set("lat", String(params.lat));
    if (params.lon !== undefined) query.set("lon", String(params.lon));
    if (params.lang !== undefined) query.set("lang", params.lang);

    return apiFetch<GeocodingSearchResponse>(
        `/integrations/${params.integrationId}/geocoders/${params.geocoderId}/search?${query.toString()}`,
        { signal: options?.signal },
    );
}
