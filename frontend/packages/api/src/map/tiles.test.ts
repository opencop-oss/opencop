// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { API_BASE, HttpResponse, http, server } from "~/test";
import {
    createTile,
    deleteTile,
    getAdminTiles,
    getTile,
    getTiles,
    makeTilePrimary,
    updateTile,
    type MapTile,
} from "./tiles";

const TILE_ID = "tile-1";

const TILE = {
    id: TILE_ID,
    primary: false,
    enabled: true,
    accessScope: "PRIVATE",
    name: "Carto Dark",
    description: "",
    url: "https://basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png",
    token: null,
    attribution: null,
    config: {
        type: "BASEMAP",
        tile: { kind: "RASTER", tileSize: 256, minZoom: null, maxZoom: null },
    },
} satisfies MapTile;

/**
 * Tile routes are split across two prefixes and the split is not guessable from the verb.
 *
 * Managing the catalog (listing it whole, adding to it, choosing the primary) is reserved to global
 * admins under /admin. Acting on one tile is authorized per tile instead, so it must sit outside
 * /admin: the server gates that whole prefix on the global admin role before a controller runs,
 * which would put every tile-level role out of reach.
 *
 * Each case mocks only the route it expects. The harness errors on an unhandled request, so calling
 * the wrong prefix fails here rather than 404ing against a real server.
 */
describe("tile route scoping", () => {
    it("reads the operator catalog from the resource route", async () => {
        server.use(http.get(`${API_BASE}/map/tiles`, () => HttpResponse.json([TILE])));

        await expect(getTiles()).resolves.toEqual([TILE]);
    });

    it("reads one tile from the resource route", async () => {
        server.use(http.get(`${API_BASE}/map/tiles/${TILE_ID}`, () => HttpResponse.json(TILE)));

        await expect(getTile(TILE_ID)).resolves.toEqual(TILE);
    });

    it("reads the full catalog from the admin route", async () => {
        server.use(http.get(`${API_BASE}/admin/map/tiles`, () => HttpResponse.json([TILE])));

        await expect(getAdminTiles()).resolves.toEqual([TILE]);
    });

    it("creates on the admin route", async () => {
        server.use(
            http.post(`${API_BASE}/admin/map/tiles`, () =>
                HttpResponse.json(TILE, { status: 201 }),
            ),
        );

        await expect(
            createTile({
                name: "Carto Dark",
                description: "",
                url: TILE.url,
                accessScope: "PRIVATE",
                config: TILE.config,
            }),
        ).resolves.toEqual(TILE);
    });

    it("sets the primary tile on the admin route", async () => {
        server.use(
            http.put(`${API_BASE}/admin/map/tiles/${TILE_ID}/primary`, () =>
                HttpResponse.json(null, { status: 204 }),
            ),
        );

        await expect(makeTilePrimary(TILE_ID)).resolves.toBeUndefined();
    });

    it("patches on the resource route, carrying the change through", async () => {
        let body: unknown;
        server.use(
            http.patch(`${API_BASE}/map/tiles/${TILE_ID}`, async ({ request }) => {
                body = await request.json();
                return HttpResponse.json({ ...TILE, enabled: false });
            }),
        );

        await expect(updateTile(TILE_ID, { enabled: false })).resolves.toMatchObject({
            enabled: false,
        });
        expect(body).toEqual({ enabled: false });
    });

    it("deletes on the resource route", async () => {
        server.use(
            http.delete(`${API_BASE}/map/tiles/${TILE_ID}`, () =>
                HttpResponse.json(null, { status: 204 }),
            ),
        );

        await expect(deleteTile(TILE_ID)).resolves.toBeUndefined();
    });
});
