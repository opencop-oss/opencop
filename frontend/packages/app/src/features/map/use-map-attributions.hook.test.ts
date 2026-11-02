// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { MapTile } from "@opencop/api";
import { useImageryOverlayStore } from "~/features/imagery/imagery-overlay.store";
import { useWeatherStore } from "~/features/weather/weather.store";
import { API_BASE, HttpResponse, act, http, renderHook, server, waitFor } from "~/test";
import { useMapAttributions } from "./use-map-attributions.hook";

const activeBasemap = vi.hoisted(() => ({ tileId: "basemap" as string | null }));
vi.mock("~/features/imagery/ActiveBasemapContext", () => ({
    useActiveBasemapTileId: () => activeBasemap.tileId,
}));

type Listener = (e?: unknown) => void;
const listeners = new Map<string, Set<Listener>>();
let styleCredit = "© Style source";
const fakeMap = {
    on: (type: string, fn: Listener) => {
        if (!listeners.has(type)) listeners.set(type, new Set());
        listeners.get(type)!.add(fn);
    },
    off: (type: string, fn: Listener) => listeners.get(type)?.delete(fn),
    getStyle: () => ({
        version: 8,
        sources: { base: { type: "vector", url: "https://example.com/base.json" } },
        layers: [{ id: "base", type: "fill", source: "base", "source-layer": "x" }],
    }),
    getZoom: () => 5,
    getSource: (id: string) => (id === "base" ? { attribution: styleCredit } : undefined),
};
vi.mock("react-map-gl/maplibre", () => ({
    useMap: () => ({ current: { getMap: () => fakeMap } }),
}));

function fire(type: string, e?: unknown) {
    act(() => listeners.get(type)?.forEach((fn) => fn(e)));
}

function tile(id: string, attribution: string | null): MapTile {
    return {
        id,
        primary: false,
        enabled: true,
        accessScope: "GLOBAL",
        name: id,
        description: `${id} tile`,
        url: `https://tiles.example.com/${id}/{z}/{x}/{y}.png`,
        token: null,
        attribution,
        config: {
            type: "BASEMAP",
            tile: { kind: "RASTER", tileSize: 256, minZoom: null, maxZoom: null },
        },
    };
}

const rawCredits = (credits: { credit: string }[]) => credits.map(({ credit }) => credit);

describe("useMapAttributions", () => {
    beforeEach(() => {
        listeners.clear();
        styleCredit = "© Style source";
        server.use(
            http.get(`${API_BASE}/map/tiles`, () =>
                HttpResponse.json([
                    tile("basemap", "© Basemap admin"),
                    tile("overlay", "© Overlay imagery"),
                    tile("hidden-overlay", "© Hidden overlay"),
                    tile("weather", "© Radar"),
                ]),
            ),
        );
        activeBasemap.tileId = "basemap";
        useImageryOverlayStore.setState({
            overlays: [
                { id: "overlay", opacity: 1, time: null, visible: true },
                { id: "hidden-overlay", opacity: 1, time: null, visible: false },
            ],
        });
        useWeatherStore.setState({ layers: [{ id: "weather", active: true, opacity: 0.5 }] });
    });

    afterEach(() => {
        activeBasemap.tileId = null;
        useImageryOverlayStore.setState({ overlays: [] });
        useWeatherStore.setState({ layers: [] });
    });

    it("credits the style, the active basemap, visible overlays and active weather, in that order", async () => {
        const { result } = renderHook(() => useMapAttributions());

        await waitFor(() =>
            expect(rawCredits(result.current)).toEqual([
                "© Style source",
                "© Basemap admin",
                "© Overlay imagery",
                "© Radar",
            ]),
        );
        expect(result.current[0].parts).toEqual([{ kind: "text", text: "© Style source" }]);
    });

    it("keeps the same credits through opacity and time ticks, and follows visibility and activation", async () => {
        const { result } = renderHook(() => useMapAttributions());
        await waitFor(() => expect(rawCredits(result.current)).toContain("© Overlay imagery"));
        const before = result.current;

        act(() => useImageryOverlayStore.getState().actions.setOverlayOpacity("overlay", 0.4));
        act(() =>
            useImageryOverlayStore
                .getState()
                .actions.setOverlayTime("overlay", "2026-10-21T00:00:00Z"),
        );
        act(() => useWeatherStore.getState().setOpacity("weather", 0.2));
        expect(result.current).toBe(before);

        act(() =>
            useImageryOverlayStore.getState().actions.setOverlayVisible("hidden-overlay", true),
        );
        expect(result.current).not.toBe(before);
        expect(rawCredits(result.current)).toContain("© Hidden overlay");
    });

    it("re-reads the style on metadata and visibility changes, not on per-tile content", async () => {
        const { result } = renderHook(() => useMapAttributions());
        await waitFor(() => expect(rawCredits(result.current)[0]).toBe("© Style source"));

        styleCredit = "© Updated source";
        fire("sourcedata", { dataType: "source", sourceDataType: "content" });
        expect(rawCredits(result.current)[0]).toBe("© Style source");

        fire("sourcedata", { dataType: "source", sourceDataType: "metadata" });
        expect(rawCredits(result.current)[0]).toBe("© Updated source");

        styleCredit = "© Source now drawn";
        fire("sourcedata", { dataType: "source", sourceDataType: "visibility" });
        expect(rawCredits(result.current)[0]).toBe("© Source now drawn");
    });

    it("stops listening to the map when unmounted", async () => {
        const { unmount } = renderHook(() => useMapAttributions());
        await waitFor(() => expect(listeners.get("styledata")?.size).toBe(1));

        unmount();

        for (const type of ["styledata", "sourcedata", "terrain"]) {
            expect(listeners.get(type)?.size ?? 0).toBe(0);
        }
    });
});
