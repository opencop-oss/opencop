// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { useImageryOverlayStore } from "./imagery-overlay.store";

const DEFAULT_OVERLAY_OPACITY = 50;

function resetStore() {
    useImageryOverlayStore.setState({ overlays: [] });
}

beforeEach(resetStore);
afterEach(resetStore);

describe("toggleOverlay", () => {
    it("adds a tile with default opacity and visible=true when not yet active", () => {
        useImageryOverlayStore.getState().actions.toggleOverlay("tile-a");
        expect(useImageryOverlayStore.getState().overlays).toEqual([
            { id: "tile-a", opacity: DEFAULT_OVERLAY_OPACITY, time: null, visible: true },
        ]);
    });

    it("removes a tile that is already active", () => {
        useImageryOverlayStore.setState({
            overlays: [{ id: "tile-a", opacity: 60, time: null, visible: true }],
        });
        useImageryOverlayStore.getState().actions.toggleOverlay("tile-a");
        expect(useImageryOverlayStore.getState().overlays).toEqual([]);
    });

    it("appends to the end of the overlays list", () => {
        useImageryOverlayStore.setState({
            overlays: [{ id: "tile-a", opacity: 50, time: null, visible: true }],
        });
        useImageryOverlayStore.getState().actions.toggleOverlay("tile-b");
        expect(useImageryOverlayStore.getState().overlays.map((o) => o.id)).toEqual([
            "tile-a",
            "tile-b",
        ]);
    });

    it("preserves existing opacity when removing one of multiple overlays", () => {
        useImageryOverlayStore.setState({
            overlays: [
                { id: "tile-a", opacity: 30, time: null, visible: true },
                { id: "tile-b", opacity: 80, time: null, visible: true },
            ],
        });
        useImageryOverlayStore.getState().actions.toggleOverlay("tile-a");
        expect(useImageryOverlayStore.getState().overlays).toEqual([
            { id: "tile-b", opacity: 80, time: null, visible: true },
        ]);
    });
});

describe("setOverlayVisible", () => {
    it("hides an active overlay without removing it from the store", () => {
        useImageryOverlayStore.setState({
            overlays: [{ id: "tile-a", opacity: 50, time: null, visible: true }],
        });
        useImageryOverlayStore.getState().actions.setOverlayVisible("tile-a", false);
        expect(useImageryOverlayStore.getState().overlays).toEqual([
            { id: "tile-a", opacity: 50, time: null, visible: false },
        ]);
    });

    it("re-shows a hidden overlay", () => {
        useImageryOverlayStore.setState({
            overlays: [{ id: "tile-a", opacity: 50, time: null, visible: false }],
        });
        useImageryOverlayStore.getState().actions.setOverlayVisible("tile-a", true);
        expect(useImageryOverlayStore.getState().overlays[0]?.visible).toBe(true);
    });

    it("does not affect other entries", () => {
        useImageryOverlayStore.setState({
            overlays: [
                { id: "tile-a", opacity: 50, time: null, visible: true },
                { id: "tile-b", opacity: 80, time: null, visible: true },
            ],
        });
        useImageryOverlayStore.getState().actions.setOverlayVisible("tile-a", false);
        expect(
            useImageryOverlayStore.getState().overlays.find((o) => o.id === "tile-b")?.visible,
        ).toBe(true);
    });
});

describe("setOverlayOpacity", () => {
    it("updates opacity for the target tile", () => {
        useImageryOverlayStore.setState({
            overlays: [{ id: "tile-a", opacity: 50, time: null, visible: true }],
        });
        useImageryOverlayStore.getState().actions.setOverlayOpacity("tile-a", 75);
        expect(useImageryOverlayStore.getState().overlays[0]?.opacity).toBe(75);
    });

    it("does not affect other entries", () => {
        useImageryOverlayStore.setState({
            overlays: [
                { id: "tile-a", opacity: 50, time: null, visible: true },
                { id: "tile-b", opacity: 80, time: null, visible: true },
            ],
        });
        useImageryOverlayStore.getState().actions.setOverlayOpacity("tile-a", 10);
        expect(
            useImageryOverlayStore.getState().overlays.find((o) => o.id === "tile-b")?.opacity,
        ).toBe(80);
    });
});

describe("reorderOverlays", () => {
    it("reorders active overlays to match the given candidate ID order", () => {
        useImageryOverlayStore.setState({
            overlays: [
                { id: "tile-a", opacity: 50, time: null, visible: true },
                { id: "tile-b", opacity: 60, time: null, visible: true },
                { id: "tile-c", opacity: 70, time: null, visible: true },
            ],
        });
        useImageryOverlayStore.getState().actions.reorderOverlays(["tile-c", "tile-a", "tile-b"]);
        expect(useImageryOverlayStore.getState().overlays.map((o) => o.id)).toEqual([
            "tile-c",
            "tile-a",
            "tile-b",
        ]);
    });

    it("ignores inactive (non-store) IDs in the candidate list", () => {
        useImageryOverlayStore.setState({
            overlays: [
                { id: "tile-a", opacity: 50, time: null, visible: true },
                { id: "tile-b", opacity: 60, time: null, visible: true },
            ],
        });
        useImageryOverlayStore.getState().actions.reorderOverlays(["tile-c", "tile-b", "tile-a"]);
        expect(useImageryOverlayStore.getState().overlays.map((o) => o.id)).toEqual([
            "tile-b",
            "tile-a",
        ]);
    });

    it("preserves overlay data (opacity, time, visible) when reordering", () => {
        useImageryOverlayStore.setState({
            overlays: [
                { id: "tile-a", opacity: 30, time: "2024-01-01T00:00:00.000Z", visible: false },
                { id: "tile-b", opacity: 80, time: null, visible: true },
            ],
        });
        useImageryOverlayStore.getState().actions.reorderOverlays(["tile-b", "tile-a"]);
        const overlays = useImageryOverlayStore.getState().overlays;
        expect(overlays[0]).toEqual({ id: "tile-b", opacity: 80, time: null, visible: true });
        expect(overlays[1]).toEqual({
            id: "tile-a",
            opacity: 30,
            time: "2024-01-01T00:00:00.000Z",
            visible: false,
        });
    });

    it("does not mutate state when order is already correct", () => {
        useImageryOverlayStore.setState({
            overlays: [
                { id: "tile-a", opacity: 50, time: null, visible: true },
                { id: "tile-b", opacity: 60, time: null, visible: true },
            ],
        });
        const before = useImageryOverlayStore.getState().overlays;
        useImageryOverlayStore.getState().actions.reorderOverlays(["tile-a", "tile-b"]);
        expect(useImageryOverlayStore.getState().overlays).toBe(before);
    });

    it("is a no-op when there are no active overlays", () => {
        const before = useImageryOverlayStore.getState().overlays;
        useImageryOverlayStore.getState().actions.reorderOverlays(["tile-a", "tile-b"]);
        expect(useImageryOverlayStore.getState().overlays).toBe(before);
    });
});

describe("reconcile", () => {
    it("removes entries for tile IDs that are no longer valid", () => {
        useImageryOverlayStore.setState({
            overlays: [
                { id: "tile-a", opacity: 50, time: null, visible: true },
                { id: "tile-b", opacity: 60, time: null, visible: true },
            ],
        });
        useImageryOverlayStore.getState().actions.reconcile(["tile-a"]);
        expect(useImageryOverlayStore.getState().overlays).toEqual([
            { id: "tile-a", opacity: 50, time: null, visible: true },
        ]);
    });

    it("does not mutate state when nothing is pruned", () => {
        useImageryOverlayStore.setState({
            overlays: [{ id: "tile-a", opacity: 50, time: null, visible: true }],
        });
        const before = useImageryOverlayStore.getState().overlays;
        useImageryOverlayStore.getState().actions.reconcile(["tile-a"]);
        expect(useImageryOverlayStore.getState().overlays).toBe(before);
    });

    it("clears all entries when given an empty tile list", () => {
        useImageryOverlayStore.setState({
            overlays: [{ id: "tile-a", opacity: 50, time: null, visible: true }],
        });
        useImageryOverlayStore.getState().actions.reconcile([]);
        expect(useImageryOverlayStore.getState().overlays).toEqual([]);
    });

    it("does not add tiles that are not already active", () => {
        useImageryOverlayStore.getState().actions.reconcile(["tile-a", "tile-b"]);
        expect(useImageryOverlayStore.getState().overlays).toEqual([]);
    });
});
