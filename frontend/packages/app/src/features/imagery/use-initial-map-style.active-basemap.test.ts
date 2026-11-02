// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { beforeEach, describe, expect, it } from "vitest";
import type { MapTile } from "@opencop/api";
import {
    API_BASE,
    HttpResponse,
    createTestPreferences,
    http,
    renderHook,
    server,
    waitFor,
} from "~/test";
import { useInitialMapStyle } from "./use-initial-map-style.hook";

function rasterTile(id: string, primary: boolean): MapTile {
    return {
        id,
        primary,
        enabled: true,
        accessScope: "GLOBAL",
        name: id,
        description: `${id} tile`,
        url: `https://tiles.example.com/${id}/{z}/{x}/{y}.png`,
        token: null,
        attribution: null,
        config: {
            type: "BASEMAP",
            tile: { kind: "RASTER", tileSize: 256, minZoom: null, maxZoom: null },
        },
    };
}

describe("useInitialMapStyle and the active basemap", () => {
    beforeEach(() => {
        sessionStorage.clear();
    });

    it("returns the id of the tile whose style it resolves, for the active basemap", async () => {
        server.use(
            http.get(`${API_BASE}/map/tiles`, () =>
                HttpResponse.json([rasterTile("other-1", false), rasterTile("primary-1", true)]),
            ),
            http.get(`${API_BASE}/me/preferences`, () =>
                HttpResponse.json(createTestPreferences()),
            ),
        );

        const { result } = renderHook(() => useInitialMapStyle());

        await waitFor(() => expect(result.current.style).not.toBeNull());
        expect(result.current.tileId).toBe("primary-1");
    });
});
