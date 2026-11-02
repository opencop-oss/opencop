// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, userEvent } from "~/test";
import type { MyLayerInfo } from "@opencop/api";
import { layerQueries } from "./layers.queries";
import { useFilterPanelStore } from "~/features/filters/filter-panel.store";
import { LayerComboBox } from "./LayerComboBox";

function makeLayer(id: string, name: string): MyLayerInfo {
    return {
        layerId: id,
        layerName: name,
        role: "OWNER",
        accessScope: "PRIVATE",
        defaultVisible: true,
    };
}

const LAYERS: MyLayerInfo[] = [
    makeLayer("alpha", "Alpha Layer"),
    makeLayer("bravo", "Bravo Layer"),
    makeLayer("charlie", "Charlie Layer"),
];

/** Seed the React Query cache with the layers list so no network call fires. */
function layersQueryData() {
    return { [JSON.stringify(layerQueries.myList().queryKey)]: LAYERS };
}

async function openDropdown() {
    await userEvent.click(screen.getByRole("combobox"));
}

describe("LayerComboBox — initial render", () => {
    it("shows all layers under All Layers when there are no recents", async () => {
        render(<LayerComboBox value="" onValueChange={vi.fn()} storageKey="test:initial" />, {
            queryData: layersQueryData(),
        });

        await openDropdown();

        const options = screen.getAllByRole("option");
        expect(options.map((o) => o.textContent?.trim())).toEqual([
            "Alpha Layer",
            "Bravo Layer",
            "Charlie Layer",
        ]);
    });

    it("applies the filter prop to exclude layers", async () => {
        render(
            <LayerComboBox
                value=""
                onValueChange={vi.fn()}
                storageKey="test:filter"
                filter={(l) => l.layerId !== "bravo"}
            />,
            { queryData: layersQueryData() },
        );

        await openDropdown();

        const labels = screen.getAllByRole("option").map((o) => o.textContent?.trim());
        expect(labels).not.toContain("Bravo Layer");
        expect(labels).toContain("Alpha Layer");
        expect(labels).toContain("Charlie Layer");
    });
});

describe("LayerComboBox — recents ordering", () => {
    it("surfaces recent layers at the top in recency order, not alphabetical", async () => {
        // Render once to record bravo then charlie as recents
        const { unmount } = render(
            <LayerComboBox value="" onValueChange={vi.fn()} storageKey="test:ordering" />,
            { queryData: layersQueryData() },
        );
        await openDropdown();
        await userEvent.click(screen.getByRole("option", { name: "Bravo Layer" }));
        unmount();

        const { unmount: unmount2 } = render(
            <LayerComboBox value="" onValueChange={vi.fn()} storageKey="test:ordering" />,
            { queryData: layersQueryData() },
        );
        await openDropdown();
        await userEvent.click(screen.getByRole("option", { name: "Charlie Layer" }));
        unmount2();

        // Third render — Charlie should be first (most recent), then Bravo
        render(<LayerComboBox value="" onValueChange={vi.fn()} storageKey="test:ordering" />, {
            queryData: layersQueryData(),
        });
        await openDropdown();

        const labels = screen.getAllByRole("option").map((o) => o.textContent?.trim());
        expect(labels).toEqual(["Charlie Layer", "Bravo Layer", "Alpha Layer"]);
    });

    it("skips stale recents whose layer is no longer in the available list", async () => {
        // Pre-seed localStorage as if "deleted" was previously used
        localStorage.setItem(
            "test:stale",
            JSON.stringify({
                state: {
                    recentItems: [
                        { label: "Deleted Layer", value: "deleted" },
                        { label: "Alpha Layer", value: "alpha" },
                    ],
                },
            }),
        );

        render(<LayerComboBox value="" onValueChange={vi.fn()} storageKey="test:stale" />, {
            queryData: layersQueryData(),
        });
        await openDropdown();

        const labels = screen.getAllByRole("option").map((o) => o.textContent?.trim());
        expect(labels).not.toContain("Deleted Layer");
        expect(labels[0]).toBe("Alpha Layer");
    });

    it("two instances with different recents keys maintain independent histories", async () => {
        const { unmount: unmountA } = render(
            <LayerComboBox value="" onValueChange={vi.fn()} storageKey="test:key-a" />,
            { queryData: layersQueryData() },
        );
        await openDropdown();
        await userEvent.click(screen.getByRole("option", { name: "Bravo Layer" }));
        unmountA();

        // key-b has its own localStorage bucket — no recents, so layers render in plain list order
        render(<LayerComboBox value="" onValueChange={vi.fn()} storageKey="test:key-b" />, {
            queryData: layersQueryData(),
        });
        await openDropdown();

        expect(screen.getAllByRole("option").map((o) => o.textContent?.trim())).toEqual([
            "Alpha Layer",
            "Bravo Layer",
            "Charlie Layer",
        ]);
    });
});

describe("LayerComboBox — recording selections", () => {
    it("calls onValueChange with the selected layer id", async () => {
        const onValueChange = vi.fn();
        render(
            <LayerComboBox value="" onValueChange={onValueChange} storageKey="test:callback" />,
            {
                queryData: layersQueryData(),
            },
        );

        await openDropdown();
        await userEvent.click(screen.getByRole("option", { name: "Bravo Layer" }));

        expect(onValueChange).toHaveBeenCalledWith("bravo");
    });
});

describe("LayerComboBox — disabled", () => {
    it("disables the combobox trigger when disabled prop is true", () => {
        render(
            <LayerComboBox value="" onValueChange={vi.fn()} storageKey="test:disabled" disabled />,
            {
                queryData: layersQueryData(),
            },
        );
        expect(screen.getByRole("combobox")).toBeDisabled();
    });
});

describe("LayerComboBox — renderItem", () => {
    it("renders custom item content via renderItem", async () => {
        render(
            <LayerComboBox
                value=""
                onValueChange={vi.fn()}
                storageKey="test:render-item"
                renderItem={(item) => <span>{item.label} (custom)</span>}
            />,
            { queryData: layersQueryData() },
        );

        await openDropdown();

        expect(screen.getAllByText(/\(custom\)/)).toHaveLength(3);
    });
});

describe("LayerComboBox — panel exclusion", () => {
    afterEach(() => useFilterPanelStore.setState({ excludedIds: [] }));

    it("does not offer layers the user removed from the map panel", async () => {
        useFilterPanelStore.setState({ excludedIds: ["bravo"] });
        render(<LayerComboBox value="" onValueChange={vi.fn()} storageKey="test:excluded" />, {
            queryData: layersQueryData(),
        });

        await openDropdown();

        const labels = screen.getAllByRole("option").map((o) => o.textContent?.trim());
        expect(labels).toEqual(["Alpha Layer", "Charlie Layer"]);
    });

    it("still offers an excluded layer that is the current value", async () => {
        useFilterPanelStore.setState({ excludedIds: ["bravo"] });
        render(
            <LayerComboBox
                value="bravo"
                onValueChange={vi.fn()}
                storageKey="test:excluded-current"
            />,
            { queryData: layersQueryData() },
        );

        await openDropdown();

        const labels = screen.getAllByRole("option").map((o) => o.textContent?.trim());
        expect(labels).toContain("Bravo Layer");
    });
});
