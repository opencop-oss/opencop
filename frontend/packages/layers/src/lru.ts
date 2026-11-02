// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Insertion-ordered bounded LRU cache. Used by the graphic layer to bound the
 * size of its derived fill-pattern-atlas cache.
 *
 * A copy of the same small utility that lives in `@opencop/symbology`. Layers
 * deliberately keeps its own so the package carries no symbology dependency —
 * see the graphic layer's genericization note.
 */
export class LRU<K, V> {
    private readonly map = new Map<K, V>();
    private readonly max: number;

    constructor(max: number) {
        if (!Number.isInteger(max) || max < 1) {
            throw new RangeError(`LRU max must be a positive integer; got ${max}`);
        }
        this.max = max;
    }

    get(key: K): V | undefined {
        if (!this.map.has(key)) return undefined;
        const v = this.map.get(key) as V;
        this.map.delete(key);
        this.map.set(key, v);
        return v;
    }

    set(key: K, value: V): void {
        if (this.map.has(key)) this.map.delete(key);
        this.map.set(key, value);
        if (this.map.size > this.max) {
            const oldest = this.map.keys().next();
            if (!oldest.done) this.map.delete(oldest.value);
        }
    }

    clear(): void {
        this.map.clear();
    }
}
