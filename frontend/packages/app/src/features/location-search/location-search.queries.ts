// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { keepPreviousData, queryOptions } from "@tanstack/react-query";
import { ApiError, getGeocoders, searchGeocoder, type GeocodedPlace } from "@opencop/api";
import { TRADITIONAL_CHINESE_TAG } from "~/i18n/locale.util";
import { toTraditionalDisplay } from "./chinese-script.util";

/** Shorter than this and the results are just noise. */
export const MIN_QUERY_LENGTH = 2;

const DEFAULT_RESULT_LIMIT = 8;

/** Our own per-user throttle (opencop-java `GeocodingProperties.RateLimit`). */
const RATE_LIMITED_STATUS = 429;
/**
 * Longer than the token bucket's worst-case refill (burst exhausted, 10 tokens/s ⇒ ≤100ms for the
 * next one) so a single retry almost always succeeds — the spinner stays up, no error surfaces.
 */
const RATE_LIMIT_RETRY_DELAY_MS = 150;

/**
 * Nominatim matches across scripts but returns the Simplified name Monaco's data carries, so
 * Traditional readers get the text converted for display.
 */
async function toTraditionalPlace(place: GeocodedPlace): Promise<GeocodedPlace> {
    const [name, label] = await Promise.all([
        toTraditionalDisplay(place.name),
        toTraditionalDisplay(place.label),
    ]);
    return { ...place, name, label };
}

/**
 * A failed dictionary-chunk fetch degrades to Simplified display rather than failing the search:
 * the geocoder still answered, and Simplified results are still useful to a Traditional reader.
 */
async function toTraditionalResults(results: GeocodedPlace[]): Promise<GeocodedPlace[]> {
    try {
        return await Promise.all(results.map(toTraditionalPlace));
    } catch {
        return results;
    }
}

export const locationSearchQueries = {
    all: ["location-search"] as const,

    /** The geocoders this deployment offers. Empty means none — show coordinate search only. */
    geocoders: () =>
        queryOptions({
            queryKey: [...locationSearchQueries.all, "geocoders"] as const,
            queryFn: ({ signal }) => getGeocoders({ signal }),
            staleTime: 5 * 60_000,
        }),

    search: ({
        integrationId,
        geocoderId,
        text,
        lang,
        lat,
        lon,
    }: {
        integrationId: string;
        geocoderId: string;
        text: string;
        lang: string;
        /** Focus point, typically the map centre. Send both or neither. */
        lat?: number;
        lon?: number;
    }) =>
        queryOptions({
            queryKey: [
                ...locationSearchQueries.all,
                "search",
                integrationId,
                geocoderId,
                text,
                lang,
                lat,
                lon,
            ] as const,
            queryFn: async ({ signal }) => {
                const response = await searchGeocoder(
                    {
                        integrationId,
                        geocoderId,
                        text,
                        limit: DEFAULT_RESULT_LIMIT,
                        lang,
                        lat,
                        lon,
                    },
                    { signal },
                );
                if (lang !== TRADITIONAL_CHINESE_TAG) return response;
                return { ...response, results: await toTraditionalResults(response.results) };
            },
            enabled: text.length >= MIN_QUERY_LENGTH,
            // Hold previous results only while a search is actually running; a disabled query
            // (cleared box, coordinate) must fall back to the empty-state hint instead.
            placeholderData: text.length >= MIN_QUERY_LENGTH ? keepPreviousData : undefined,
            staleTime: 30_000,
            // Retry only our own rate limit, and only once — every other failure (provider
            // down, geocoder gone) is not a transient blip and should surface immediately.
            retry: (failureCount, err) =>
                failureCount < 1 && err instanceof ApiError && err.status === RATE_LIMITED_STATUS,
            retryDelay: RATE_LIMIT_RETRY_DELAY_MS,
        }),
};
