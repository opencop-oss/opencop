// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { beforeEach, describe, expect, it, vi } from "vitest";
import type { MapTile, TileConfig } from "@opencop/api";
import { act, render } from "~/test";
import { RasterOverlayLayer as CompositeWeatherLayer } from "@opencop/layers";
import type { RasterLayerEntry } from "~/features/imagery/raster-layer-factory";
import { useWeatherStore } from "~/features/weather/weather.store";

const registerMock = vi.fn();
const mockUseQuery = vi.fn();
const mockUseQueries = vi.fn();

vi.mock("~/features/map/DeckLayerContext", () => ({
    useRegisterLayers: (id: string, layers: unknown[]) => {
        registerMock(id, layers);
    },
}));

vi.mock("@tanstack/react-query", async (importOriginal) => {
    const actual = await importOriginal<typeof import("@tanstack/react-query")>();
    return {
        ...actual,
        useQuery: (...args: unknown[]) => mockUseQuery(...args),
        useQueries: (...args: unknown[]) => mockUseQueries(...args),
    };
});

function wrapWeather(
    tile: Extract<TileConfig, { kind: "RASTER" | "WMS" | "WMTS" }>,
    featureIds: string[],
    baseUrl: string,
): MapTile["config"] {
    return {
        type: "WEATHER",
        extension: {
            extensionType: "WEATHER",
            features: featureIds.map((f) => ({ feature: f, url: baseUrl })),
        },
        tile,
    };
}

let mockTiles: MapTile[] = [];
let mockTileDetails = new Map<string, MapTile>();
let mockQueryError: Error | null = null;

interface MockUseQueriesOptions {
    queries: Array<{ queryKey: unknown[] }>;
    combine?: (
        results: Array<{
            data: MapTile | undefined;
        }>,
    ) => Map<string, string | null>;
}

function makeWeatherSource(
    id: string,
    tile: Extract<TileConfig, { kind: "RASTER" | "WMS" | "WMTS" }>,
    features: string[],
    overrides: Partial<MapTile> = {},
): MapTile {
    const url = (overrides.url ?? `https://example.com/${id}/{z}/{x}/{y}.png`) as string;
    return {
        id,
        name: id,
        description: "",
        url,
        token: null,
        attribution: null,
        primary: false,
        enabled: true,
        accessScope: "GLOBAL",
        config: wrapWeather(tile, features, url),
        ...overrides,
    };
}

const precipSource = makeWeatherSource(
    "src-precip",
    {
        kind: "RASTER",
        tileSize: 256,
        minZoom: null,
        maxZoom: null,
    },
    ["PRECIPITATION"],
);

const keyedPrecipListSource = makeWeatherSource(
    "src-precip-keyed",
    {
        kind: "RASTER",
        tileSize: 256,
        minZoom: null,
        maxZoom: null,
    },
    ["PRECIPITATION"],
    {
        url: "https://example.com/precip/{z}/{x}/{y}.png?key={key}",
    },
);

const keyedPrecipDetailSource: MapTile = {
    ...keyedPrecipListSource,
    token: "secret-key",
    attribution: null,
};

const gibsPrecipitationSource = makeWeatherSource(
    "src-gibs-precip",
    {
        kind: "WMTS",
        version: "1.0.0",
        layer: "IMERG_Precipitation_Rate_30min",
        style: "default",
        format: "image/png",
        tileMatrixSet: "GoogleMapsCompatible_Level6",
    },
    ["PRECIPITATION_RATE"],
    {
        name: "NASA GIBS Precipitation",
        url: "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/IMERG_Precipitation_Rate_30min/default/{Time}/GoogleMapsCompatible_Level6/{z}/{y}/{x}.png",
    },
);

const wmsWindSource = makeWeatherSource(
    "src-wms-wind",
    {
        kind: "WMS",
        version: "1.3.0",
        layers: ["wind"],
        styles: [],
        format: "image/png",
        transparent: true,
        crs: "EPSG:3857",
    },
    ["WIND_10M"],
);

const { WeatherLayer } = await import("./WeatherLayer");

function resetStore() {
    useWeatherStore.setState({ layers: [] });
}

function getRegisteredCompositeLayer() {
    const layers = registerMock.mock.calls.at(-1)?.[1] ?? [];
    return layers[0] as CompositeWeatherLayer;
}

beforeEach(() => {
    mockTiles = [];
    mockTileDetails = new Map();
    mockQueryError = null;
    registerMock.mockReset();
    mockUseQuery.mockReset();
    mockUseQueries.mockReset();
    mockUseQuery.mockImplementation(() => ({
        data: mockTiles,
        isError: mockQueryError !== null,
        error: mockQueryError,
    }));
    mockUseQueries.mockImplementation((options: MockUseQueriesOptions) => {
        const results = options.queries.map((query) => ({
            data: mockTileDetails.get(String(query.queryKey[2])),
        }));
        return options.combine ? options.combine(results) : results;
    });
    resetStore();
});

describe("WeatherLayer (deck registration)", () => {
    it("registers one composite weather layer when a weather tile is active", () => {
        mockTiles = [precipSource];
        useWeatherStore.setState({ layers: [{ id: "src-precip", active: true, opacity: 50 }] });

        render(<WeatherLayer />);

        expect(registerMock).toHaveBeenCalledWith(
            "weather",
            expect.arrayContaining([expect.objectContaining({ id: "weather" })]),
        );
        expect(getRegisteredCompositeLayer()).toBeInstanceOf(CompositeWeatherLayer);
    });

    it("passes the active weather source through as composite layer data", () => {
        mockTiles = [precipSource];
        useWeatherStore.setState({ layers: [{ id: "src-precip", active: true, opacity: 50 }] });

        render(<WeatherLayer />);

        const layer = getRegisteredCompositeLayer();
        const data = layer.props.data as RasterLayerEntry[];

        expect(data).toHaveLength(1);
        expect(data[0]).toMatchObject({
            opacity: 50,
            time: null,
            tile: expect.objectContaining({ id: "src-precip", token: null }),
        });
    });

    it("registers updated layer data when opacity changes", () => {
        mockTiles = [precipSource];
        useWeatherStore.setState({ layers: [{ id: "src-precip", active: true, opacity: 50 }] });

        render(<WeatherLayer />);

        act(() => {
            useWeatherStore.getState().setOpacity("src-precip", 42);
        });

        const layer = getRegisteredCompositeLayer();
        const data = layer.props.data as RasterLayerEntry[];

        expect(data[0]?.opacity).toBe(42);
    });

    it("uses the shared 50 percent default opacity when none is stored yet", () => {
        mockTiles = [precipSource];
        useWeatherStore.setState({ layers: [{ id: "src-precip", active: true, opacity: 50 }] });

        render(<WeatherLayer />);

        const layer = getRegisteredCompositeLayer();
        const data = layer.props.data as RasterLayerEntry[];

        expect(data[0]?.opacity).toBe(50);
    });

    it("registers an empty array when no layers are active", () => {
        mockTiles = [precipSource];

        render(<WeatherLayer />);

        expect(registerMock).toHaveBeenCalledWith("weather", []);
    });

    it("registers an empty array when the active tile ID is not in the tile list", () => {
        mockTiles = [];
        useWeatherStore.setState({ layers: [{ id: "src-precip", active: true, opacity: 50 }] });

        render(<WeatherLayer />);

        expect(registerMock).toHaveBeenCalledWith("weather", []);
    });

    it("skips weather WMS sources (only supported as imagery overlays, not weather layers)", () => {
        mockTiles = [wmsWindSource];
        useWeatherStore.setState({ layers: [{ id: "src-wms-wind", active: true, opacity: 50 }] });

        render(<WeatherLayer />);

        expect(registerMock).toHaveBeenCalledWith("weather", []);
    });

    it("fetches detail for tokenized sources before registering the weather layer", () => {
        mockTiles = [keyedPrecipListSource];
        mockTileDetails.set(keyedPrecipDetailSource.id, keyedPrecipDetailSource);
        useWeatherStore.setState({
            layers: [{ id: "src-precip-keyed", active: true, opacity: 50 }],
        });

        render(<WeatherLayer />);

        const layer = getRegisteredCompositeLayer();
        const data = layer.props.data as RasterLayerEntry[];

        expect(data[0]?.tile.token).toBe("secret-key");
    });

    it("orders composite layer data so higher-priority entries render later", () => {
        mockTiles = [precipSource, gibsPrecipitationSource];
        useWeatherStore.setState({
            layers: [
                { id: "src-precip", active: true, opacity: 50 },
                { id: "src-gibs-precip", active: true, opacity: 50 },
            ],
        });

        render(<WeatherLayer />);

        const layer = getRegisteredCompositeLayer();
        const data = layer.props.data as RasterLayerEntry[];

        expect(data.map((entry) => entry.tile.id)).toEqual(["src-gibs-precip", "src-precip"]);
    });
});
