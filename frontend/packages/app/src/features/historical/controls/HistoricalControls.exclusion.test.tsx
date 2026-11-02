// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MyDataSource, MyLayerInfo } from "@opencop/api";
import { afterEach, describe, expect, it } from "vitest";
import { render, screen, userEvent } from "~/test";
import { dataSourceQueries } from "~/features/data-sources/data-sources.queries";
import { useFilterPanelStore } from "~/features/filters/filter-panel.store";
import { layerQueries } from "~/features/layers/layers.queries";
import { useHistoricalStore } from "../data-plane/historical.store";
import { HistoricalControls } from "./HistoricalControls";

function layer(id: string, name: string): MyLayerInfo {
    return {
        layerId: id,
        layerName: name,
        role: "OWNER",
        accessScope: "PRIVATE",
        defaultVisible: true,
    };
}

function source(id: string, name: string): MyDataSource {
    return {
        serverId: id,
        serverName: name,
        serverState: "CONNECTED",
        role: "OWNER",
        directRole: "OWNER",
        via: "DIRECT",
        access: "RW",
        transportType: "TCP",
        defaultVisible: true,
    };
}

function renderControls() {
    return render(<HistoricalControls />, {
        queryData: {
            [JSON.stringify(layerQueries.myList().queryKey)]: [
                layer("l-kept", "Kept Layer"),
                layer("l-removed", "Removed Layer"),
            ],
            [JSON.stringify(layerQueries.myList(true).queryKey)]: [],
            [JSON.stringify(dataSourceQueries.myList().queryKey)]: [
                source("s-kept", "Kept Source"),
                source("s-removed", "Removed Source"),
            ],
            [JSON.stringify(dataSourceQueries.myList(true).queryKey)]: [],
        },
    });
}

async function openSourcePicker() {
    await userEvent.click(screen.getByRole("combobox"));
}

afterEach(() => {
    useFilterPanelStore.setState({ excludedIds: [] });
    useHistoricalStore.setState(useHistoricalStore.getInitialState(), true);
});

describe("HistoricalControls — panel exclusion", () => {
    it("offers every layer and source when nothing is removed", async () => {
        renderControls();
        await openSourcePicker();

        expect(screen.queryAllByText("Removed Layer").length).toBeGreaterThan(0);
        expect(screen.queryAllByText("Removed Source").length).toBeGreaterThan(0);
    });

    it("does not offer layers or sources the user removed from the map panel", async () => {
        useFilterPanelStore.setState({ excludedIds: ["l-removed", "s-removed"] });
        renderControls();
        await openSourcePicker();

        expect(screen.queryAllByText("Kept Layer").length).toBeGreaterThan(0);
        expect(screen.queryAllByText("Kept Source").length).toBeGreaterThan(0);
        expect(screen.queryAllByText("Removed Layer")).toHaveLength(0);
        expect(screen.queryAllByText("Removed Source")).toHaveLength(0);
    });
});
