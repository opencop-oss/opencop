// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { useFilterPanelStore } from "./filter-panel.store";

const STORAGE_KEY = "opencop:filter-panel";

function resetStore() {
    useFilterPanelStore.setState(useFilterPanelStore.getInitialState(), true);
    localStorage.removeItem(STORAGE_KEY);
}

beforeEach(resetStore);
afterEach(resetStore);

describe("filter panel persistence", () => {
    it("starts with no sources opted in to text", () => {
        expect(useFilterPanelStore.getState().textVisibleSources).toEqual([]);
    });

    it("records discovered sources without opting their text in", () => {
        useFilterPanelStore.getState().initNewSources([
            { id: "default-visible", defaultVisible: true },
            { id: "default-hidden", defaultVisible: false },
        ]);

        expect(useFilterPanelStore.getState().knownSources).toEqual([
            "default-visible",
            "default-hidden",
        ]);
        expect(useFilterPanelStore.getState().textVisibleSources).toEqual([]);
    });

    it("forgets a stale source's text choice before rediscovery", () => {
        useFilterPanelStore.setState({
            knownSources: ["stale", "kept"],
            textVisibleSources: ["stale", "kept"],
        });

        useFilterPanelStore.getState().removeKnownSources(["stale"]);
        useFilterPanelStore.getState().initNewSources([{ id: "stale", defaultVisible: true }]);

        expect(useFilterPanelStore.getState().knownSources).toEqual(["kept", "stale"]);
        expect(useFilterPanelStore.getState().textVisibleSources).toEqual(["kept"]);
    });
});

describe("panel exclusion list", () => {
    it("adds ids once and removes them on restore", () => {
        const { setExcludedIds } = useFilterPanelStore.getState();
        setExcludedIds(["a", "b"], true);
        setExcludedIds(["b", "c"], true);
        expect(useFilterPanelStore.getState().excludedIds).toEqual(["a", "b", "c"]);

        setExcludedIds(["b"], false);
        expect(useFilterPanelStore.getState().excludedIds).toEqual(["a", "c"]);
    });

    it("keeps state identity when nothing changes", () => {
        const { setExcludedIds } = useFilterPanelStore.getState();
        setExcludedIds(["a"], true);
        const before = useFilterPanelStore.getState();

        setExcludedIds(["a"], true);
        setExcludedIds(["zzz"], false);

        expect(useFilterPanelStore.getState()).toBe(before);
    });

    it("drops a stale id from the exclusion list along with the other per-source state", () => {
        useFilterPanelStore.setState({ knownSources: ["a", "b"], excludedIds: ["a", "b"] });

        useFilterPanelStore.getState().removeKnownSources(["a"]);

        expect(useFilterPanelStore.getState().excludedIds).toEqual(["b"]);
    });

    it("persists the exclusion list", () => {
        useFilterPanelStore.getState().setExcludedIds(["a"], true);

        const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "{}");
        expect(stored.state.excludedIds).toEqual(["a"]);
    });
});

describe("pinned sources", () => {
    it("toggles a pin on and off, keeping the others in order", () => {
        const { togglePinnedSource } = useFilterPanelStore.getState();
        togglePinnedSource("a");
        togglePinnedSource("b");
        togglePinnedSource("a");

        expect(useFilterPanelStore.getState().pinnedSources).toEqual(["b"]);
    });

    it("drops a pin when its source is forgotten", () => {
        useFilterPanelStore.setState({ knownSources: ["a", "b"], pinnedSources: ["a", "b"] });

        useFilterPanelStore.getState().removeKnownSources(["a"]);

        expect(useFilterPanelStore.getState().pinnedSources).toEqual(["b"]);
    });

    it("persists pins", () => {
        useFilterPanelStore.getState().togglePinnedSource("a");

        const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "{}");
        expect(stored.state.pinnedSources).toEqual(["a"]);
    });
});
