// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { BijectiveRecord } from "./BijectiveRecord";

/**
 * A {@link BijectiveRecord} that serves as both an array of id's, but also a map of id's to their respective indices.
 *
 * All public methods bypass the polymorphic {@link BijectiveRecord.get},
 * {@link BijectiveRecord.swap}, and {@link BijectiveRecord.delete} helpers
 * and access `forward` / `reverse` maps directly. This avoids a key-space
 * collision: when an element's objectId is a numeric string (e.g. "42"),
 * the `in` operator cannot distinguish it from the numeric index `42`
 * because JavaScript coerces both to the string `"42"`. Direct map access
 * eliminates the ambiguity.
 */
export class IdIndexer
    extends BijectiveRecord<string, number>
    implements Iterable<[string, number]>
{
    private _length: number;

    constructor() {
        super();
        this._length = 0;
    }

    push(id: string): void {
        this.set(id, this._length++);
    }

    pop(): void {
        const idx = --this._length;
        const id = this.reverse[idx] as string | undefined;
        delete this.reverse[idx];
        if (id !== undefined) delete this.forward[id];
    }

    swapPop(index: number): void {
        const last = this._length - 1;

        if (index !== last) {
            // Swap entries at `index` and `last` using direct map access.
            const idAtIndex = this.reverse[index] as string;
            const idAtLast = this.reverse[last] as string;

            this.reverse[index] = idAtLast;
            this.forward[idAtLast] = index;

            this.reverse[last] = idAtIndex;
            this.forward[idAtIndex] = last;
        }

        this.pop();
    }

    remove(index: number): void {
        if (index < 0 || index >= this._length) return;

        const removedId = this.reverse[index] as string | undefined;

        // Shift reverse index left by one and patch forward mappings in place.
        // This avoids repeated get()/set() calls through BijectiveRecord, which
        // are expensive in hot delete paths.
        for (let i = index; i < this._length - 1; i++) {
            const shiftedId = this.reverse[i + 1] as string;
            this.reverse[i] = shiftedId;
            this.forward[shiftedId] = i;
        }

        delete this.reverse[this._length - 1];
        if (removedId !== undefined) delete this.forward[removedId];
        this._length--;
    }

    /** Return the objectId stored at a given numeric index. */
    getId(index: number): string | undefined {
        return this.reverse[index] as string | undefined;
    }

    /** Return the numeric index for a given objectId. */
    getIndex(id: string): number | undefined {
        return this.forward[id] as number | undefined;
    }

    [Symbol.iterator](): Iterator<[string, number]> {
        return new IdIndexerIterator(this._length, this.reverse);
    }

    clear(): void {
        for (let i = 0; i < this._length; i++) {
            const id = this.reverse[i] as string | undefined;
            delete this.reverse[i];
            if (id !== undefined) delete this.forward[id];
        }
        this._length = 0;
    }

    get length() {
        return this._length;
    }
}

class IdIndexerIterator implements Iterator<[string, number]> {
    private length: number;
    private curr: number;
    private index: Partial<Record<number, string>>;

    constructor(length: number, index: Partial<Record<number, string>>) {
        this.curr = 0;
        this.length = length;
        this.index = index;
    }

    next(): IteratorResult<[string, number]> {
        return {
            done: this.curr === this.length,
            value: [this.index[this.curr]!, this.curr++],
        };
    }
}
