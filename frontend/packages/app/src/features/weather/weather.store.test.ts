// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { DEFAULT_OPACITY, getWeatherOpacity, useWeatherStore } from "./weather.store";

const STORAGE_KEY = "opencop:weather";

function resetStore() {
    localStorage.removeItem(STORAGE_KEY);
    useWeatherStore.setState({ layers: [] });
}

beforeEach(resetStore);
afterEach(resetStore);

describe("toggleLayer", () => {
    it("toggles precipitation on and persists the active layer", () => {
        useWeatherStore.setState({ layers: [{ id: "precipitation", active: false, opacity: 50 }] });
        useWeatherStore.getState().toggleLayer("precipitation");

        expect(
            useWeatherStore.getState().layers.find((l) => l.id === "precipitation")?.active,
        ).toBe(true);
        const persisted = JSON.parse(localStorage.getItem(STORAGE_KEY)!);
        expect(persisted.state.layers).toEqual([
            { id: "precipitation", active: true, opacity: 50 },
        ]);
    });

    it("toggles precipitation off and persists the removal", () => {
        useWeatherStore.setState({ layers: [{ id: "precipitation", active: true, opacity: 50 }] });
        useWeatherStore.getState().toggleLayer("precipitation");

        expect(
            useWeatherStore.getState().layers.find((l) => l.id === "precipitation")?.active,
        ).toBe(false);
        const persisted = JSON.parse(localStorage.getItem(STORAGE_KEY)!);
        expect(persisted.state.layers).toEqual([
            { id: "precipitation", active: false, opacity: 50 },
        ]);
    });
});

describe("setOpacity / getWeatherOpacity", () => {
    it("sets and reads per-layer opacity", () => {
        useWeatherStore.setState({ layers: [{ id: "precipitation", active: false, opacity: 50 }] });
        useWeatherStore.getState().setOpacity("precipitation", 42);
        expect(getWeatherOpacity(useWeatherStore.getState().layers, "precipitation")).toBe(42);
    });

    it("returns 50 as the default opacity for unset layers", () => {
        expect(getWeatherOpacity(useWeatherStore.getState().layers, "precipitation")).toBe(
            DEFAULT_OPACITY,
        );
    });
});

describe("reorderLayers", () => {
    it("replaces layers order with the provided id sequence", () => {
        useWeatherStore.setState({
            layers: [
                { id: "a", active: false, opacity: 50 },
                { id: "b", active: false, opacity: 50 },
                { id: "c", active: false, opacity: 50 },
            ],
        });
        useWeatherStore.getState().reorderLayers(["b", "a", "c"]);
        expect(useWeatherStore.getState().layers.map((l) => l.id)).toEqual(["b", "a", "c"]);
    });

    it("preserves active and opacity values when reordering", () => {
        useWeatherStore.setState({
            layers: [
                { id: "a", active: true, opacity: 30 },
                { id: "b", active: false, opacity: 70 },
            ],
        });
        useWeatherStore.getState().reorderLayers(["b", "a"]);
        const layers = useWeatherStore.getState().layers;
        expect(layers[0]).toEqual({ id: "b", active: false, opacity: 70 });
        expect(layers[1]).toEqual({ id: "a", active: true, opacity: 30 });
    });
});

describe("reconcile", () => {
    it("adds new tiles as inactive with default opacity", () => {
        useWeatherStore.getState().reconcile(["wind", "precip"]);
        const layers = useWeatherStore.getState().layers;
        expect(layers).toEqual([
            { id: "wind", active: false, opacity: DEFAULT_OPACITY },
            { id: "precip", active: false, opacity: DEFAULT_OPACITY },
        ]);
    });

    it("preserves active and opacity for existing entries", () => {
        useWeatherStore.setState({
            layers: [{ id: "wind", active: true, opacity: 75 }],
        });
        useWeatherStore.getState().reconcile(["wind", "precip"]);
        const layers = useWeatherStore.getState().layers;
        expect(layers.find((l) => l.id === "wind")).toEqual({
            id: "wind",
            active: true,
            opacity: 75,
        });
        expect(layers.find((l) => l.id === "precip")).toEqual({
            id: "precip",
            active: false,
            opacity: DEFAULT_OPACITY,
        });
    });

    it("removes tiles that are no longer in the tile list", () => {
        useWeatherStore.setState({
            layers: [
                { id: "wind", active: true, opacity: 50 },
                { id: "old-tile", active: false, opacity: 50 },
            ],
        });
        useWeatherStore.getState().reconcile(["wind"]);
        expect(useWeatherStore.getState().layers.map((l) => l.id)).toEqual(["wind"]);
    });

    it("does not mutate the state when nothing changes", () => {
        useWeatherStore.setState({
            layers: [{ id: "wind", active: false, opacity: DEFAULT_OPACITY }],
        });
        const before = useWeatherStore.getState().layers;
        useWeatherStore.getState().reconcile(["wind"]);
        expect(useWeatherStore.getState().layers).toBe(before);
    });
});

describe("localStorage persistence on load", () => {
    it("hydrates layers from v1 persisted shape", async () => {
        localStorage.setItem(
            STORAGE_KEY,
            JSON.stringify({
                state: {
                    layers: [{ id: "precipitation", active: true, opacity: 42 }],
                },
                version: 1,
            }),
        );

        await useWeatherStore.persist.rehydrate();

        expect(useWeatherStore.getState().layers).toEqual([
            { id: "precipitation", active: true, opacity: 42 },
        ]);
    });

    it("migrates v0 tagged Set/Map shape to v1 layers array", async () => {
        localStorage.setItem(
            STORAGE_KEY,
            JSON.stringify({
                state: {
                    activeWeatherLayers: { __type: "Set", values: ["precipitation"] },
                    opacities: { __type: "Map", entries: [["precipitation", 42]] },
                    layerOrder: ["precipitation"],
                },
                version: 0,
            }),
        );

        await useWeatherStore.persist.rehydrate();

        expect(useWeatherStore.getState().layers).toEqual([
            { id: "precipitation", active: true, opacity: 42 },
        ]);
    });

    it("migrates v0 data preserving active layers not captured by layerOrder", async () => {
        localStorage.setItem(
            STORAGE_KEY,
            JSON.stringify({
                state: {
                    activeWeatherLayers: { __type: "Set", values: ["a", "b"] },
                    opacities: { __type: "Map", entries: [["a", 60]] },
                    layerOrder: ["a"],
                },
                version: 0,
            }),
        );

        await useWeatherStore.persist.rehydrate();

        const layers = useWeatherStore.getState().layers;
        expect(layers.find((l) => l.id === "a")).toEqual({ id: "a", active: true, opacity: 60 });
        expect(layers.find((l) => l.id === "b")).toEqual({
            id: "b",
            active: true,
            opacity: DEFAULT_OPACITY,
        });
    });

    it("falls back to empty layers when v0 data is malformed", async () => {
        localStorage.setItem(STORAGE_KEY, JSON.stringify({ state: null, version: 0 }));

        await useWeatherStore.persist.rehydrate();

        expect(useWeatherStore.getState().layers).toEqual([]);
    });

    it("hydrates all string tile IDs without filtering", async () => {
        localStorage.setItem(
            STORAGE_KEY,
            JSON.stringify({
                state: {
                    layers: [
                        { id: "tile-abc", active: true, opacity: 50 },
                        { id: "tile-xyz", active: false, opacity: 80 },
                    ],
                },
                version: 1,
            }),
        );

        await useWeatherStore.persist.rehydrate();

        expect(useWeatherStore.getState().layers.map((l) => l.id)).toEqual([
            "tile-abc",
            "tile-xyz",
        ]);
    });
});
