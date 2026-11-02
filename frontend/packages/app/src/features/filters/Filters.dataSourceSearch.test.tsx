// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MyDataSource, MyLayerInfo } from "@opencop/api";
import { beforeEach, describe, expect, it } from "vitest";
import { render, screen, userEvent } from "~/test";
import { dataSourceQueries } from "~/features/data-sources/data-sources.queries";
import { layerGroupQueries } from "~/features/layer-groups/layer-groups.queries";
import { layerQueries } from "~/features/layers/layers.queries";
import { Filters } from "./Filters";
import { useFilterPanelStore } from "./filter-panel.store";

const layer = (layerId: string, layerName: string): MyLayerInfo => ({
    layerId,
    layerName,
    role: "OWNER",
    accessScope: "PRIVATE",
    defaultVisible: true,
});

const dataSource = (serverId: string, serverName: string): MyDataSource => ({
    serverId,
    serverName,
    serverState: "CONNECTED",
    role: "OWNER",
    directRole: "OWNER",
    via: "DIRECT",
    access: "RW",
    transportType: "TCP",
    defaultVisible: true,
});

const queryData = {
    [JSON.stringify(layerQueries.myList().queryKey)]: [layer("l1", "Alpha"), layer("l2", "Beta")],
    [JSON.stringify(layerGroupQueries.myList().queryKey)]: [],
    [JSON.stringify(dataSourceQueries.myList().queryKey)]: [
        dataSource("d1", "Sample source"),
        dataSource("d2", "Other"),
    ],
    [JSON.stringify(layerQueries.dataSources("l1").queryKey)]: [
        { serverId: "d1", serverName: "Sample source" },
        { serverId: "d2", serverName: "Other" },
    ],
    [JSON.stringify(layerQueries.dataSources("l2").queryKey)]: [
        { serverId: "d2", serverName: "Other" },
    ],
};

beforeEach(() => {
    useFilterPanelStore.setState(useFilterPanelStore.getInitialState(), true);
});

describe("Filters — data sources section default", () => {
    it("starts collapsed until the user opens it, then remembers the choice", async () => {
        const user = userEvent.setup();
        render(<Filters />, { queryData });

        const toggle = screen.getByRole("button", { name: /toggle accordion data sources/i });
        expect(toggle).toHaveAttribute("aria-expanded", "false");

        await user.click(toggle);
        expect(useFilterPanelStore.getState().expandedSections["data-sources"]).toBe(true);
    });
});

describe("Filters — searching by data source name", () => {
    it("lists layers containing the data source, expanded to just the matches", async () => {
        const user = userEvent.setup();
        render(<Filters />, { queryData });

        await user.type(
            screen.getByPlaceholderText("Search layers and data sources"),
            "sample source",
        );

        expect(await screen.findByRole("group", { name: "Alpha" })).toBeInTheDocument();
        expect(screen.queryByRole("group", { name: "Beta" })).not.toBeInTheDocument();
        expect(await screen.findAllByRole("group", { name: "Sample source" })).not.toHaveLength(0);
        expect(screen.queryByRole("group", { name: "Other" })).not.toBeInTheDocument();
    });
});
