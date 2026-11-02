// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * EXEMPLAR — hook that fetches. The hook runs the *real* `apiFetch` (headers,
 * CSRF, error-throwing, JSON parsing) and the real React Query pipeline against
 * an MSW-mocked HTTP *response* — not a stubbed API module. To control the
 * return value, declare a handler with `server.use(...)`.
 */
import { renderHook, waitFor, server, http, HttpResponse, API_BASE } from "~/test";
import { describe, expect, it } from "vitest";
import { useQuery } from "@tanstack/react-query";
import { layerQueries } from "~/features/layers/layers.queries";
import type { MyLayerInfo } from "@opencop/api";

const fakeLayers: MyLayerInfo[] = [
    {
        layerId: "layer-1",
        layerName: "Alpha Layer",
        role: "OWNER",
        accessScope: "PRIVATE",
        defaultVisible: true,
    },
];

describe("useQuery(layerQueries.myList) (harness exemplar)", () => {
    it("resolves data through the real apiFetch + React Query", async () => {
        server.use(http.get(`${API_BASE}/me/layers`, () => HttpResponse.json(fakeLayers)));

        const { result } = renderHook(() => useQuery(layerQueries.myList()));

        await waitFor(() => expect(result.current.isSuccess).toBe(true));
        expect(result.current.data).toEqual(fakeLayers);
    });
});
