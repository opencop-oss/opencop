// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MyDataSource, MyLayerInfo } from "@opencop/api";
import { beforeEach, describe, expect, it } from "vitest";
import { render, screen } from "~/test";
import { dataSourceQueries } from "~/features/data-sources/data-sources.queries";
import { layerGroupQueries } from "~/features/layer-groups/layer-groups.queries";
import type { MyLayerGroup } from "~/features/layer-groups/layer-groups.queries";
import { layerQueries } from "~/features/layers/layers.queries";
import { Filters } from "./Filters";
import { useFilterPanelStore } from "./filter-panel.store";

function makeLayer(id: string, name: string): MyLayerInfo {
    return {
        layerId: id,
        layerName: name,
        role: "OWNER",
        accessScope: "PRIVATE",
        defaultVisible: true,
    };
}

function makeSource(id: string, name: string): MyDataSource {
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

function makeGroup(id: string, name: string, layers: MyLayerInfo[]): MyLayerGroup {
    return {
        id,
        name,
        description: null,
        containerRole: "OWNER",
        accessScope: "PRIVATE",
        createdAt: "2026-01-01T00:00:00Z",
        layers: layers.map((l) => ({
            id: l.layerId,
            name: l.layerName,
            accessScope: l.accessScope,
            role: l.role,
        })),
    };
}

const KEPT = makeLayer("kept", "Kept Layer");
const REMOVED = makeLayer("removed", "Removed Layer");
const GROUPED_KEPT = makeLayer("g-kept", "Grouped Kept");
const GROUPED_REMOVED = makeLayer("g-removed", "Grouped Removed");

function renderFilters(groups: MyLayerGroup[], layers: MyLayerInfo[]) {
    return render(<Filters />, {
        queryData: {
            [JSON.stringify(layerQueries.myList().queryKey)]: layers,
            [JSON.stringify(layerGroupQueries.myList().queryKey)]: groups,
            ...Object.fromEntries(
                [...layers, ...groups.flatMap((g) => g.layers)].map((l) => [
                    JSON.stringify(
                        layerQueries.dataSources("layerId" in l ? l.layerId : l.id).queryKey,
                    ),
                    [],
                ]),
            ),
            [JSON.stringify(dataSourceQueries.myList().queryKey)]: [
                makeSource("src-kept", "Kept Source"),
                makeSource("src-removed", "Removed Source"),
            ],
        },
    });
}

describe("Filters — panel exclusion", () => {
    beforeEach(() => {
        useFilterPanelStore.setState(useFilterPanelStore.getInitialState(), true);
        useFilterPanelStore.setState({
            excludedIds: [],
            expandedSections: { "data-sources": true },
        });
    });

    it("omits excluded layers and data sources", () => {
        useFilterPanelStore.setState({ excludedIds: ["removed", "src-removed"] });
        renderFilters([], [KEPT, REMOVED]);

        expect(screen.getByText("Kept Layer")).toBeInTheDocument();
        expect(screen.getByText("Kept Source")).toBeInTheDocument();
        expect(screen.queryByText("Removed Layer")).not.toBeInTheDocument();
        expect(screen.queryByText("Removed Source")).not.toBeInTheDocument();
    });

    it("shows everything when nothing is excluded", () => {
        renderFilters([], [KEPT, REMOVED]);

        expect(screen.getByText("Removed Layer")).toBeInTheDocument();
        expect(screen.getByText("Removed Source")).toBeInTheDocument();
    });

    it("drops an excluded layer from its group's member count", () => {
        useFilterPanelStore.setState({ excludedIds: ["g-removed"] });
        renderFilters(
            [makeGroup("grp", "Mixed Group", [GROUPED_KEPT, GROUPED_REMOVED])],
            [GROUPED_KEPT, GROUPED_REMOVED],
        );

        expect(screen.getByText("Mixed Group")).toBeInTheDocument();
        expect(screen.getByText("(1)")).toBeInTheDocument();
    });

    it("drops a group whose layers are all excluded", () => {
        useFilterPanelStore.setState({ excludedIds: ["g-kept", "g-removed"] });
        renderFilters(
            [makeGroup("grp", "Emptied Group", [GROUPED_KEPT, GROUPED_REMOVED])],
            [GROUPED_KEPT, GROUPED_REMOVED],
        );

        expect(screen.queryByText("Emptied Group")).not.toBeInTheDocument();
    });

    it("keeps a group that never had layers", () => {
        renderFilters([makeGroup("grp", "Empty From Start", [])], []);

        expect(screen.getByText("Empty From Start")).toBeInTheDocument();
    });
});
