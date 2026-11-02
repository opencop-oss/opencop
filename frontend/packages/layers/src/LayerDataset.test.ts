// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { LayerDataset, swapPop } from "./LayerDataset";

type Item = { id: string; value: number };
const getId = (d: Item) => d.id;

describe("swapPop", () => {
    it("removes the last element with a pop", () => {
        const arr = [1, 2, 3];
        swapPop(arr, 2);
        expect(arr).toEqual([1, 2]);
    });

    it("swaps with the last element then pops for non-tail indices", () => {
        const arr = [1, 2, 3, 4];
        swapPop(arr, 1);
        expect(arr).toEqual([1, 4, 3]);
    });

    it("handles a single-element array", () => {
        const arr = [1];
        swapPop(arr, 0);
        expect(arr).toEqual([]);
    });
});

describe("LayerDataset.add", () => {
    it("appends new items and marks their indices dirty", () => {
        const ds = new LayerDataset<Item>(getId);
        ds.add({ id: "a", value: 1 });
        ds.add({ id: "b", value: 2 });

        const snap = ds.consume();
        expect(snap.data).toEqual([
            { id: "a", value: 1 },
            { id: "b", value: 2 },
        ]);
        expect(ds.getDirty()).toEqual(new Set([0, 1]));
    });

    it("replaces an existing object at the same index when re-added with a new reference", () => {
        const ds = new LayerDataset<Item>(getId);
        ds.add({ id: "a", value: 1 });
        ds.consume()._dataDiff!(); // drain dirty
        expect(ds.getDirty().size).toBe(0);

        ds.add({ id: "a", value: 2 });
        expect(ds.consume().data).toEqual([{ id: "a", value: 2 }]);
        expect(ds.getDirty()).toEqual(new Set([0]));
    });

    it("does not mark dirty when re-adding the exact same object reference", () => {
        const ds = new LayerDataset<Item>(getId);
        const item = { id: "a", value: 1 };
        ds.add(item);
        ds.consume()._dataDiff!(); // drain dirty

        ds.add(item);
        expect(ds.getDirty().size).toBe(0);
    });

    it("flips visible from false to true once data is present", () => {
        const ds = new LayerDataset<Item>(getId);
        expect(ds.consume().visible).toBe(false);
        ds.add({ id: "a", value: 1 });
        expect(ds.consume().visible).toBe(true);
    });
});

describe("LayerDataset.update", () => {
    it("updates an existing item and marks it dirty", () => {
        const ds = new LayerDataset<Item>(getId);
        ds.add({ id: "a", value: 1 });
        ds.consume()._dataDiff!();

        ds.update({ id: "a", value: 99 });
        expect(ds.consume().data).toEqual([{ id: "a", value: 99 }]);
        expect(ds.getDirty()).toEqual(new Set([0]));
    });

    it("is a no-op for unknown ids and does not append", () => {
        const ds = new LayerDataset<Item>(getId);
        ds.add({ id: "a", value: 1 });
        ds.consume()._dataDiff!();

        ds.update({ id: "ghost", value: 5 });
        expect(ds.consume().data).toEqual([{ id: "a", value: 1 }]);
        expect(ds.getDirty().size).toBe(0);
    });
});

describe("LayerDataset.remove", () => {
    it("removes a tail item and marks the tail index dirty", () => {
        const ds = new LayerDataset<Item>(getId);
        ds.add({ id: "a", value: 1 });
        ds.add({ id: "b", value: 2 });
        ds.consume()._dataDiff!();

        ds.remove({ id: "b", value: 2 });
        const snap = ds.consume();
        expect(snap.data).toEqual([{ id: "a", value: 1 }]);
        expect(ds.getDirty()).toEqual(new Set([1]));
    });

    it("swap-pops a non-tail item and marks both the swap and tail indices dirty", () => {
        const ds = new LayerDataset<Item>(getId);
        ds.add({ id: "a", value: 1 });
        ds.add({ id: "b", value: 2 });
        ds.add({ id: "c", value: 3 });
        ds.consume()._dataDiff!();

        ds.remove({ id: "a", value: 1 });

        const snap = ds.consume();
        expect(snap.data).toEqual([
            { id: "c", value: 3 },
            { id: "b", value: 2 },
        ]);
        expect(ds.getDirty()).toEqual(new Set([0, 2]));

        // Subsequent updates by id should hit the new index.
        ds.consume()._dataDiff!();
        ds.update({ id: "c", value: 30 });
        expect(ds.consume().data[0]).toEqual({ id: "c", value: 30 });
        expect(ds.getDirty()).toEqual(new Set([0]));
    });

    it("is a no-op for unknown ids", () => {
        const ds = new LayerDataset<Item>(getId);
        ds.add({ id: "a", value: 1 });
        ds.consume()._dataDiff!();

        ds.remove({ id: "ghost", value: 0 });
        expect(ds.consume().data).toEqual([{ id: "a", value: 1 }]);
        expect(ds.getDirty().size).toBe(0);
    });
});

describe("LayerDataset.consume", () => {
    it("does not clear dirty until _dataDiff is called", () => {
        const ds = new LayerDataset<Item>(getId);
        ds.add({ id: "a", value: 1 });
        ds.add({ id: "b", value: 2 });

        const snap = ds.consume();
        // dataComparator alone must not drain dirty.
        expect(snap.dataComparator!()).toBe(false);
        expect(ds.getDirty().size).toBe(2);
        expect(snap.dataComparator!()).toBe(false);
        expect(ds.getDirty().size).toBe(2);

        // _dataDiff is what actually clears the dirty set.
        expect(snap._dataDiff!()).toEqual([{ startRow: 0, endRow: 2 }]);
        expect(ds.getDirty().size).toBe(0);
    });

    it("preserves a snapshot's diff across repeated _dataDiff calls (downstream sub-layers)", () => {
        const ds = new LayerDataset<Item>(getId);
        ds.add({ id: "a", value: 1 });

        const snap = ds.consume();
        const first = snap._dataDiff!();
        const second = snap._dataDiff!();

        expect(first).toEqual([{ startRow: 0, endRow: 1 }]);
        expect(second).toBe(first);
    });

    it("accumulates dirty across back-to-back consume calls so the latest snapshot sees every change", () => {
        const ds = new LayerDataset<Item>(getId);
        ds.add({ id: "a", value: 1 });

        const snap = ds.consume();
        // First consume must not clear dirty — deck.gl may still pick up the later snapshot.
        expect(ds.getDirty()).toEqual(new Set([0]));

        ds.add({ id: "b", value: 2 });
        const snap2 = ds.consume();

        // Both snapshots' _dataDiff fire off the same accumulated dirty set.
        expect(snap2._dataDiff!()).toEqual([{ startRow: 0, endRow: 2 }]);
        expect(ds.getDirty().size).toBe(0);

        // The first snapshot's _dataDiff was never called, so its closure stays unsealed
        // and now coalesces an empty diff — that's fine: any pending changes were already
        // delivered via snap2.
        expect(snap._dataDiff!()).toEqual([]);
    });

    it("coalesces non-contiguous dirty indices into separate ranges", () => {
        const ds = new LayerDataset<Item>(getId);
        ds.add({ id: "a", value: 1 });
        ds.add({ id: "b", value: 2 });
        ds.add({ id: "c", value: 3 });
        ds.add({ id: "d", value: 4 });
        ds.consume()._dataDiff!();

        // Mark indices 0 and 2 dirty (skipping 1).
        ds.update({ id: "a", value: 10 });
        ds.update({ id: "c", value: 30 });

        expect(ds.consume()._dataDiff!()).toEqual([
            { startRow: 0, endRow: 1 },
            { startRow: 2, endRow: 3 },
        ]);
    });

    it("reports dataComparator true and an empty diff when nothing has changed", () => {
        const ds = new LayerDataset<Item>(getId);
        ds.add({ id: "a", value: 1 });
        ds.consume()._dataDiff!();

        const snap = ds.consume();
        expect(snap.dataComparator!()).toBe(true);
        expect(snap._dataDiff!()).toEqual([]);
    });
});
