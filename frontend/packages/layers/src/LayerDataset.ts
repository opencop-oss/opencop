// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { DataRange } from "./diff.util";

/**
 * O(1) removal by swapping the element at `index` with the last element and popping.
 * @param array The array to modify in place.
 * @param index The index of the element to remove.
 */
export function swapPop(array: unknown[], index: number): void {
    if (index !== array.length - 1) {
        [array[index], array[array.length - 1]] = [array[array.length - 1], array[index]];
    }
    array.pop();
}

/** Properties emitted by {@link LayerDataset} for efficient layer updates. */
export type DatasetSnapshot<T = unknown> = {
    readonly data: T[];
    readonly dataComparator?: () => boolean;
    readonly _dataDiff?: () => DataRange[];
    readonly visible: boolean;
};

/**
 * Managed dataset for a deck.gl layer. Emits properties diffable with `_dataDiff`.
 */
export class LayerDataset<T> {
    /** Internal data array. */
    private readonly data: T[] = [];
    /** Track index associated to an id. */
    private readonly idToIndex: Record<string, number> = {};
    /** Track all dirty indices. */
    private readonly dirty = new Set<number>();
    /** Function to retrieve a unique identifier of a data object. */
    private readonly getId: (data: T) => string;

    /**
     * @param getId Function to retrieve a unique identifier of a data object.
     */
    constructor(getId: (data: T) => string) {
        this.getId = getId;
    }

    /**
     * Add data to the underlying layer.
     * @param data The data to be added.
     */
    /** How many rows the dataset currently holds. */
    get size(): number {
        return this.data.length;
    }

    /** The row held under `id`, or undefined. */
    get(id: string): T | undefined {
        const index = this.idToIndex[id];
        return index === undefined ? undefined : this.data[index];
    }

    add(data: T): void {
        const id = this.getId(data);
        if (id in this.idToIndex) {
            // Reactive updates. Only treat data as changed when new object.
            if (data === this.data[this.idToIndex[id]]) return;
            this.data[this.idToIndex[id]] = data;
        } else {
            this.idToIndex[id] = this.data.length;
            this.data.push(data);
        }

        this.dirty.add(this.idToIndex[id]);
    }

    getDirty() {
        return this.dirty;
    }

    /**
     * Update data in the underlying layer.
     * @param data The data to be updated, id must exist in the dataset.
     */
    update(data: T): void {
        const id = this.getId(data);
        if (!(id in this.idToIndex)) return;
        this.add(data);
    }

    /**
     * Remove data from the underlying layer.
     * @param data The data to be deleted.
     */
    remove(data: T): void {
        const id = this.getId(data);
        if (!(id in this.idToIndex)) return;

        const index = this.idToIndex[id];
        const lastId = this.getId(this.data[this.data.length - 1]);
        this.dirty.add(index); // Indicate data at this index has been changed.
        this.dirty.add(this.data.length - 1); // Indicate data at this index has been removed.
        this.idToIndex[lastId] = index;
        swapPop(this.data, this.idToIndex[id]);
        delete this.idToIndex[id];
    }

    /**
     * Return deck.gl props with the diffed data.
     * @returns Data related props for the deck.gl layer.
     */
    consume(): DatasetSnapshot<T> {
        // Deck.gl coalesces .setProps calls via animation frames. Due to this, we can not clear the dirty set until
        // deck.gl asynchronously begins to update our layer. If we didn't wait, in the event that two `consume` calls
        // are fired in close succession the first set of `DataRange`s could be ignored.
        //
        // So, we need to wait till deck calls `_dataDiff` itself in order to clear the dirty set.
        // However, the _dataDiff prop may be passed downstream to sub-layers. If it called `this.coalesce` naively, the next caller would
        // recieve an empty array despite data actually being changed. So, each snapshot closes over a `coalesced` variable
        // that will preserve the diff after deck.gl calls `_dataDiff` for the first time.

        let coalesced: DataRange[] | undefined;
        const props = {
            data: this.data,
            dataComparator: () => !(this.dirty.size > 0 || (coalesced && coalesced.length > 0)),
            _dataDiff: () => {
                if (!coalesced) coalesced = this.coalesce();
                return coalesced;
            },
            visible: this.data.length > 0,
        };

        return props;
    }

    /**
     * Converts the {@link LayerSource.dirty dirty set} to an array of maximal contiguous data ranges.
     *
     * More concretely:
     * For a finite set S of integers, a half-open interval [a, b) with a < b is a maximal contiguous run of S iff:
     * 1. {a, a+1, ..., b-1} is in S; every integer in the interval is in S.
     * 2. a - 1 is not in S; can't be extended one position left.
     * 3. b is not in S; can't be extended one position right.
     *
     * Some examples:
     *
     * 1. (1, 2, 3, 10, 11) => [{ startRow: 1, endRow: 4 }, { startRow: 10, endRow: 12 }].
     * 2. (1, 2, 3, 4) => [{ startRow: 1, endRow: 5 }].
     * 3. (2, 3, 4, 7, 8, 9) => [{ startRow: 2, endRow: 5 }, { startRow: 7, endRow: 10 }].
     *
     * @returns Coalesced data ranges.
     */
    private coalesce(): DataRange[] {
        const sorted = [...this.dirty].sort((a, b) => a - b);
        const diff: DataRange[] = [];

        for (const index of sorted) {
            const last = diff[diff.length - 1];
            if (last && last.endRow === index) {
                last.endRow = index + 1;
            } else {
                diff.push({ startRow: index, endRow: index + 1 });
            }
        }

        this.dirty.clear();
        return diff;
    }
}
