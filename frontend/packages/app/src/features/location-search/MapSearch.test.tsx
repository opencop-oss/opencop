// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { GeocoderSummary } from "@opencop/api";
import { describe, expect, it } from "vitest";
import { API_BASE, HttpResponse, http, render, screen, server, userEvent, waitFor } from "~/test";
import { MapSearch } from "./MapSearch";

function geocoderOf(
    geocoderId: string,
    operations: GeocoderSummary["operations"],
): GeocoderSummary {
    return {
        integrationId: "integration",
        integrationLabel: "Nominatim",
        geocoderId,
        label: "Nominatim",
        description: "Places from OpenStreetMap, searched through Nominatim.",
        operations,
    };
}

describe("MapSearch", () => {
    it("falls back to coordinate search when no geocoder is active", async () => {
        server.use(http.get(`${API_BASE}/geocoders`, () => HttpResponse.json([])));
        render(<MapSearch />);

        expect(await screen.findByRole("textbox")).toBeInTheDocument();
        expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
    });

    it("renders place search when a search-capable geocoder is active", async () => {
        server.use(
            http.get(`${API_BASE}/geocoders`, () =>
                HttpResponse.json([geocoderOf("g1", ["SEARCH"])]),
            ),
        );
        render(<MapSearch />);

        expect(await screen.findByRole("combobox")).toBeInTheDocument();
    });

    it("skips a geocoder that doesn't support SEARCH and uses the next one that does", async () => {
        server.use(
            http.get(`${API_BASE}/geocoders`, () =>
                HttpResponse.json([geocoderOf("g1", []), geocoderOf("g2", ["SEARCH"])]),
            ),
        );
        let searchedGeocoderId: string | null = null;
        server.use(
            http.get(
                `${API_BASE}/integrations/integration/geocoders/:geocoderId/search`,
                ({ params }) => {
                    searchedGeocoderId = params.geocoderId as string;
                    return HttpResponse.json({
                        integrationId: "integration",
                        geocoderId: searchedGeocoderId,
                        attribution: [],
                        results: [],
                    });
                },
            ),
        );
        const user = userEvent.setup({ delay: null });
        render(<MapSearch />);

        await user.type(await screen.findByRole("combobox"), "Casino");

        await waitFor(() => expect(searchedGeocoderId).toBe("g2"));
    });
});
