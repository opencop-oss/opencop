// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it } from "vitest";
import { QueryClient } from "@tanstack/react-query";
import { Line } from "@opencop/elements";
import { TEXT_SIZE } from "@opencop/scene";
import { resetInteractionForTest, setSessionForTest } from "~/test/interaction";
import { TruthyExpression } from "~/features/map-filter/compiled-filter.model";
import { useMapFilterStore } from "~/features/map-filter/map-filter.store";
import { useHistoricalStore } from "~/features/historical/data-plane/historical.store";
import type { HistoricalDataManager } from "~/features/historical/data-plane/HistoricalDataManager";
import { useMapStore } from "../core/map.store";
import type { TerrainConfig } from "../core/slices/terrain/terrain.slice";
import type { Session } from "../interaction-runtime/session/interaction.store";
import {
    DEFAULT_SYMBOL_SIZE,
    defaultMapSettings,
    useMapSettingsStore,
} from "../map-settings.store";
import { LABEL } from "../scene-binding/elements/element-pipeline.util";
import { RenderSink } from "../scene-binding/RenderSink";
import { readSceneProps } from "./scene-props.util";

const TERRAIN: TerrainConfig = {
    elevationUrl: "elevation",
    textureUrl: "texture",
    meshMaxError: 4,
    elevationDecoder: { rScaler: 1, gScaler: 1, bScaler: 1, offset: 0 },
};

const playhead = { dataFilterRange: [10, 20] } as unknown as HistoricalDataManager;

function sceneProps(historical: HistoricalDataManager | null = null) {
    const sink = new RenderSink(new QueryClient());
    sink.setHistorical(historical);
    return readSceneProps(sink.get());
}

const live = () => sceneProps();

function editing(custody: boolean): Session {
    return {
        kind: "edit",
        descriptorId: null,
        epoch: 1,
        product: Line.create({ positions: [0, 0, 1, 1] }),
        ref: { objectId: "a" } as never,
        custody,
        params: {},
        isNew: false,
        sub: { hotspot: null, dragging: null },
    } as Session;
}

afterEach(() => {
    resetInteractionForTest();
    useMapStore.setState(useMapStore.getInitialState(), true);
    useMapSettingsStore.setState({ ...defaultMapSettings });
    useMapFilterStore.setState({ filters: [], activeNames: [], active: [] });
    useHistoricalStore.setState({ mode: "live" });
});

describe("readSceneProps", () => {
    it("lets shown and pinned labels through at the text zoom threshold, and only pinned ones below it", () => {
        useMapSettingsStore.setState({ textZoomThreshold: 8 });

        useMapStore.setState({ zoom: 8 });
        const shown = live().text!.filterRange!;
        useMapStore.setState({ zoom: 7.9 });
        const culled = live().text!.filterRange!;

        expect(shown[1]).toEqual([LABEL.shown, LABEL.pinned]);
        expect(culled[1]).toEqual([LABEL.pinned, LABEL.pinned]);
        expect(culled[0]).toEqual(shown[0]);
    });

    it("filters live primitives by the map filters only while one is active", () => {
        useMapStore.setState({ zoom: 10 });
        const unfiltered = live();
        useMapFilterStore.setState({ active: [new TruthyExpression("everything")] });
        const filtered = live();

        for (const batch of [
            unfiltered.polygon,
            unfiltered.path,
            unfiltered.point,
            unfiltered.icon,
        ])
            expect(batch?.filterRange).toBeUndefined();
        for (const batch of [filtered.polygon, filtered.path, filtered.point, filtered.icon])
            expect(batch?.filterRange).toEqual([0, 1]);
        // Text always filters, so its label channel applies.
        expect(unfiltered.text?.filterRange).toEqual([
            [0, 1],
            [LABEL.shown, LABEL.pinned],
        ]);
        expect(filtered.text?.filterRange).toEqual([
            [0, 1],
            [LABEL.shown, LABEL.pinned],
        ]);
    });

    it("filters by the playhead's range in historical mode, ignoring the map filters", () => {
        useMapStore.setState({ zoom: 10 });
        useHistoricalStore.setState({ mode: "historical" });
        useMapFilterStore.setState({ active: [new TruthyExpression("everything")] });

        const props = sceneProps(playhead);

        for (const batch of [props.polygon, props.path, props.point, props.icon])
            expect(batch?.filterRange).toEqual([10, 20]);
        expect(props.text?.filterRange).toEqual([
            [10, 20],
            [LABEL.shown, LABEL.pinned],
        ]);
    });

    it("leaves historical primitives unfiltered before a manager supplies a range", () => {
        useHistoricalStore.setState({ mode: "historical" });

        const props = live();

        expect(props.polygon?.filterRange).toBeUndefined();
        expect(props.text?.filterRange?.[0]).toEqual([0, 1]);
    });

    it("declutters labels only while they show and terrain is off", () => {
        useMapSettingsStore.setState({ textZoomThreshold: 5 });

        useMapStore.setState({ zoom: 6 });
        expect(live().text?.collisionEnabled).toBe(true);

        useMapStore.setState({ zoom: 4 });
        expect(live().text?.collisionEnabled).toBe(false);

        useMapStore.setState({ zoom: 6, terrainConfig: TERRAIN });
        expect(live().text?.collisionEnabled).toBe(false);
    });

    it("attaches no terrain without terrain", () => {
        const props = live();

        for (const batch of [
            props.polygon,
            props.path,
            props.point,
            props.angle,
            props.icon,
            props.text,
        ])
            expect(batch?.terrainDrawMode).toBeUndefined();
    });

    it("drapes polygons, paths and angle arcs over terrain when draping, and offsets everything else", () => {
        useMapStore.setState({ terrainConfig: TERRAIN, drape: true });
        const draped = live();
        useMapStore.setState({ drape: false });
        const offset = live();

        expect(draped.polygon?.terrainDrawMode).toBe("drape");
        expect(draped.path?.terrainDrawMode).toBe("drape");
        expect(draped.angle?.terrainDrawMode).toBe("drape");
        for (const batch of [draped.point, draped.icon, draped.text])
            expect(batch?.terrainDrawMode).toBe("offset");
        for (const batch of [
            offset.polygon,
            offset.path,
            offset.point,
            offset.angle,
            offset.icon,
            offset.text,
        ])
            expect(batch?.terrainDrawMode).toBe("offset");
    });

    it("draws every batch over everything while depth compare is always", () => {
        expect(live().polygon?.parameters).toBeUndefined();

        useMapStore.setState({ depthCompare: "always" });
        const props = live();

        for (const batch of [props.polygon, props.path, props.point, props.icon, props.text])
            expect(batch?.parameters).toEqual({ depthCompare: "always", depthWriteEnabled: false });
    });

    it("outlines the selection in the settings' outline color, but not text", () => {
        useMapSettingsStore.setState({ outlineColor: "#ff800080" });
        useMapStore.setState({ selected: "a" });

        const props = live();

        expect(props.polygon?.outline).toEqual({
            enabled: true,
            color: [1, 128 / 255, 0],
            width: 3,
        });
        expect(props.text).not.toHaveProperty("outline");
    });

    it("turns the outline off without a selection, and during a custody edit", () => {
        expect(live().path?.outline?.enabled).toBe(false);

        useMapStore.setState({ selected: "a" });
        setSessionForTest(editing(false));
        expect(live().path?.outline?.enabled).toBe(true);

        setSessionForTest(editing(true));
        expect(live().path?.outline?.enabled).toBe(false);
    });

    it("leaves icon size to the mapper and scales text by the text size", () => {
        useMapSettingsStore.setState({ symbolSize: DEFAULT_SYMBOL_SIZE * 2, textSize: 30 });

        const props = live();

        expect(props.icon?.sizeScale).toBeUndefined();
        expect(props.text?.sizeScale).toBe(30 / TEXT_SIZE);
    });
});
