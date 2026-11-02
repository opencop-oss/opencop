// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MyDataSource, MyLayerInfo } from "@opencop/api";
import { beforeEach, describe, expect, it } from "vitest";
import { render, screen, userEvent, within } from "~/test";
import { dataSourceQueries } from "~/features/data-sources/data-sources.queries";
import { layerGroupQueries, type MyLayerGroup } from "~/features/layer-groups/layer-groups.queries";
import { layerQueries } from "~/features/layers/layers.queries";
import { defaultMapSettings, useMapSettingsStore } from "~/features/map/map-settings.store";
import { Filters } from "./Filters";
import { useFilterPanelStore } from "./filter-panel.store";

const LAYER_ID = "layer-a";
const DATA_SOURCE_ID = "source-a";
const OUTSIDE_ID = "persisted-outside-current-results";

const LAYERS: MyLayerInfo[] = [
    {
        layerId: LAYER_ID,
        layerName: "Layer A",
        role: "OWNER",
        accessScope: "PRIVATE",
        defaultVisible: true,
    },
];

const DATA_SOURCES: MyDataSource[] = [
    {
        serverId: DATA_SOURCE_ID,
        serverName: "Source A",
        serverState: "CONNECTED",
        role: "OWNER",
        directRole: "OWNER",
        via: "DIRECT",
        access: "RW",
        transportType: "TCP",
        defaultVisible: true,
    },
];

const LAYER_B_ID = "layer-b";
const DATA_SOURCE_B_ID = "source-b";

const MORE_LAYERS: MyLayerInfo[] = [
    ...LAYERS,
    {
        layerId: LAYER_B_ID,
        layerName: "Layer B",
        role: "OWNER",
        accessScope: "PRIVATE",
        defaultVisible: true,
    },
];

const MORE_DATA_SOURCES: MyDataSource[] = [
    ...DATA_SOURCES,
    { ...DATA_SOURCES[0], serverId: DATA_SOURCE_B_ID, serverName: "Source B" },
];

const queryData = {
    [JSON.stringify(layerQueries.myList().queryKey)]: LAYERS,
    [JSON.stringify(layerGroupQueries.myList().queryKey)]: [],
    [JSON.stringify(dataSourceQueries.myList().queryKey)]: DATA_SOURCES,
    [JSON.stringify(layerQueries.dataSources(LAYER_ID).queryKey)]: [],
};

function renderWithMore() {
    return render(<Filters />, {
        queryData: {
            ...queryData,
            [JSON.stringify(layerQueries.myList().queryKey)]: MORE_LAYERS,
            [JSON.stringify(layerQueries.dataSources(LAYER_B_ID).queryKey)]: [],
            [JSON.stringify(dataSourceQueries.myList().queryKey)]: MORE_DATA_SOURCES,
        },
    });
}

function renderFilters() {
    return render(<Filters />, { queryData });
}

const GROUP: MyLayerGroup = {
    id: "group-a",
    name: "Group A",
    description: null,
    containerRole: "OWNER",
    accessScope: "PRIVATE",
    createdAt: "2024-01-01T00:00:00Z",
    layers: [{ id: LAYER_ID, name: "Layer A", accessScope: "PRIVATE", role: "OWNER" }],
};

function renderWithGroup() {
    return render(<Filters />, {
        queryData: { ...queryData, [JSON.stringify(layerGroupQueries.myList().queryKey)]: [GROUP] },
    });
}

function getSectionVisibilityOptions(sectionName: string) {
    const sortButton = screen.getByRole("button", { name: `Sort by ${sectionName}` });
    const sectionActions = sortButton.parentElement;
    if (!sectionActions) throw new Error(`Missing actions for ${sectionName}`);
    return within(sectionActions).getByRole("button", { name: "Visibility options" });
}

beforeEach(() => {
    useFilterPanelStore.setState(useFilterPanelStore.getInitialState(), true);
    useMapSettingsStore.setState({ ...defaultMapSettings });
});

describe("Filters — section text visibility", () => {
    it("shows all Layer text without changing Data Sources, absent IDs, or global settings", async () => {
        useFilterPanelStore.setState({
            knownSources: [LAYER_ID, DATA_SOURCE_ID],
            textVisibleSources: [DATA_SOURCE_ID, OUTSIDE_ID],
        });
        useMapSettingsStore.getState().setTextEnabled(false, "circle");
        const globalBefore = { ...useMapSettingsStore.getState().textEnabled };
        const user = userEvent.setup();
        renderFilters();

        await user.click(getSectionVisibilityOptions("Layers"));
        await user.click(await screen.findByRole("menuitem", { name: "Show all text" }));

        expect(useFilterPanelStore.getState().textVisibleSources).toEqual([
            DATA_SOURCE_ID,
            OUTSIDE_ID,
            LAYER_ID,
        ]);
        expect(useMapSettingsStore.getState().textEnabled).toEqual(globalBefore);
    });

    it("hides all Layer text without changing Data Sources, absent IDs, or global settings", async () => {
        useFilterPanelStore.setState({
            knownSources: [LAYER_ID, DATA_SOURCE_ID],
            textVisibleSources: [LAYER_ID, DATA_SOURCE_ID, OUTSIDE_ID],
        });
        const globalBefore = { ...useMapSettingsStore.getState().textEnabled };
        const user = userEvent.setup();
        renderFilters();

        await user.click(getSectionVisibilityOptions("Layers"));
        await user.click(await screen.findByRole("menuitem", { name: "Hide all text" }));

        expect(useFilterPanelStore.getState().textVisibleSources).toEqual([
            DATA_SOURCE_ID,
            OUTSIDE_ID,
        ]);
        expect(useMapSettingsStore.getState().textEnabled).toEqual(globalBefore);
    });

    it("shows all Data Source text without changing Layers, absent IDs, or global settings", async () => {
        useFilterPanelStore.setState({
            knownSources: [LAYER_ID, DATA_SOURCE_ID],
            textVisibleSources: [LAYER_ID, OUTSIDE_ID],
        });
        const globalBefore = { ...useMapSettingsStore.getState().textEnabled };
        const user = userEvent.setup();
        renderFilters();

        await user.click(getSectionVisibilityOptions("Data Sources"));
        await user.click(await screen.findByRole("menuitem", { name: "Show all text" }));

        expect(useFilterPanelStore.getState().textVisibleSources).toEqual([
            LAYER_ID,
            OUTSIDE_ID,
            DATA_SOURCE_ID,
        ]);
        expect(useMapSettingsStore.getState().textEnabled).toEqual(globalBefore);
    });

    it("hides all Data Source text without changing Layers, absent IDs, or global settings", async () => {
        useFilterPanelStore.setState({
            knownSources: [LAYER_ID, DATA_SOURCE_ID],
            textVisibleSources: [LAYER_ID, DATA_SOURCE_ID, OUTSIDE_ID],
        });
        const globalBefore = { ...useMapSettingsStore.getState().textEnabled };
        const user = userEvent.setup();
        renderFilters();

        await user.click(getSectionVisibilityOptions("Data Sources"));
        await user.click(await screen.findByRole("menuitem", { name: "Hide all text" }));

        expect(useFilterPanelStore.getState().textVisibleSources).toEqual([LAYER_ID, OUTSIDE_ID]);
        expect(useMapSettingsStore.getState().textEnabled).toEqual(globalBefore);
    });
});

describe("Filters — pinned", () => {
    it("lists an ungrouped pinned layer once", () => {
        useFilterPanelStore.setState({ pinnedSources: [LAYER_ID] });
        renderFilters();

        expect(screen.getAllByRole("button", { name: /actions for layer a/i })).toHaveLength(1);
    });

    it("lists a grouped pinned layer once while its group stays collapsed", () => {
        useFilterPanelStore.setState({ pinnedSources: [LAYER_ID] });
        renderWithGroup();

        expect(screen.getAllByRole("button", { name: /actions for layer a/i })).toHaveLength(1);
    });

    it("keeps a grouped pinned layer in its group too, and unpinning from either row clears both", async () => {
        useFilterPanelStore.setState({ pinnedSources: [LAYER_ID] });
        const user = userEvent.setup();
        renderWithGroup();

        await user.click(screen.getByText("Group A"));
        const rows = screen.getAllByRole("button", { name: /actions for layer a/i });
        expect(rows).toHaveLength(2);

        await user.click(rows[1]);
        await user.click(await screen.findByRole("menuitem", { name: /unpin/i }));

        expect(useFilterPanelStore.getState().pinnedSources).toEqual([]);
        expect(screen.getAllByRole("button", { name: /actions for layer a/i })).toHaveLength(1);
    });

    it("the Pinned filter lists only pinned layers, outside their groups", async () => {
        useFilterPanelStore.setState({
            pinnedSources: [LAYER_ID],
            visibilityFilter: "pinned",
        });
        renderWithGroup();

        expect(screen.queryByText("Group A")).not.toBeInTheDocument();
        expect(screen.getAllByRole("button", { name: /actions for layer a/i })).toHaveLength(1);
    });

    it("the Pinned filter hides unpinned layers and data sources", async () => {
        useFilterPanelStore.setState({
            pinnedSources: [LAYER_ID, DATA_SOURCE_ID],
            visibilityFilter: "pinned",
            expandedSections: { "data-sources": true },
        });
        renderWithMore();

        expect(screen.getByRole("group", { name: "Layer A" })).toBeInTheDocument();
        expect(screen.queryByRole("group", { name: "Layer B" })).not.toBeInTheDocument();
        expect(screen.getByRole("group", { name: "Source A" })).toBeInTheDocument();
        expect(screen.queryByRole("group", { name: "Source B" })).not.toBeInTheDocument();
    });

    it("lists pinned data sources first, alphabetically, whatever the sort toggle says", () => {
        useFilterPanelStore.setState({
            pinnedSources: [DATA_SOURCE_B_ID],
            dataSourceSortOrder: "name-asc",
            expandedSections: { "data-sources": true },
        });
        renderWithMore();

        const names = screen
            .getAllByRole("group")
            .map((el) => el.getAttribute("aria-label"))
            .filter((name) => name?.startsWith("Source"));
        expect(names).toEqual(["Source B", "Source A"]);
    });

    it("marks the pinned-block copy of a row for screen readers", () => {
        useFilterPanelStore.setState({ pinnedSources: [LAYER_ID] });
        renderFilters();

        expect(screen.getByRole("group", { name: "Layer A" })).toHaveAccessibleDescription(
            "Pinned",
        );
    });

    it("Hide all in Pinned mode only hides the pinned rows", async () => {
        useFilterPanelStore.setState({ pinnedSources: [LAYER_ID], visibilityFilter: "pinned" });
        const user = userEvent.setup();
        renderWithMore();

        await user.click(getSectionVisibilityOptions("Layers"));
        await user.click(await screen.findByRole("menuitem", { name: /^hide all$/i }));

        expect(useFilterPanelStore.getState().hiddenSources).toEqual([LAYER_ID]);
    });

    it("explains an empty Pinned view and disables the section sort", () => {
        useFilterPanelStore.setState({ visibilityFilter: "pinned" });
        renderFilters();

        expect(screen.getAllByText(/nothing pinned yet/i).length).toBeGreaterThan(0);
        expect(screen.getByRole("button", { name: "Sort by Layers" })).toBeDisabled();
    });

    it("reorders a pinned layer instead of duplicating it", async () => {
        const user = userEvent.setup();
        renderWithMore();

        const group = () => screen.getByRole("group", { name: "Layer B" });
        await user.click(within(group()).getByRole("button", { name: /actions for layer b/i }));
        await user.click(await screen.findByRole("menuitem", { name: /^pin$/i }));

        expect(useFilterPanelStore.getState().pinnedSources).toEqual([LAYER_B_ID]);
        expect(screen.getAllByRole("group", { name: "Layer B" })).toHaveLength(1);
    });
});
