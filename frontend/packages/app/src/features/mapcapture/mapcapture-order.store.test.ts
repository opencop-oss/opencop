// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { useMapCaptureOrderStore } from "./mapcapture-order.store";

function resetStore() {
    useMapCaptureOrderStore.setState({ order: [] });
}

beforeEach(resetStore);
afterEach(resetStore);

describe("reorderCaptures", () => {
    it("sets the new order", () => {
        useMapCaptureOrderStore.getState().actions.reorderCaptures(["b", "a"]);
        expect(useMapCaptureOrderStore.getState().order).toEqual(["b", "a"]);
    });

    it("does not mutate state when the order is unchanged", () => {
        useMapCaptureOrderStore.setState({ order: ["a", "b"] });
        const before = useMapCaptureOrderStore.getState().order;
        useMapCaptureOrderStore.getState().actions.reorderCaptures(["a", "b"]);
        expect(useMapCaptureOrderStore.getState().order).toBe(before);
    });
});

describe("reconcile", () => {
    it("removes ids that are no longer present in the live list", () => {
        useMapCaptureOrderStore.setState({ order: ["a", "b", "c"] });
        useMapCaptureOrderStore.getState().actions.reconcile(["a", "c"]);
        expect(useMapCaptureOrderStore.getState().order).toEqual(["a", "c"]);
    });

    it("preserves the relative order of surviving ids", () => {
        useMapCaptureOrderStore.setState({ order: ["c", "a", "b"] });
        useMapCaptureOrderStore.getState().actions.reconcile(["a", "b", "c"]);
        expect(useMapCaptureOrderStore.getState().order).toEqual(["c", "a", "b"]);
    });

    it("does not mutate state when all ids are still valid", () => {
        useMapCaptureOrderStore.setState({ order: ["a", "b"] });
        const before = useMapCaptureOrderStore.getState().order;
        useMapCaptureOrderStore.getState().actions.reconcile(["a", "b", "c"]);
        expect(useMapCaptureOrderStore.getState().order).toBe(before);
    });
});
