// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { GeocoderSummary, GeocodingSearchResponse } from "@opencop/api";
import { useQuery } from "@tanstack/react-query";
import { Profiler, type ProfilerOnRenderCallback } from "react";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import {
    API_BASE,
    HttpResponse,
    act,
    http,
    render,
    renderHook,
    screen,
    server,
    userEvent,
    waitFor,
} from "~/test";
import { useMapStore } from "~/features/map/core/map.store";
import { LocationSearch } from "./LocationSearch";
import { locationSearchQueries } from "./location-search.queries";

const INTEGRATION_ID = "00a1f521-8dd4-4270-9f9d-404004e84df3";
const GEOCODER_ID = "680892bb-c3a8-4484-8b93-7d3deea339c0";
const SEARCH_PATH = `${API_BASE}/integrations/${INTEGRATION_ID}/geocoders/${GEOCODER_ID}/search`;

const geocoder: GeocoderSummary = {
    integrationId: INTEGRATION_ID,
    integrationLabel: "Nominatim",
    geocoderId: GEOCODER_ID,
    label: "Nominatim",
    description: "Places from OpenStreetMap, searched through Nominatim.",
    operations: ["SEARCH"],
};

const response: GeocodingSearchResponse = {
    integrationId: INTEGRATION_ID,
    geocoderId: GEOCODER_ID,
    // Exactly what NominatimGeocoderCapability.ATTRIBUTION sends — not a license-notice-shaped
    // string, which a source-policy scanner reads as retained third-party license text.
    attribution: ["© OpenStreetMap contributors"],
    results: [
        {
            id: "R1",
            name: "Casino de Monte Carlo",
            label: "Casino de Monte Carlo, Place du Casino, Monte-Carlo, Monaco",
            placeType: "venue",
            position: { lat: 43.7391605, lon: 7.42802 },
        },
    ],
};

const INITIAL_MAP_STATE = {
    zoom: 1,
    flyToTarget: undefined,
    fitBoundsTarget: undefined,
    latitude: Infinity,
    longitude: Infinity,
};

beforeEach(() => {
    // The map store is a module-level singleton, so an earlier test's view state lingers otherwise.
    useMapStore.setState(INITIAL_MAP_STATE);
});

afterEach(() => {
    useMapStore.setState(INITIAL_MAP_STATE);
});

describe("LocationSearch", () => {
    it("shows results and the attribution as plain text", async () => {
        server.use(http.get(SEARCH_PATH, () => HttpResponse.json(response)));
        const user = userEvent.setup();
        render(<LocationSearch geocoder={geocoder} />);

        await user.type(screen.getByRole("combobox"), "Casino");

        expect(await screen.findByText("Casino de Monte Carlo")).toBeInTheDocument();
        // Plain text, not a link: the attribution sits inside the dismissable popup layer, which
        // Tab doesn't enter, so a link there would be unreachable by keyboard.
        expect(screen.getByText(response.attribution[0]!)).toBeInTheDocument();
        expect(screen.queryByRole("link")).not.toBeInTheDocument();
    });

    it("clamps the zoom when the selected result has no bbox, instead of leaving latitude stuck", async () => {
        server.use(http.get(SEARCH_PATH, () => HttpResponse.json(response)));
        // The app's default view is zoomed out far enough that this bug left latitude at 0.
        useMapStore.setState({ zoom: 0.17 });
        const user = userEvent.setup();
        render(<LocationSearch geocoder={geocoder} />);

        await user.type(screen.getByRole("combobox"), "Casino");
        await user.click(await screen.findByRole("option", { name: /Casino de Monte Carlo/ }));

        expect(useMapStore.getState().flyToTarget?.zoom).toBeGreaterThanOrEqual(10);
    });

    it("pans to a typed coordinate on Enter without ever calling the geocoder", async () => {
        let searched = false;
        server.use(
            http.get(SEARCH_PATH, () => {
                searched = true;
                return HttpResponse.json(response);
            }),
        );
        // Synchronous typing: the default per-keystroke delay races this combobox's controlled
        // value and corrupts fast-typed input (confirmed by logging the DOM value mid-test).
        // Enter is a separate `type` call: combined into the same call as the digits, it fires
        // before the combobox's open-state transition settles and never reaches our handler.
        const user = userEvent.setup({ delay: null });
        render(<LocationSearch geocoder={geocoder} />);
        const input = screen.getByRole("combobox");

        await user.type(input, "43.7391, 7.4280");
        await user.type(input, "{Enter}");

        expect(useMapStore.getState().flyToTarget?.center).toEqual({ lat: 43.7391, lng: 7.428 });
        expect(searched).toBe(false);
    });

    it("doesn't fly to the previous query's top hit if Enter lands before the new search resolves", async () => {
        const jardin: GeocodingSearchResponse = {
            integrationId: INTEGRATION_ID,
            geocoderId: GEOCODER_ID,
            attribution: ["© OpenStreetMap contributors"],
            results: [
                {
                    id: "R2",
                    name: "Jardin Exotique",
                    label: "Jardin Exotique, Monaco",
                    placeType: "venue",
                    position: { lat: 43.7334, lon: 7.4138 },
                },
            ],
        };
        server.use(
            http.get(SEARCH_PATH, async ({ request }) => {
                const text = new URL(request.url).searchParams.get("text") ?? "";
                if (!text.includes("Jardin")) {
                    return HttpResponse.json(response);
                }
                // Held open past the debounce window: by the time this resolves, an unguarded
                // Enter would already have fired against the still-displayed Casino row.
                await new Promise((resolve) => setTimeout(resolve, 1000));
                return HttpResponse.json(jardin);
            }),
        );
        const user = userEvent.setup({ delay: null });
        render(<LocationSearch geocoder={geocoder} />);
        const input = screen.getByRole("combobox");

        await user.type(input, "Casino");
        await screen.findByText("Casino de Monte Carlo");

        await user.clear(input);
        await user.type(input, "Jardin");
        // keepPreviousData still shows Casino's row here; autohighlight marks it the top hit.
        await user.type(input, "{Enter}");

        // Let the in-flight Jardin response land, so a wrongly-accepted Enter has already
        // committed its flyTo by the time this asserts.
        await screen.findByText("Jardin Exotique", {}, { timeout: 3000 });

        expect(useMapStore.getState().flyToTarget?.center).not.toEqual({
            lat: 43.7391605,
            lng: 7.42802,
        });
    });

    it("retries silently through a single rate-limit and still shows results", async () => {
        let calls = 0;
        server.use(
            http.get(SEARCH_PATH, () => {
                calls += 1;
                return calls === 1
                    ? new HttpResponse(null, { status: 429 })
                    : HttpResponse.json(response);
            }),
        );
        const user = userEvent.setup();
        render(<LocationSearch geocoder={geocoder} />);

        await user.type(screen.getByRole("combobox"), "Casino");

        // The retry is invisible: results appear, and no error text ever renders.
        expect(await screen.findByText("Casino de Monte Carlo")).toBeInTheDocument();
        expect(screen.queryByText(/search failed/i)).not.toBeInTheDocument();
        expect(screen.queryByText(/temporarily unavailable/i)).not.toBeInTheDocument();
    });

    it("falls back to the generic message if the rate limit doesn't clear after the retry", async () => {
        server.use(http.get(SEARCH_PATH, () => new HttpResponse(null, { status: 429 })));
        const user = userEvent.setup();
        render(<LocationSearch geocoder={geocoder} />);

        await user.type(screen.getByRole("combobox"), "Casino");

        // No distinct "rate limited" wording — a sustained 429 falls back to the same calm
        // message as a provider outage once the one retry has already been spent.
        const matches = await screen.findAllByText(
            "The place search is temporarily unavailable. Try again shortly.",
        );
        expect(matches).toHaveLength(2);
    });

    it("debounces the map centre so a pan doesn't burst past the per-user rate limit", async () => {
        let searchCalls = 0;
        const seenLat: string[] = [];
        server.use(
            http.get(SEARCH_PATH, ({ request }) => {
                searchCalls += 1;
                seenLat.push(new URL(request.url).searchParams.get("lat") ?? "");
                return HttpResponse.json(response);
            }),
        );
        const user = userEvent.setup();
        render(<LocationSearch geocoder={geocoder} />);

        await user.type(screen.getByRole("combobox"), "Casino");
        await waitFor(() => expect(searchCalls).toBeGreaterThan(0));
        const callsBeforePan = searchCalls;

        // MapView writes the store on every onMove frame — simulate a burst of them, each its
        // own macrotask tick (real onMove callbacks aren't batched together the way a plain
        // synchronous loop would be) and each crossing the 2-decimal rounding boundary, so an
        // undebounced centre would fire once per frame, the same way a real pan or the
        // post-select flyTo does.
        for (let i = 0; i < 10; i++) {
            // Each iteration is its own simulated onMove frame — must stay sequential.
            await act(async () => {
                useMapStore.setState({ latitude: 43 + i * 0.02, longitude: 7 + i * 0.02 });
                await new Promise((resolve) => setTimeout(resolve, 0));
            });
        }

        await waitFor(() => expect(searchCalls).toBe(callsBeforePan + 1));
        expect(seenLat.at(-1)).toBe("43.18");
    });

    it("stops committing once a pan settles, instead of re-rendering forever", async () => {
        server.use(http.get(SEARCH_PATH, () => HttpResponse.json(response)));
        let commits = 0;
        const onRender: ProfilerOnRenderCallback = () => {
            commits += 1;
        };
        render(
            <Profiler id="location-search" onRender={onRender}>
                <LocationSearch geocoder={geocoder} />
            </Profiler>,
        );

        // One simulated onMove frame, then long enough for the debounce to settle and the
        // resulting re-render to commit.
        await act(async () => {
            useMapStore.setState({ latitude: 43.74, longitude: 7.43 });
            await new Promise((resolve) => setTimeout(resolve, 450));
        });
        const commitsAfterSettling = commits;

        // A debounced value that re-arms on every render (the { lat, lon } object-literal bug)
        // keeps committing at roughly one cycle per debounce window, forever, with nothing else
        // happening. A correctly debounced value commits zero more times here.
        await act(async () => {
            await new Promise((resolve) => setTimeout(resolve, 450));
        });

        expect(commits).toBe(commitsAfterSettling);
    });

    it("re-resolves the geocoder list when the search 404s", async () => {
        let geocodersCalls = 0;
        server.use(
            http.get(`${API_BASE}/geocoders`, () => {
                geocodersCalls += 1;
                return HttpResponse.json([geocoder]);
            }),
            http.get(SEARCH_PATH, () => new HttpResponse(null, { status: 404 })),
        );
        const user = userEvent.setup();
        const { queryClient } = render(<LocationSearch geocoder={geocoder} />);
        // Mounts an observer on the geocoders query, same as MapSearch, so the invalidation this
        // 404 triggers has an active query to refetch rather than just marking it stale unseen.
        renderHook(() => useQuery(locationSearchQueries.geocoders()), { queryClient });

        await user.type(screen.getByRole("combobox"), "Casino");

        await waitFor(() => expect(geocodersCalls).toBeGreaterThanOrEqual(2));
    });
});
