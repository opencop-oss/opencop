// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it, vi } from "vitest";
import { QueryClient } from "@tanstack/react-query";
import { shallow } from "zustand/shallow";
import { createTestPreferences } from "~/test/fixtures/preferences";
import { useFilterPanelStore } from "~/features/filters/filter-panel.store";
import { TruthyExpression } from "~/features/map-filter/compiled-filter.model";
import { useMapFilterStore } from "~/features/map-filter/map-filter.store";
import { preferencesQueries } from "~/features/preferences/preferences.queries";
import { useHistoricalStore } from "~/features/historical/data-plane/historical.store";
import type { HistoricalDataManager } from "~/features/historical/data-plane/HistoricalDataManager";
import { resetInteractionForTest, setSessionForTest } from "~/test/interaction";
import { useMapStore } from "../core/map.store";
import type { TerrainConfig } from "../core/slices/terrain/terrain.slice";
import type { Session } from "../interaction-runtime/session/interaction.store";
import { defaultMapSettings, useMapSettingsStore } from "../map-settings.store";
import { RenderSink, type Selector } from "./RenderSink";

const selection: Selector<string | null> = { select: (state) => state.emphasis.selected };

/** A stand-in manager whose range moves on demand. */
function fakeHistorical(range: [number, number]) {
    const listeners = new Set<() => void>();
    const manager = {
        dataFilterRange: range,
        subscribe: (listener: () => void) => {
            listeners.add(listener);
            return () => listeners.delete(listener);
        },
    };
    const move = (next: [number, number]) => {
        manager.dataFilterRange = next;
        for (const listener of listeners) listener();
    };
    return { manager: manager as unknown as HistoricalDataManager, move };
}

afterEach(() => {
    vi.useRealTimers();
    resetInteractionForTest();
    useMapStore.setState(useMapStore.getInitialState(), true);
    useMapSettingsStore.setState({ ...defaultMapSettings });
    useFilterPanelStore.setState(useFilterPanelStore.getInitialState(), true);
    useMapFilterStore.setState({ filters: [], activeNames: [], active: [] });
    useHistoricalStore.setState({ mode: "live" });
});

describe("RenderSink.get", () => {
    it("composes render state from the map, settings, filter panel and map filter stores", () => {
        const filter = new TruthyExpression("everything");
        const textEnabled = { line: false };
        useMapStore.setState({
            selected: "s",
            hovered: "h",
            latitude: 40,
            zoom: 7,
            bounds: [1, 2, 3, 4],
            drape: false,
            depthCompare: "always",
        });
        useMapSettingsStore.setState({
            outlineColor: "#ff0000ff",
            textEnabled,
            textSize: 18,
            textZoomThreshold: 9,
            angleUnit: "mils",
            angleOrientation: "cw",
            symbolSize: 40,
        });
        useFilterPanelStore.setState({ knownSources: ["a", "b"], textVisibleSources: ["a"] });
        useMapFilterStore.setState({ active: [filter] });

        expect(new RenderSink(new QueryClient()).get()).toEqual({
            emphasis: {
                selected: "s",
                hovered: "h",
                outlineColor: "#ff0000ff",
                custodyEdit: false,
            },
            inclusion: { filters: [filter], historical: false, playheadRange: null },
            text: {
                enabledTypes: textEnabled,
                managedSources: ["a", "b"],
                visibleSources: ["a"],
                size: 18,
                zoomThreshold: 9,
            },
            units: { angle: "mils", angleOrientation: "cw", measurement: "IMPERIAL" },
            icons: { size: 40 },
            terrain: { enabled: false, depthCompare: "always", drape: false },
            view: { latitude: 40, zoom: 7, bounds: [1, 2, 3, 4] },
        });
    });

    it("bounds the view to the world until the map reports bounds", () => {
        useMapStore.setState({ bounds: undefined });

        expect(new RenderSink(new QueryClient()).get().view.bounds).toEqual([-180, -90, 180, 90]);
    });

    it("reads the measurement system from the user's preferences, IMPERIAL until they load", () => {
        const client = new QueryClient();
        expect(new RenderSink(client).get().units.measurement).toBe("IMPERIAL");

        client.setQueryData(
            preferencesQueries.current().queryKey,
            createTestPreferences({ measurementSystem: "METRIC" }),
        );

        expect(new RenderSink(client).get().units.measurement).toBe("METRIC");
    });

    it("notifies a units subscriber when the measurement preference changes", () => {
        const client = new QueryClient();
        const listener = vi.fn();
        new RenderSink(client).subscribe({ select: (state) => state.units.measurement }, listener);

        client.setQueryData(
            preferencesQueries.current().queryKey,
            createTestPreferences({ measurementSystem: "METRIC" }),
        );

        expect(listener).toHaveBeenCalledWith("METRIC", "IMPERIAL");
    });
});

describe("RenderSink.subscribe", () => {
    it("calls the listener only when its selection changes, with the next and previous values", () => {
        const sink = new RenderSink(new QueryClient());
        const listener = vi.fn();
        sink.subscribe(selection, listener);

        useMapStore.getState().hover("h");
        useMapSettingsStore.getState().setTextSize(20);
        expect(listener).not.toHaveBeenCalled();

        useMapStore.getState().select("a");
        expect(listener).toHaveBeenCalledExactlyOnceWith("a", null);
    });

    it("compares selections with the selector's equality function", () => {
        const sink = new RenderSink(new QueryClient());
        const select = (state: ReturnType<RenderSink["get"]>) => [state.emphasis.selected];
        const byIdentity = vi.fn();
        const byShallow = vi.fn();
        sink.subscribe({ select }, byIdentity);
        sink.subscribe({ select, equalityFn: shallow }, byShallow);

        useMapStore.getState().hover("h");

        expect(byIdentity).toHaveBeenCalledTimes(1);
        expect(byShallow).not.toHaveBeenCalled();
    });

    it("debounces the listener by the selector's debounce, calling it with the last change", () => {
        vi.useFakeTimers();
        const sink = new RenderSink(new QueryClient());
        const listener = vi.fn();
        sink.subscribe({ ...selection, debounce: 100 }, listener);

        useMapStore.getState().select("a");
        useMapStore.getState().select("b");
        vi.advanceTimersByTime(99);
        expect(listener).not.toHaveBeenCalled();

        vi.advanceTimersByTime(1);
        expect(listener).toHaveBeenCalledExactlyOnceWith("b", "a");
    });

    it("serves its cached state while subscribed, kept current by every store change", () => {
        const sink = new RenderSink(new QueryClient());
        sink.subscribe(selection, vi.fn());
        const before = sink.get();
        expect(sink.get()).toBe(before);

        useMapStore.getState().select("a");

        expect(sink.get()).not.toBe(before);
        expect(sink.get().emphasis.selected).toBe("a");
    });

    it("stops calling the listener once unsubscribed", () => {
        const sink = new RenderSink(new QueryClient());
        const listener = vi.fn();
        const unsubscribe = sink.subscribe(selection, listener);

        unsubscribe();
        useMapStore.getState().select("a");

        expect(listener).not.toHaveBeenCalled();
    });

    it("hears every store it reads", () => {
        const historical = fakeHistorical([0, 0]);
        const sink = new RenderSink(new QueryClient());
        sink.setHistorical(historical.manager);
        const listener = vi.fn();
        sink.subscribe({ select: (state) => state }, listener);

        useMapStore.getState().setDepthCompare("always");
        useMapSettingsStore.getState().setSymbolSize(45);
        useFilterPanelStore.getState().setTextVisibleSources(() => ["a"]);
        useMapFilterStore.setState({ active: [new TruthyExpression("everything")] });
        useMapStore.setState({ terrainConfig: {} as TerrainConfig });
        useHistoricalStore.setState({ mode: "historical" });
        setSessionForTest({ kind: "edit", custody: true } as Session);
        historical.move([10, 20]);

        expect(listener.mock.calls.map(([next]) => next)).toEqual([
            expect.objectContaining({
                terrain: expect.objectContaining({ depthCompare: "always" }),
            }),
            expect.objectContaining({ icons: { size: 45 } }),
            expect.objectContaining({ text: expect.objectContaining({ visibleSources: ["a"] }) }),
            expect.objectContaining({
                inclusion: expect.objectContaining({ filters: [expect.any(TruthyExpression)] }),
            }),
            expect.objectContaining({ terrain: expect.objectContaining({ enabled: true }) }),
            expect.objectContaining({ inclusion: expect.objectContaining({ historical: true }) }),
            expect.objectContaining({ emphasis: expect.objectContaining({ custodyEdit: true }) }),
            expect.objectContaining({
                inclusion: expect.objectContaining({ playheadRange: [10, 20] }),
            }),
        ]);
    });

    it("follows the historical manager it is pointed at, and only that one", () => {
        const first = fakeHistorical([1, 2]);
        const second = fakeHistorical([3, 4]);
        const sink = new RenderSink(new QueryClient());
        const listener = vi.fn();
        sink.subscribe({ select: (state) => state.inclusion.playheadRange }, listener);

        sink.setHistorical(first.manager);
        sink.setHistorical(second.manager);
        first.move([5, 6]);
        second.move([7, 8]);
        sink.setHistorical(null);

        expect(listener.mock.calls.map(([next]) => next)).toEqual([[1, 2], [3, 4], [7, 8], null]);
    });
});
