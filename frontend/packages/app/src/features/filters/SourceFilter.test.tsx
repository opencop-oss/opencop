// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, userEvent, within } from "~/test";
import { dataSourceQueries } from "~/features/data-sources/data-sources.queries";
import { useFilterPanelStore } from "./filter-panel.store";
import { SourceFilter } from "./SourceFilter";

vi.mock("@opencop/ingest", () => ({
    useSourceState: vi.fn().mockReturnValue("idle"),
    useSourceCount: vi.fn().mockReturnValue(3),
    useElementActions: vi.fn().mockReturnValue({
        hideSource: vi.fn(),
        showSource: vi.fn(),
        deleteBySource: vi.fn(),
    }),
    useSourceActions: vi.fn().mockReturnValue({ enable: vi.fn(), disable: vi.fn() }),
    useSources: vi.fn().mockReturnValue({}),
}));

const NESTED = vi.hoisted(() => ({ data: [] as unknown[], fail: false }));

vi.mock("~/features/layers/layers.queries", () => ({
    layerQueries: {
        dataSources: () => ({
            queryKey: ["layer-ds", "test"],
            queryFn: () => {
                if (NESTED.fail) throw new Error("lookup failed");
                return NESTED.data;
            },
        }),
    },
}));

const SOURCE_ID = "test-source";

function renderComponent() {
    return render(<SourceFilter sourceId={SOURCE_ID} sourceName="Test Layer" kind="layer" />, {
        queryData: {
            '["layer-ds","test"]': NESTED.data,
            [JSON.stringify(dataSourceQueries.myList().queryKey)]: [],
        },
    });
}

async function openMenu(user: ReturnType<typeof userEvent.setup>) {
    await user.click(screen.getByRole("button", { name: /actions for test layer/i }));
}

describe("SourceFilter — actions menu", () => {
    beforeEach(() => {
        useFilterPanelStore.setState(useFilterPanelStore.getInitialState(), true);
    });

    it("offers an enabled Show text action for an unconfigured source", async () => {
        const user = userEvent.setup();
        renderComponent();
        await openMenu(user);
        expect(screen.getByRole("menuitem", { name: /show text/i })).not.toHaveAttribute(
            "aria-disabled",
            "true",
        );
    });

    it("enabling text adds only the row source", async () => {
        useFilterPanelStore.setState({ textVisibleSources: ["other-source"] });
        const user = userEvent.setup();
        renderComponent();

        await openMenu(user);
        await user.click(screen.getByRole("menuitem", { name: /show text/i }));

        expect(useFilterPanelStore.getState().textVisibleSources).toEqual([
            "other-source",
            SOURCE_ID,
        ]);
    });

    it("disabling text removes only the row source", async () => {
        useFilterPanelStore.setState({ textVisibleSources: [SOURCE_ID, "other-source"] });
        const user = userEvent.setup();
        renderComponent();

        await openMenu(user);
        await user.click(screen.getByRole("menuitem", { name: /hide text/i }));

        expect(useFilterPanelStore.getState().textVisibleSources).toEqual(["other-source"]);
    });

    it("keeps Show text enabled when every managed source starts with text off", async () => {
        useFilterPanelStore.setState({
            knownSources: [SOURCE_ID, "other-source"],
            textVisibleSources: [],
        });
        const user = userEvent.setup();
        renderComponent();

        await openMenu(user);

        expect(screen.getByRole("menuitem", { name: /show text/i })).not.toHaveAttribute(
            "aria-disabled",
            "true",
        );
    });

    it("pinning adds the source; unpinning removes it", async () => {
        const user = userEvent.setup();
        renderComponent();

        await openMenu(user);
        await user.click(screen.getByRole("menuitem", { name: /^pin$/i }));
        expect(useFilterPanelStore.getState().pinnedSources).toEqual([SOURCE_ID]);

        await openMenu(user);
        await user.click(screen.getByRole("menuitem", { name: /unpin/i }));
        expect(useFilterPanelStore.getState().pinnedSources).toEqual([]);
    });
});

describe("SourceFilter — nested data sources", () => {
    beforeEach(() => {
        useFilterPanelStore.setState(useFilterPanelStore.getInitialState(), true);
        NESTED.data = [{ serverId: "ds-1", serverName: "Nested DS" }];
        NESTED.fail = false;
    });

    it("has no chevron when the layer has no data sources", () => {
        NESTED.data = [];
        renderComponent();
        expect(screen.queryByRole("button", { name: /data sources/i })).not.toBeInTheDocument();
    });

    it("reveals nested data sources when the chevron is clicked", async () => {
        const user = userEvent.setup();
        renderComponent();
        expect(screen.queryByRole("group", { name: "Nested DS" })).not.toBeInTheDocument();

        await user.click(await screen.findByRole("button", { name: /data sources/i }));

        expect(screen.getByRole("group", { name: "Nested DS" })).toBeInTheDocument();
    });

    it("toggling a nested data source updates the shared hidden state", async () => {
        const user = userEvent.setup();
        renderComponent();
        await user.click(await screen.findByRole("button", { name: /data sources/i }));

        const row = screen.getByRole("group", { name: "Nested DS" });
        await user.click(within(row).getByRole("button", { name: /hide data source/i }));

        expect(useFilterPanelStore.getState().hiddenSources).toContain("ds-1");
    });

    it("toggling the layer off hides nested data sources and toggling on shows them", async () => {
        const user = userEvent.setup();
        renderComponent();
        await screen.findByRole("button", { name: /data sources/i });

        await user.click(screen.getByRole("button", { name: /hide layer/i }));
        expect(useFilterPanelStore.getState().hiddenSources).toEqual(
            expect.arrayContaining([SOURCE_ID, "ds-1"]),
        );

        await user.click(screen.getByRole("button", { name: /show layer/i }));
        expect(useFilterPanelStore.getState().hiddenSources).toEqual([]);
    });

    it("leaves panel-excluded data sources out of the nested list", async () => {
        useFilterPanelStore.setState({ excludedIds: ["ds-1"] });
        renderComponent();
        expect(screen.queryByRole("button", { name: /data sources/i })).not.toBeInTheDocument();
    });

    it("still toggles the layer when its data source lookup fails", async () => {
        NESTED.fail = true;
        const user = userEvent.setup();
        render(<SourceFilter sourceId={SOURCE_ID} sourceName="Test Layer" kind="layer" />);

        await user.click(screen.getByRole("button", { name: /hide layer/i }));

        expect(useFilterPanelStore.getState().hiddenSources).toEqual([SOURCE_ID]);
    });
});
