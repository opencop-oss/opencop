// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { act, render, screen, userEvent, server, http, HttpResponse, API_BASE } from "~/test";
import type { ReactNode } from "react";
import { QueryClient } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useWeatherStore } from "./weather.store";
import { WeatherPanel } from "./WeatherPanel";
import type { MapTile } from "@opencop/api";

const MOVE_LAYER_UP_LABEL = "Move layer up";
const MOVE_LAYER_DOWN_LABEL = "Move layer down";

const TILES_URL = `${API_BASE}/map/tiles`;

/** Serve the weather tile list from MSW so real React Query resolves it. */
function stubTiles(tiles: MapTile[]) {
    server.use(http.get(TILES_URL, () => HttpResponse.json(tiles)));
}

/** Serve a failing tile list so the query enters its error state. */
function stubTilesError() {
    server.use(http.get(TILES_URL, () => new HttpResponse(null, { status: 500 })));
}

vi.mock("@dnd-kit/core", () => ({
    DndContext: ({ children }: { children?: ReactNode }) => <>{children}</>,
    closestCenter: undefined,
    PointerSensor: class {},
    KeyboardSensor: class {},
    useSensor: vi.fn(),
    useSensors: vi.fn(() => []),
}));

vi.mock("@dnd-kit/sortable", () => ({
    SortableContext: ({ children }: { children?: ReactNode }) => <>{children}</>,
    arrayMove: (arr: unknown[], from: number, to: number) => {
        const next = [...arr];
        const [item] = next.splice(from, 1);
        next.splice(to, 0, item);
        return next;
    },
    sortableKeyboardCoordinates: undefined,
    verticalListSortingStrategy: undefined,
}));

vi.mock("./SortableWeatherEntry", () => ({
    SortableWeatherEntry: ({
        children,
        onMoveUp,
        onMoveDown,
        canMoveUp,
        canMoveDown,
        id,
    }: {
        children?: ReactNode;
        onMoveUp?: () => void;
        onMoveDown?: () => void;
        canMoveUp?: boolean;
        canMoveDown?: boolean;
        id?: string;
    }) => (
        <div data-testid={`sortable-entry-${id}`}>
            <button
                type="button"
                aria-label={MOVE_LAYER_UP_LABEL}
                onClick={onMoveUp}
                disabled={!canMoveUp}
            />
            <button
                type="button"
                aria-label={MOVE_LAYER_DOWN_LABEL}
                onClick={onMoveDown}
                disabled={!canMoveDown}
            />
            {children}
        </div>
    ),
}));

const windSource: MapTile = {
    id: "weather-wind",
    name: "Wind",
    description: "",
    url: "https://example.com/wind/{z}/{x}/{y}.png",
    token: null,
    attribution: null,
    primary: false,
    enabled: true,
    accessScope: "GLOBAL",
    config: {
        type: "WEATHER",
        extension: {
            extensionType: "WEATHER",
            features: [{ feature: "WIND_10M", url: "https://example.com/wind/{z}/{x}/{y}.png" }],
        },
        tile: {
            kind: "RASTER",
            tileSize: 256,
            minZoom: null,
            maxZoom: null,
        },
    },
};

const precipSource: MapTile = {
    id: "weather-precip",
    name: "Precipitation",
    description: "",
    url: "https://example.com/precip/{z}/{x}/{y}.png",
    token: null,
    attribution: null,
    primary: false,
    enabled: true,
    accessScope: "GLOBAL",
    config: {
        type: "WEATHER",
        extension: {
            extensionType: "WEATHER",
            features: [
                { feature: "PRECIPITATION", url: "https://example.com/precip/{z}/{x}/{y}.png" },
            ],
        },
        tile: {
            kind: "RASTER",
            tileSize: 256,
            minZoom: null,
            maxZoom: null,
        },
    },
};

const wmsSource: MapTile = {
    id: "weather-wms",
    name: "Legacy WMS",
    description: "",
    url: "https://example.com/wms",
    token: null,
    attribution: null,
    primary: false,
    enabled: true,
    accessScope: "GLOBAL",
    config: {
        type: "WEATHER",
        extension: {
            extensionType: "WEATHER",
            features: [{ feature: "WIND_10M", url: "https://example.com/wms" }],
        },
        tile: {
            kind: "WMS",
            version: "1.3.0",
            layers: ["wind"],
            styles: [],
            format: "image/png",
            transparent: true,
            crs: "EPSG:3857",
        },
    },
};

function resetStore() {
    useWeatherStore.setState({ layers: [] });
}

beforeEach(() => {
    resetStore();
    // Default: no tiles configured. Individual tests override via stubTiles.
    stubTiles([]);
});

describe("WeatherPanel", () => {
    it("re-renders the slider with the latest opacity value from the store", async () => {
        useWeatherStore.setState({
            layers: [{ id: "weather-precip", active: false, opacity: 50 }],
        });
        stubTiles([precipSource]);
        render(<WeatherPanel />);

        const slider = await screen.findByRole("slider", { name: "Precipitation opacity value" });
        expect(slider.getAttribute("aria-valuenow")).toBe("50");

        act(() => {
            useWeatherStore.getState().setOpacity("weather-precip", 42);
        });

        expect(slider.getAttribute("aria-valuenow")).toBe("42");
    });

    it("shows a user-facing error when weather sources fail to load", async () => {
        useWeatherStore.setState({ layers: [{ id: "weather-precip", active: true, opacity: 50 }] });
        stubTilesError();

        render(<WeatherPanel />);

        expect(await screen.findByText("Could not load weather sources.")).toBeTruthy();
    });

    it("still lets the user turn the active layer off when source loading fails", async () => {
        useWeatherStore.setState({ layers: [{ id: "weather-precip", active: true, opacity: 50 }] });
        // Seed the tile list into the cache, then fail the refetch: React Query keeps the
        // last-known data while surfacing the error, reproducing "data present + error".
        stubTilesError();

        // This test asserts refetch-on-mount behaviour, so it opts out of the
        // harness's `staleTime: Infinity` default with its own client: the seeded
        // data is treated as stale, the refetch fires, fails, and surfaces the error.
        render(<WeatherPanel />, {
            queryClient: new QueryClient({
                defaultOptions: { queries: { retry: false, staleTime: 0 } },
            }),
            queryData: { '["tiles","list","all"]': [precipSource] },
        });

        // Wait for the failing refetch to surface the error banner.
        await screen.findByText("Could not load weather sources.");

        const toggle = screen.getByRole("switch", { name: "Precipitation" });
        expect((toggle as HTMLInputElement).disabled).toBe(false);

        // Real Chakra Switch toggles on a full user click (Ark UI listens on the
        // control), not a raw fireEvent on the hidden input.
        await userEvent.click(toggle);

        expect(
            useWeatherStore.getState().layers.find((l) => l.id === "weather-precip")?.active,
        ).toBe(false);
    });

    it("renders the section label as an h3 heading", async () => {
        render(<WeatherPanel />);

        expect(await screen.findByRole("heading", { level: 3, name: "Layers" })).toBeTruthy();
        // Let the (empty) tile query settle so no post-test state update leaks.
        await screen.findByText("No weather sources are available.");
    });

    it("shows only configured weather toggles when sources are available", async () => {
        stubTiles([precipSource]);

        render(<WeatherPanel />);

        expect(await screen.findByRole("switch", { name: "Precipitation" })).toBeTruthy();
        expect(screen.queryByText("No weather sources are available.")).toBeNull();
    });

    it("hides weather WMS sources while frontend support is disabled", async () => {
        stubTiles([wmsSource]);
        useWeatherStore.setState({ layers: [{ id: "weather-wms", active: true, opacity: 50 }] });

        render(<WeatherPanel />);

        // WMS tiles are filtered out, so the panel falls back to the empty-state copy.
        await screen.findByText("No weather sources are available.");
        expect(screen.queryByRole("switch", { name: "Legacy WMS" })).toBeNull();
    });

    it("clicking move-up updates layerOrder in the store", async () => {
        stubTiles([precipSource, windSource]);
        useWeatherStore.setState({
            layers: [
                { id: "weather-precip", active: false, opacity: 50 },
                { id: "weather-wind", active: false, opacity: 50 },
            ],
        });

        render(<WeatherPanel />);

        const windEntry = await screen.findByTestId("sortable-entry-weather-wind");
        const moveUpBtn = windEntry.querySelector<HTMLButtonElement>(
            `button[aria-label="${MOVE_LAYER_UP_LABEL}"]`,
        )!;
        act(() => {
            moveUpBtn.click();
        });

        expect(useWeatherStore.getState().layers.map((l) => l.id)).toEqual([
            "weather-wind",
            "weather-precip",
        ]);
    });

    it("move-up is disabled for the first item in layerOrder", async () => {
        stubTiles([precipSource, windSource]);
        useWeatherStore.setState({
            layers: [
                { id: "weather-precip", active: false, opacity: 50 },
                { id: "weather-wind", active: false, opacity: 50 },
            ],
        });

        render(<WeatherPanel />);

        const precipEntry = await screen.findByTestId("sortable-entry-weather-precip");
        const moveUpBtn = precipEntry.querySelector<HTMLButtonElement>(
            `button[aria-label="${MOVE_LAYER_UP_LABEL}"]`,
        )!;
        expect(moveUpBtn.disabled).toBe(true);
    });

    it("move-down is disabled for the last item in layerOrder", async () => {
        stubTiles([precipSource, windSource]);
        useWeatherStore.setState({
            layers: [
                { id: "weather-precip", active: false, opacity: 50 },
                { id: "weather-wind", active: false, opacity: 50 },
            ],
        });

        render(<WeatherPanel />);

        const windEntry = await screen.findByTestId("sortable-entry-weather-wind");
        const moveDownBtn = windEntry.querySelector<HTMLButtonElement>(
            `button[aria-label="${MOVE_LAYER_DOWN_LABEL}"]`,
        )!;
        expect(moveDownBtn.disabled).toBe(true);
    });
});
