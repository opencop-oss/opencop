// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { renderHook } from "~/test";
import type { MapTile, RasterTileConfig, TileConfig } from "@opencop/api";

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

const mockUseQuery = vi.fn();

vi.mock("@tanstack/react-query", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@tanstack/react-query")>()),
    useQuery: (...args: unknown[]) => mockUseQuery(...args),
}));

vi.mock("./imagery.queries", () => ({
    tileQueries: {
        list: () => ({ queryKey: ["tiles", "list", "all"], queryFn: vi.fn() }),
        detail: (id: string) => ({
            queryKey: ["tiles", "detail", id],
            queryFn: vi.fn(),
            enabled: !!id,
        }),
    },
}));

vi.mock("~/features/preferences/preferences.queries", () => ({
    preferencesQueries: {
        current: () => ({ queryKey: ["preferences", "current"], queryFn: vi.fn() }),
    },
}));

vi.mock("./imagery.util", () => ({
    resolveMapStyle: (tile: MapTile) => `resolved:${tile.url}:${tile.token ?? "no-token"}`,
    hasTokenPlaceholder: (url: string) => /\{(token|key|api_key|apikey|access_token)\}/.test(url),
}));

vi.mock("~/features/map/map.default", () => ({
    defaultStyle: "DEFAULT_STYLE",
}));

import { useInitialMapStyle } from "./use-initial-map-style.hook";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const rasterConfig: RasterTileConfig = {
    kind: "RASTER",
    tileSize: 256,
    minZoom: null,
    maxZoom: null,
};

const wrapBasemap = (tile: TileConfig): MapTile["config"] => ({
    type: "BASEMAP",
    extension: null,
    tile,
});

const wrapWeather = (
    tile: Extract<TileConfig, { kind: "RASTER" | "WMS" | "WMTS" }>,
    featureIds: string[],
): MapTile["config"] => ({
    type: "WEATHER",
    extension: {
        extensionType: "WEATHER",
        features: featureIds.map((f) => ({ feature: f, url: "" })),
    },
    tile,
});

const makeTile = (overrides: Partial<MapTile> = {}): MapTile => ({
    id: "tile-1",
    primary: false,
    enabled: true,
    accessScope: "GLOBAL",
    name: "Test Tile",
    description: "",
    url: "https://example.com/{z}/{x}/{y}.png",
    token: null,
    attribution: null,
    config: wrapBasemap(rasterConfig),
    ...overrides,
});

const tokenTile = makeTile({
    id: "token-tile",
    url: "https://api.mapbox.com/tiles/{z}/{x}/{y}?access_token={token}",
    token: null, // list endpoint omits the token
    attribution: null,
});

const tokenTileDetail = makeTile({
    id: "token-tile",
    url: "https://api.mapbox.com/tiles/{z}/{x}/{y}?access_token={token}",
    token: "pk.realtoken123", // detail endpoint includes the token
    attribution: null,
});

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * The hook calls useQuery three times:
 *   1. tileQueries.list()       → tiles list
 *   2. preferencesQueries.current() → user preferences
 *   3. tileQueries.detail(id)   → tile detail (token-protected tiles)
 */
function setupQueries({
    tiles = [] as MapTile[],
    tilesLoading = false,
    preferences = undefined as { defaultTile: MapTile | null } | undefined,
    preferencesLoading = false,
    tileDetail = undefined as MapTile | undefined,
}: {
    tiles?: MapTile[];
    tilesLoading?: boolean;
    preferences?: { defaultTile: MapTile | null };
    preferencesLoading?: boolean;
    tileDetail?: MapTile;
} = {}) {
    mockUseQuery.mockImplementation((options: { queryKey: unknown[] }) => {
        const key = JSON.stringify(options.queryKey);

        if (key.includes('"list"')) {
            return { data: tilesLoading ? undefined : tiles, isLoading: tilesLoading };
        }
        if (key.includes('"preferences"')) {
            return {
                data: preferencesLoading ? undefined : preferences,
                isLoading: preferencesLoading,
            };
        }
        if (key.includes('"detail"')) {
            return { data: tileDetail };
        }
        // Harness AuthProvider query (["auth","session"]) — return an
        // authenticated user so the provider renders children (and the hook).
        if (key.includes('"auth"')) {
            return { data: { id: "test-user" }, isLoading: false, error: null };
        }
        // Any other key is unexpected — don't silently resolve it as authenticated.
        return { data: undefined, isLoading: false, error: null };
    });
}

describe("useInitialMapStyle", () => {
    beforeEach(() => {
        mockUseQuery.mockReset();
        sessionStorage.clear();
    });

    afterEach(() => {
        sessionStorage.clear();
    });

    it("returns null while tiles are loading", () => {
        setupQueries({ tilesLoading: true });
        const { result } = renderHook(() => useInitialMapStyle());
        expect(result.current.style).toBeNull();
    });

    it("returns null while preferences are loading", () => {
        setupQueries({ preferencesLoading: true });
        const { result } = renderHook(() => useInitialMapStyle());
        expect(result.current.style).toBeNull();
    });

    it("returns defaultStyle when there are no imagery tiles", () => {
        setupQueries({ tiles: [] });
        const { result } = renderHook(() => useInitialMapStyle());
        expect(result.current.style).toBe("DEFAULT_STYLE");
    });

    it("filters out non-imagery tile kinds", () => {
        const terrainTile = makeTile({
            primary: true,
            config: wrapBasemap({
                kind: "TERRAIN_MESH",
                elevationUrl: undefined,
                encoding: "TERRARIUM",
                decoderConfig: undefined,
                exaggeration: undefined,
                meshMaxError: 4,
                boundsPadding: undefined,
            }),
        });
        setupQueries({ tiles: [terrainTile] });
        const { result } = renderHook(() => useInitialMapStyle());
        expect(result.current.style).toBe("DEFAULT_STYLE");
    });

    it("filters out RASTER tiles with type WEATHER", () => {
        const weatherTile = makeTile({
            id: "weather-1",
            primary: true,
            config: wrapWeather(
                {
                    kind: "RASTER",
                    tileSize: 256,
                    minZoom: null,
                    maxZoom: null,
                },
                ["PRECIPITATION"],
            ),
        });
        setupQueries({ tiles: [weatherTile] });
        const { result } = renderHook(() => useInitialMapStyle());
        expect(result.current.style).toBe("DEFAULT_STYLE");
    });

    it("uses a plain RASTER basemap while ignoring a co-listed weather tile", () => {
        const basemap = makeTile({ id: "base-1", primary: true });
        const weatherTile = makeTile({
            id: "weather-2",
            primary: false,
            config: wrapWeather(
                {
                    kind: "RASTER",
                    tileSize: 256,
                    minZoom: null,
                    maxZoom: null,
                },
                ["WIND_10M"],
            ),
        });
        setupQueries({ tiles: [basemap, weatherTile] });
        const { result } = renderHook(() => useInitialMapStyle());
        expect(result.current.style).toBe("resolved:https://example.com/{z}/{x}/{y}.png:no-token");
    });

    it("selects the primary tile when no session or preference is set", () => {
        const primary = makeTile({ id: "primary-1", primary: true });
        const other = makeTile({ id: "other-1" });
        setupQueries({ tiles: [other, primary] });

        const { result } = renderHook(() => useInitialMapStyle());
        expect(result.current.style).toBe("resolved:https://example.com/{z}/{x}/{y}.png:no-token");
    });

    it("prefers user defaultTile preference over primary tile", () => {
        const primary = makeTile({
            id: "primary-1",
            primary: true,
            url: "https://primary.com/{z}/{x}/{y}",
        });
        const preferred = makeTile({ id: "pref-1", url: "https://preferred.com/{z}/{x}/{y}" });
        setupQueries({
            tiles: [primary, preferred],
            preferences: { defaultTile: preferred },
        });

        const { result } = renderHook(() => useInitialMapStyle());
        expect(result.current.style).toBe("resolved:https://preferred.com/{z}/{x}/{y}:no-token");
    });

    it("prefers sessionStorage selection over preference and primary", () => {
        const primary = makeTile({
            id: "primary-1",
            primary: true,
            url: "https://primary.com/{z}/{x}/{y}",
        });
        const preferred = makeTile({ id: "pref-1", url: "https://preferred.com/{z}/{x}/{y}" });
        const saved = makeTile({ id: "saved-1", url: "https://saved.com/{z}/{x}/{y}" });
        sessionStorage.setItem("opencop-selected-tile-id", "saved-1");

        setupQueries({
            tiles: [primary, preferred, saved],
            preferences: { defaultTile: preferred },
        });

        const { result } = renderHook(() => useInitialMapStyle());
        expect(result.current.style).toBe("resolved:https://saved.com/{z}/{x}/{y}:no-token");
    });

    it("falls back to the primary tile when the preferred tile is no longer offered", () => {
        const primary = makeTile({
            id: "primary-1",
            primary: true,
            url: "https://primary.com/{z}/{x}/{y}",
        });
        // The preference still carries a full tile object, but an admin has since disabled it,
        // so the server withholds it from the list.
        const disabledPreferred = makeTile({
            id: "pref-1",
            enabled: false,
            url: "https://preferred.com/{z}/{x}/{y}",
        });
        setupQueries({
            tiles: [primary],
            preferences: { defaultTile: disabledPreferred },
        });

        const { result } = renderHook(() => useInitialMapStyle());
        expect(result.current.style).toBe("resolved:https://primary.com/{z}/{x}/{y}:no-token");
    });

    it("ignores sessionStorage if the tile ID is not in the list", () => {
        sessionStorage.setItem("opencop-selected-tile-id", "nonexistent");
        const primary = makeTile({ id: "primary-1", primary: true });
        setupQueries({ tiles: [primary] });

        const { result } = renderHook(() => useInitialMapStyle());
        expect(result.current.style).toBe("resolved:https://example.com/{z}/{x}/{y}.png:no-token");
    });

    it("returns null while detail is loading for a token-protected tile", () => {
        setupQueries({ tiles: [{ ...tokenTile, primary: true }], tileDetail: undefined });

        const { result } = renderHook(() => useInitialMapStyle());
        expect(result.current.style).toBeNull();
    });

    it("resolves token-protected tile once detail endpoint returns", () => {
        setupQueries({
            tiles: [{ ...tokenTile, primary: true }],
            tileDetail: tokenTileDetail,
        });

        const { result } = renderHook(() => useInitialMapStyle());
        expect(result.current.style).toBe(
            "resolved:https://api.mapbox.com/tiles/{z}/{x}/{y}?access_token={token}:pk.realtoken123",
        );
    });

    it("enables the detail query only when the selected tile has a token placeholder", () => {
        const noTokenTile = makeTile({ primary: true });
        setupQueries({ tiles: [noTokenTile] });
        renderHook(() => useInitialMapStyle());

        // The third useQuery call is the detail query — check its enabled flag
        const detailCall = mockUseQuery.mock.calls.find((call: unknown[]) => {
            const opts = call[0] as { queryKey: unknown[] };
            return JSON.stringify(opts.queryKey).includes('"detail"');
        });
        expect(detailCall).toBeDefined();
        expect((detailCall![0] as { enabled: boolean }).enabled).toBe(false);
    });

    it("enables the detail query when selected tile has a {token} placeholder", () => {
        setupQueries({ tiles: [{ ...tokenTile, primary: true }] });
        renderHook(() => useInitialMapStyle());

        const detailCall = mockUseQuery.mock.calls.find((call: unknown[]) => {
            const opts = call[0] as { queryKey: unknown[] };
            return JSON.stringify(opts.queryKey).includes('"detail"');
        });
        expect(detailCall).toBeDefined();
        expect((detailCall![0] as { enabled: boolean }).enabled).toBe(true);
    });
});
