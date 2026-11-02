// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { beforeEach, describe, expect, it } from "vitest";

import { isRangeOverLimit, MIN_ZOOM_RANGE_MS, useHistoricalStore } from "./historical.store";

// The store is a module-level singleton with `persist` middleware. Reset it to
// a known shape before each test so cases don't leak state through each other.
function resetStore() {
    useHistoricalStore.setState({
        mode: "historical",
        sourceIds: ["src-A"],
        from: "2026-01-01T00:00:00.000Z",
        to: "2026-01-02T00:00:00.000Z",
        rangeMode: "24h",
        intervalMode: "auto",
        activated: true,
        zoomStack: [],
        playheadPosition: null,
        panelHeight: 100,
        retryNonce: 0,
        dismissedSignature: null,
        showActiveEvents: true,
        showActiveEventUpdates: false,
        settingsOpen: false,
    });
}

beforeEach(() => {
    resetStore();
});

describe("pushZoom min-zoom guard", () => {
    it("refuses a brush narrower than MIN_ZOOM_RANGE_MS (15 min)", () => {
        const before = useHistoricalStore.getState();
        const tooNarrowFrom = "2026-01-01T12:00:00.000Z";
        const tooNarrowTo = "2026-01-01T12:10:00.000Z"; // 10 min < 15 min floor

        useHistoricalStore.getState().actions.pushZoom(tooNarrowFrom, tooNarrowTo);

        const after = useHistoricalStore.getState();
        expect(after.from).toBe(before.from);
        expect(after.to).toBe(before.to);
        expect(after.zoomStack).toEqual([]);
    });

    it("accepts a brush at exactly the floor", () => {
        const fromMs = Date.parse("2026-01-01T12:00:00.000Z");
        const newFrom = new Date(fromMs).toISOString();
        const newTo = new Date(fromMs + MIN_ZOOM_RANGE_MS).toISOString();

        useHistoricalStore.getState().actions.pushZoom(newFrom, newTo);

        const after = useHistoricalStore.getState();
        expect(after.from).toBe(newFrom);
        expect(after.to).toBe(newTo);
        expect(after.zoomStack).toHaveLength(1);
    });

    it("pushes the previous range onto the zoom stack and switches rangeMode to custom", () => {
        const before = useHistoricalStore.getState();
        const newFrom = "2026-01-01T06:00:00.000Z";
        const newTo = "2026-01-01T12:00:00.000Z"; // 6h, well above floor

        useHistoricalStore.getState().actions.pushZoom(newFrom, newTo);

        const after = useHistoricalStore.getState();
        expect(after.zoomStack).toEqual([
            { from: before.from, to: before.to, rangeMode: before.rangeMode },
        ]);
        expect(after.rangeMode).toBe("custom");
        expect(after.playheadPosition).toBeNull();
    });

    it("does nothing when current from/to are null", () => {
        useHistoricalStore.setState({ from: null, to: null });

        useHistoricalStore
            .getState()
            .actions.pushZoom("2026-01-01T06:00:00.000Z", "2026-01-01T12:00:00.000Z");

        expect(useHistoricalStore.getState().zoomStack).toEqual([]);
    });
});

describe("source selection (multi-source)", () => {
    it("setSourceIds from an inactive view sets the set and leaves activation off", () => {
        useHistoricalStore.setState({
            activated: false,
            zoomStack: [{ from: "a", to: "b", rangeMode: "24h" }],
            playheadPosition: "2026-01-01T01:00:00.000Z",
        });

        useHistoricalStore.getState().actions.setSourceIds(["src-A", "src-B"], {});

        const after = useHistoricalStore.getState();
        expect(after.sourceIds).toEqual(["src-A", "src-B"]);
        expect(after.activated).toBe(false);
        expect(after.zoomStack).toEqual([]);
        expect(after.playheadPosition).toBeNull();
    });

    it("keeps activation/zoom/playhead when a source is removed from an active view", () => {
        useHistoricalStore.setState({
            sourceIds: ["src-A", "src-B"],
            activated: true,
            zoomStack: [{ from: "a", to: "b", rangeMode: "24h" }],
            playheadPosition: "2026-01-01T01:00:00.000Z",
        });

        useHistoricalStore.getState().actions.setSourceIds(["src-A"], {});

        const after = useHistoricalStore.getState();
        expect(after.sourceIds).toEqual(["src-A"]);
        expect(after.activated).toBe(true);
        expect(after.zoomStack).toHaveLength(1);
        expect(after.playheadPosition).toBe("2026-01-01T01:00:00.000Z");
    });

    it("keeps activation/zoom/playhead when a source is added to an active view", () => {
        // Adding to an active view reconciles in place (incremental add or a
        // full re-submit), so the store keeps the view activated — the provider
        // streams the new source in rather than forcing a re-activate.
        useHistoricalStore.setState({
            sourceIds: ["src-A"],
            activated: true,
            zoomStack: [{ from: "a", to: "b", rangeMode: "24h" }],
            playheadPosition: "2026-01-01T01:00:00.000Z",
        });

        useHistoricalStore.getState().actions.setSourceIds(["src-A", "src-B"], {});

        const after = useHistoricalStore.getState();
        expect(after.sourceIds).toEqual(["src-A", "src-B"]);
        expect(after.activated).toBe(true);
        expect(after.zoomStack).toHaveLength(1);
        expect(after.playheadPosition).toBe("2026-01-01T01:00:00.000Z");
    });

    it("removing the last source deactivates", () => {
        useHistoricalStore.setState({ sourceIds: ["src-A"], activated: true });
        useHistoricalStore.getState().actions.setSourceIds([], {});
        const after = useHistoricalStore.getState();
        expect(after.sourceIds).toEqual([]);
        expect(after.activated).toBe(false);
    });
});

describe("retry clears dismissedSignature", () => {
    it("bumps retryNonce and clears dismissedSignature atomically", () => {
        useHistoricalStore.setState({ dismissedSignature: "heartbeatTimeout", retryNonce: 7 });

        useHistoricalStore.getState().actions.retry();

        const after = useHistoricalStore.getState();
        expect(after.retryNonce).toBe(8);
        expect(after.dismissedSignature).toBeNull();
    });

    it("retry from a clean state still bumps retryNonce and leaves dismissedSignature null", () => {
        useHistoricalStore.getState().actions.retry();

        const after = useHistoricalStore.getState();
        expect(after.retryNonce).toBe(1);
        expect(after.dismissedSignature).toBeNull();
    });
});

describe("dismissSignature", () => {
    it("stores the signature so subsequent reads can compare", () => {
        useHistoricalStore.getState().actions.dismissSignature("missingParts:3");
        expect(useHistoricalStore.getState().dismissedSignature).toBe("missingParts:3");
    });

    it("overwrites a prior dismissal", () => {
        useHistoricalStore.getState().actions.dismissSignature("missingParts:3");
        useHistoricalStore.getState().actions.dismissSignature("missingParts:5");
        expect(useHistoricalStore.getState().dismissedSignature).toBe("missingParts:5");
    });
});

describe("isRangeOverLimit", () => {
    it("returns false for null or empty inputs", () => {
        expect(isRangeOverLimit(null, null)).toBe(false);
        expect(isRangeOverLimit(null, "2026-05-20T00:00:00.000Z")).toBe(false);
        expect(isRangeOverLimit("2026-05-20T00:00:00.000Z", null)).toBe(false);
        expect(isRangeOverLimit("", "")).toBe(false);
    });

    it("returns false for invalid ISO strings", () => {
        expect(isRangeOverLimit("not-a-date", "2026-05-20T00:00:00.000Z")).toBe(false);
        expect(isRangeOverLimit("2026-05-20T00:00:00.000Z", "garbage")).toBe(false);
    });

    it("returns false when span equals exactly 30 days", () => {
        expect(isRangeOverLimit("2026-04-20T00:00:00.000Z", "2026-05-20T00:00:00.000Z")).toBe(
            false,
        );
    });

    it("returns true when span exceeds 30 days by even 1 ms", () => {
        expect(isRangeOverLimit("2026-04-20T00:00:00.000Z", "2026-05-20T00:00:00.001Z")).toBe(true);
    });

    it("returns true for a 31-day span", () => {
        expect(isRangeOverLimit("2026-04-19T00:00:00.000Z", "2026-05-20T00:00:00.000Z")).toBe(true);
    });

    it("returns false when end is before start (negative span)", () => {
        expect(isRangeOverLimit("2026-05-20T00:00:00.000Z", "2026-04-20T00:00:00.000Z")).toBe(
            false,
        );
    });
});
