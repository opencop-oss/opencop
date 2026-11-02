// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { QueryClient } from "@tanstack/react-query";
import type { GeocodingSearchResponse } from "@opencop/api";
import { describe, expect, it, vi } from "vitest";
import { API_BASE, HttpResponse, http, server } from "~/test";
import { locationSearchQueries } from "./location-search.queries";
import { toTraditionalDisplay } from "./chinese-script.util";

// Pure utility, not a network boundary — wrapped (not replaced) so other tests still get the real
// conversion; only the one test below overrides a single call to force the fallback path.
vi.mock("./chinese-script.util", async (importOriginal) => {
    const actual = await importOriginal<typeof import("./chinese-script.util")>();
    return { ...actual, toTraditionalDisplay: vi.fn(actual.toTraditionalDisplay) };
});

const INTEGRATION_ID = "00a1f521-8dd4-4270-9f9d-404004e84df3";
const GEOCODER_ID = "680892bb-c3a8-4484-8b93-7d3deea339c0";
const SEARCH_PATH = `${API_BASE}/integrations/${INTEGRATION_ID}/geocoders/${GEOCODER_ID}/search`;

const response: GeocodingSearchResponse = {
    integrationId: INTEGRATION_ID,
    geocoderId: GEOCODER_ID,
    attribution: ["© OpenStreetMap contributors"],
    results: [
        {
            id: "R1",
            name: "摩纳哥",
            label: "摩纳哥, Monaco",
            placeType: "country",
            position: { lat: 43.73, lon: 7.42 },
        },
    ],
};

function search(lang: string) {
    return new QueryClient().fetchQuery(
        locationSearchQueries.search({
            integrationId: INTEGRATION_ID,
            geocoderId: GEOCODER_ID,
            text: "摩納哥",
            lang,
        }),
    );
}

describe("location search query", () => {
    it("sends the language tag and converts names to Traditional for zh-TW", async () => {
        let requestedLang: string | null = null;
        server.use(
            http.get(SEARCH_PATH, ({ request }) => {
                requestedLang = new URL(request.url).searchParams.get("lang");
                return HttpResponse.json(response);
            }),
        );

        const result = await search("zh-TW");

        expect(requestedLang).toBe("zh-TW");
        expect(result.results[0]).toMatchObject({ name: "摩納哥", label: "摩納哥, Monaco" });
        expect(result.attribution).toEqual(["© OpenStreetMap contributors"]);
    });

    it("returns Simplified names for other languages", async () => {
        server.use(http.get(SEARCH_PATH, () => HttpResponse.json(response)));

        const result = await search("en");

        expect(result.results[0]).toMatchObject({ name: "摩纳哥", label: "摩纳哥, Monaco" });
    });

    it("falls back to the Simplified names when the OpenCC dictionary fails to load", async () => {
        server.use(http.get(SEARCH_PATH, () => HttpResponse.json(response)));
        vi.mocked(toTraditionalDisplay).mockRejectedValueOnce(new Error("chunk load failed"));

        const result = await search("zh-TW");

        // The geocoder still answered; only the display conversion failed.
        expect(result.results[0]).toMatchObject({ name: "摩纳哥", label: "摩纳哥, Monaco" });
    });
});
