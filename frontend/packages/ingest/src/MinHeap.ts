// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

export class MinHeap<T> {
    private data: T[] = [];
    private compare: (a: T, b: T) => number;
    private referenceIndex = new Map<T, number>();

    constructor(compare: typeof this.compare) {
        this.compare = compare;
    }

    get size(): number {
        return this.data.length;
    }

    peek(): T | undefined {
        return this.data[0];
    }

    clear(): void {
        this.data.length = 0;
        this.referenceIndex.clear();
    }

    push(value: T): void {
        this.referenceIndex.set(value, this.data.length);
        this.data.push(value);
        this.siftUp(this.data.length - 1);
    }

    /** Remove an entry by reference. O(log n) sift. */
    remove(value: T): boolean {
        const i = this.referenceIndex.get(value);
        if (i === undefined) return false;

        this.referenceIndex.delete(value);
        const last = this.data.pop()!;
        if (i < this.data.length) {
            this.data[i] = last;
            this.referenceIndex.set(last, i);
            this.siftUp(i);
            this.siftDown(i);
        }
        return true;
    }

    pop(): T | undefined {
        if (this.data.length === 0) return undefined;
        const top = this.data[0];
        this.referenceIndex.delete(top);
        const last = this.data.pop()!;
        if (this.data.length > 0) {
            this.data[0] = last;
            this.referenceIndex.set(last, 0);
            this.siftDown(0);
        }
        return top;
    }

    private siftUp(i: number): void {
        const d = this.data;
        const cmp = this.compare;
        while (i > 0) {
            const parent = (i - 1) >> 1;
            if (cmp(d[i], d[parent]) >= 0) break;
            this.swap(i, parent);
            i = parent;
        }
    }

    private swap(i: number, j: number): void {
        const d = this.data;
        const tmp = d[i];
        const map = this.referenceIndex;
        d[i] = d[j];
        d[j] = tmp;
        map.set(d[i], i);
        this.referenceIndex.set(d[j], j);
    }

    private siftDown(i: number): void {
        const n = this.data.length;
        const d = this.data;
        const cmp = this.compare;
        while (true) {
            const l = 2 * i + 1,
                r = 2 * i + 2;
            let smallest = i;

            if (l < n && cmp(d[l], d[smallest]) < 0) smallest = l;
            if (r < n && cmp(d[r], d[smallest]) < 0) smallest = r;

            if (smallest === i) break;

            this.swap(i, smallest);
            i = smallest;
        }
    }
}
