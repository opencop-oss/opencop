// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, userEvent, waitFor } from "~/test";
import { layerQueries } from "~/features/layers/layers.queries";
import type { MyLayerGroup } from "~/features/layer-groups/layer-groups.queries";
import { useFilterPanelStore } from "./filter-panel.store";
import { LayerGroupFilter } from "./LayerGroupFilter";

vi.mock("@opencop/ingest", () => ({
    useSourceState: vi.fn().mockReturnValue("idle"),
    useSourceCount: vi.fn().mockReturnValue(0),
    useElementActions: vi.fn().mockReturnValue({ hideSource: vi.fn(), showSource: vi.fn() }),
    useSourceActions: vi.fn().mockReturnValue({ enable: vi.fn(), disable: vi.fn() }),
    useSources: vi.fn().mockReturnValue({}),
}));

const GROUP = {
    id: "g1",
    name: "Group",
    description: null,
    containerRole: "OWNER",
    accessScope: "PRIVATE",
    createdAt: "2026-01-01T00:00:00Z",
    layers: [],
} satisfies MyLayerGroup;
const LAYERS = [{ layerId: "layer-1", layerName: "Layer 1" }];

const QUERY_DATA = {
    [JSON.stringify(layerQueries.dataSources("layer-1").queryKey)]: [
        { serverId: "ds-1", serverName: "DS 1" },
    ],
};

function renderGroup() {
    return render(<LayerGroupFilter group={GROUP} layers={LAYERS} />, { queryData: QUERY_DATA });
}

describe("LayerGroupFilter — visibility cascade", () => {
    beforeEach(() => {
        useFilterPanelStore.setState(useFilterPanelStore.getInitialState(), true);
    });

    it("hides member layers and their data sources, and shows them again", async () => {
        const user = userEvent.setup();
        renderGroup();

        await user.click(screen.getByRole("button", { name: /hide layer group/i }));
        await waitFor(() =>
            expect(useFilterPanelStore.getState().hiddenSources).toEqual(
                expect.arrayContaining(["layer-1", "ds-1"]),
            ),
        );

        await user.click(screen.getByRole("button", { name: /show layer group/i }));
        await waitFor(() => expect(useFilterPanelStore.getState().hiddenSources).toEqual([]));
    });

    it("expands when the search starts matching its layers after mount", () => {
        const { rerender } = renderGroup();
        expect(screen.queryByRole("group", { name: "Layer 1" })).not.toBeInTheDocument();

        rerender(<LayerGroupFilter group={GROUP} layers={LAYERS} defaultExpanded />);

        expect(screen.getByRole("group", { name: "Layer 1" })).toBeInTheDocument();
    });

    it("toggles every member layer even when only some are displayed", async () => {
        const user = userEvent.setup();
        render(
            <LayerGroupFilter
                group={GROUP}
                layers={LAYERS}
                toggleLayerIds={["layer-1", "layer-hidden-by-search"]}
            />,
            {
                queryData: {
                    ...QUERY_DATA,
                    [JSON.stringify(layerQueries.dataSources("layer-hidden-by-search").queryKey)]:
                        [],
                },
            },
        );

        await user.click(screen.getByRole("button", { name: /hide layer group/i }));

        await waitFor(() =>
            expect(useFilterPanelStore.getState().hiddenSources).toEqual(
                expect.arrayContaining(["layer-1", "layer-hidden-by-search"]),
            ),
        );
    });
});
