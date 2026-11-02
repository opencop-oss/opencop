// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    Badge,
    Combobox,
    Flex,
    HStack,
    Portal,
    Spinner,
    Text,
    VisuallyHidden,
    createListCollection,
} from "@chakra-ui/react";
import { ApiError, type GeocoderSummary, type PlaceType } from "@opencop/api";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useDebouncedValue } from "@tanstack/react-pacer";
import { round } from "lodash";
import { useEffect, useMemo, useState, type ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { useSpinDelay } from "spin-delay";
import { toBcp47Locale } from "~/i18n/locale.util";
import { formatCoord, parseCoordinate } from "@opencop/utils";
import { useMapStore } from "~/features/map/core/map.store";
import { useViewActions } from "~/features/map/core/slices/view/view.hook";
import { locationSearchQueries, MIN_QUERY_LENGTH } from "./location-search.queries";

const DEBOUNCE_MS = 300;
const COORDINATE_ZOOM = 10;

/**
 * The geocoder itself is unavailable or overloaded: bad gateway, rate limited, or timed out.
 *
 * Our own per-user throttle (429) is deliberately not distinguished here — the query already
 * retries it once (see `location-search.queries.ts`), so a user only ever sees this branch for
 * a 429 in the rare case that retry also failed, at which point it's no longer a useful signal
 * to call out specifically.
 */
const PROVIDER_UNAVAILABLE_STATUSES = new Set([429, 502, 503, 504]);
/** The addressed geocoder no longer exists or its integration went inactive mid-session. */
const GEOCODER_GONE_STATUS = 404;

type SearchErrorMessageKey = "locationSearch.error" | "locationSearch.providerUnavailable";

function searchErrorMessageKey(error: unknown): SearchErrorMessageKey {
    if (error instanceof ApiError && PROVIDER_UNAVAILABLE_STATUSES.has(error.status)) {
        return "locationSearch.providerUnavailable";
    }
    return "locationSearch.error";
}

/** A geocoder hit in the shape this component renders, independent of the API's field names. */
interface LocationResult {
    id: string;
    name: string;
    label: string;
    placeType: PlaceType;
    /** `[lng, lat]`, WGS84. */
    center: [number, number];
    /** `[minLng, minLat, maxLng, maxLat]` when the provider knows the extent. */
    bbox?: [number, number, number, number];
}

interface SearchItem {
    value: string;
    label: string;
    result: LocationResult;
}

interface LocationSearchProps {
    /** The geocoder to search — this app shows no picker, so callers resolve one default. */
    geocoder: GeocoderSummary;
}

/**
 * Place search for one geocoder. The map-centre focus point is rounded and debounced like the
 * typed text (MapView writes the store every onMove frame; per-user limit 10 req/s, burst 20), and
 * as two primitives rather than one { lat, lon } object: react-pacer re-arms on [value] by
 * reference, so a fresh literal every render would never settle.
 */
export function LocationSearch({ geocoder }: LocationSearchProps): ReactElement {
    const { t, i18n } = useTranslation();
    const { flyTo, fitBounds } = useViewActions();
    const queryClient = useQueryClient();

    const [input, setInput] = useState("");
    const [debouncedInput] = useDebouncedValue(input, { wait: DEBOUNCE_MS });

    const trimmed = debouncedInput.trim();
    const coordinate = trimmed ? parseCoordinate(trimmed) : null;

    // A valid coordinate is unambiguous, so skip the place lookup.
    const searchText = coordinate ? "" : trimmed;

    // Rounded so panning changes the query key only when the centre moves meaningfully; the
    // dummy initial state (Infinity) is excluded rather than sent as a focus point.
    const rawLat = useMapStore((s) =>
        Number.isFinite(s.latitude) ? round(s.latitude, 2) : undefined,
    );
    const rawLon = useMapStore((s) =>
        Number.isFinite(s.longitude) ? round(s.longitude, 2) : undefined,
    );
    // Debounced as two primitives — see the doc-comment.
    const [lat] = useDebouncedValue(rawLat, { wait: DEBOUNCE_MS });
    const [lon] = useDebouncedValue(rawLon, { wait: DEBOUNCE_MS });

    const { data, error, isFetching, isError, isPlaceholderData } = useQuery(
        locationSearchQueries.search({
            integrationId: geocoder.integrationId,
            geocoderId: geocoder.geocoderId,
            text: searchText,
            lang: toBcp47Locale(i18n.language),
            lat,
            lon,
        }),
    );

    // A 404 means this geocoder no longer exists or went inactive — refresh the discovery list
    // rather than keep searching an id that will never answer again.
    useEffect(() => {
        if (error instanceof ApiError && error.status === GEOCODER_GONE_STATUS) {
            void queryClient.invalidateQueries({
                queryKey: locationSearchQueries.geocoders().queryKey,
            });
        }
    }, [error, queryClient]);

    // Against a local Nominatim, results return well under 500ms — don't flash a spinner on a
    // fast search. The "Searching…" empty-state text stays on isFetching (below), not this: the
    // slot would otherwise fall through to "No results" for the delayed spinner's first ~500ms.
    const showSpinner = useSpinDelay(isFetching, { delay: 500, minDuration: 200, ssr: false });

    const results = useMemo(() => data?.results ?? [], [data]);
    const attribution = data?.attribution ?? [];

    const items: SearchItem[] = useMemo(
        () =>
            results.map((place) => ({
                value: place.id,
                label: place.label,
                result: {
                    id: place.id,
                    name: place.name,
                    label: place.label,
                    placeType: place.placeType,
                    center: [place.position.lon, place.position.lat],
                    bbox: place.bbox
                        ? [
                              place.bbox.minLon,
                              place.bbox.minLat,
                              place.bbox.maxLon,
                              place.bbox.maxLat,
                          ]
                        : undefined,
                },
            })),
        [results],
    );

    const collection = useMemo(
        () =>
            createListCollection({
                items,
                itemToString: (item) => item.label,
                itemToValue: (item) => item.value,
            }),
        [items],
    );

    function goToCoordinate(target: { lat: number; lng: number }) {
        const { zoom: currentZoom, pitch, bearing } = useMapStore.getState();
        flyTo({
            center: target,
            zoom: Math.max(currentZoom ?? 1, COORDINATE_ZOOM),
            pitch: pitch ?? 0,
            bearing: bearing ?? 0,
            duration: 1200,
        });
    }

    function handleSelect(value: string) {
        const result = items.find((candidate) => candidate.value === value)?.result;
        if (!result) return;

        // Frame the whole area when we know its extent, otherwise just fly to the point.
        if (result.bbox) {
            const [minLng, minLat, maxLng, maxLat] = result.bbox;
            fitBounds(
                [
                    [minLng, minLat],
                    [maxLng, maxLat],
                ],
                { padding: 80, duration: 1200, maxZoom: 16 },
            );
            return;
        }

        goToCoordinate({ lat: result.center[1], lng: result.center[0] });
    }

    const tooShort = !coordinate && trimmed.length > 0 && trimmed.length < MIN_QUERY_LENGTH;
    const emptyMessage = coordinate
        ? t("locationSearch.pressEnterForCoordinate", {
              coordinate: formatCoord(coordinate.lat, coordinate.lng, "DD"),
          })
        : isError
          ? t(searchErrorMessageKey(error))
          : isFetching
            ? t("locationSearch.searching")
            : tooShort || !trimmed
              ? t("locationSearch.hint")
              : t("locationSearch.noResults");

    return (
        <Combobox.Root
            collection={collection}
            inputValue={input}
            onInputValueChange={(details) => setInput(details.inputValue)}
            onSelect={(details) => handleSelect(details.itemValue)}
            // Enter should take the top hit without an arrow-key detour first.
            inputBehavior="autohighlight"
            openOnClick={false}
            selectionBehavior="preserve"
            size="xs"
            width={{ base: "44", lg: "72" }}
        >
            {/* Mounted before it has anything to say: a live region only reliably announces a
                change inside a node already in the DOM. Combobox.Empty forces role="presentation"
                and unmounts once there are items, so screen-reader users never hear it there. */}
            <VisuallyHidden aria-live="polite">
                {trimmed && !isFetching && collection.size === 0
                    ? emptyMessage
                    : showSpinner
                      ? t("locationSearch.searching")
                      : ""}
            </VisuallyHidden>
            <Combobox.Control>
                <Combobox.Input
                    aria-label={t("locationSearch.label")}
                    placeholder={t("locationSearch.placeholder")}
                    h="7"
                    fontSize="2xs"
                    onKeyDown={(event) => {
                        if (event.key !== "Enter") return;
                        // No row to highlight, and the live input beats the debounced copy here.
                        const typed = parseCoordinate(input.trim());
                        if (typed) {
                            event.preventDefault();
                            goToCoordinate(typed);
                            return;
                        }
                        // Rows still belong to the previous query until the debounce settles and
                        // its fetch lands — selecting now would fly to the stale top hit.
                        if (input.trim() !== trimmed || isPlaceholderData) event.preventDefault();
                    }}
                />
                <Combobox.IndicatorGroup>
                    {showSpinner && <Spinner size="xs" />}
                    <Combobox.ClearTrigger />
                </Combobox.IndicatorGroup>
            </Combobox.Control>
            <Portal>
                <Combobox.Positioner>
                    <Combobox.Content
                        maxH="80"
                        overflowY="auto"
                        p="0"
                        bg="bg.glass"
                        backdropFilter="blur(4px)"
                        borderColor="border"
                        borderWidth="1px"
                        borderRadius="md"
                        boxShadow="md"
                    >
                        <Combobox.Empty>{emptyMessage}</Combobox.Empty>
                        {collection.items.map((item) => (
                            <Combobox.Item key={item.value} item={item}>
                                <Flex direction="column" gap="0.5" minW="0" flex="1">
                                    <HStack gap="2" minW="0">
                                        <Combobox.ItemText
                                            fontSize="xs"
                                            fontWeight="medium"
                                            truncate
                                        >
                                            {item.result.name}
                                        </Combobox.ItemText>
                                        <Badge size="xs" variant="subtle" flexShrink={0}>
                                            {t(
                                                `locationSearch.placeTypes.${item.result.placeType}`,
                                                {
                                                    defaultValue: item.result.placeType,
                                                },
                                            )}
                                        </Badge>
                                    </HStack>
                                    {item.result.label !== item.result.name && (
                                        <Text fontSize="2xs" color="fg.muted" truncate>
                                            {item.result.label}
                                        </Text>
                                    )}
                                </Flex>
                                <Combobox.ItemIndicator />
                            </Combobox.Item>
                        ))}
                        {attribution.length > 0 && (
                            <Flex
                                role="presentation"
                                direction="column"
                                position="sticky"
                                bottom="0"
                                bg="bg.glass"
                                backdropFilter="blur(4px)"
                                borderTopWidth="1px"
                                borderColor="border"
                                px="2"
                                py="1"
                            >
                                {attribution.map((credit) => (
                                    <Text key={credit} fontSize="2xs" color="fg.subtle" truncate>
                                        {credit}
                                    </Text>
                                ))}
                            </Flex>
                        )}
                    </Combobox.Content>
                </Combobox.Positioner>
            </Portal>
        </Combobox.Root>
    );
}
