// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

export type TypedArray =
    | Int8Array
    | Uint8Array
    | Uint8ClampedArray
    | Int16Array
    | Uint16Array
    | Int32Array
    | Uint32Array
    | Float32Array
    | Float64Array;

export default class Vec<E extends TypedArray = TypedArray> {
    private readonly ctor: new (capacity: number) => E;
    private _array: E;
    private _length: number;

    constructor(ctor: new (capacity: number) => E, arg?: number | E) {
        this.ctor = ctor;

        if (Array.isArray(arg)) {
            this._array = new this.ctor(arg.length);
            this._array.set(arg);
            this._length = arg.length;
        } else {
            this._array = new this.ctor((arg as number) ?? 0);
            this._length = 0;
        }
    }

    ensureRoom(additional: number): void {
        while (this._length + additional > this._array.length) this.resize();
    }

    ensureCapacity(cap: number): void {
        while (cap > this._array.length) this.resize();
    }

    isEmpty(): boolean {
        return this._length === 0;
    }

    resize(capacity?: number): void {
        let arr: E;
        if (capacity && capacity > this._array.length) {
            arr = new this.ctor(capacity);
        } else {
            arr = new this.ctor(Math.max(this._array.length * 2, 1));
        }

        arr.set(this._array.subarray(0, this._length));
        this._array = arr;
    }

    get(index: number): number {
        return this._array[index];
    }

    set(index: number, value: number): void {
        this._array[index] = value;
    }

    setArray(index: number, values: number[]): void {
        this.ensureCapacity(index + values.length);
        this._array.set(values, index);
        if (index + values.length > this._length) this._length = index + values.length;
    }

    push(...values: number[]): void {
        this.ensureRoom(values.length);
        this._array.set(values, this._length);
        this._length = this._length + values.length;
    }

    pushArray(array: number[]): void {
        this.setArray(this._length, array);
    }

    pop(): void {
        this._length = Math.max(this._length - 1, 0);
    }

    /**
     * A `O(1)` deletion, does not preserve order.
     *
     * @param index The index to remove.
     */
    swapPop(index: number): void {
        [this._array[index], this._array[this._length - 1]] = [
            this._array[this._length - 1],
            this._array[index],
        ];

        this._length--;
    }

    /**
     * Swap and pop a range of indices. Preserves relative order.
     *
     * For example,
     *
     * If Vec = `[1, 2, ..., n - 1, n]`, `swapPopRange(0, 2)` results in: `[n - 1, n, ...]`
     *
     * @param index The starting index.
     * @param range The number of indices to delete.
     */
    swapPopRange(index: number, range: number): void {
        for (let i: number = 0; i < range; i++) {
            this.swapPop(index + range - 1 - i);
        }
    }

    /**
     * Remove element(s) from the vec and shift all proceeding elements back.
     *
     * This is typically needed for variable length data, like text or polygon positions.
     *
     * @param index The starting index.
     * @param number The number of elements to remove.
     */
    remove(index: number, number?: number): void {
        const shift: number = number ?? 1;
        if (shift <= 0 || index >= this._length) return;
        const boundedShift = Math.min(shift, this._length - index);

        // Native memmove in typed arrays is much faster than JS loops.
        this._array.copyWithin(index, index + boundedShift, this._length);
        this._length -= boundedShift;
    }

    clear(): void {
        this._length = 0;
    }

    /**
     * Copy compacted data into a Vec-owned backing array and update length.
     * If the incoming data is larger than capacity, grows the array first
     * via resize() — never assigns an external array to _array. This
     * ensures deck.gl always sees a Vec-owned TypedArray with consistent
     * capacity (doubling strategy), avoiding bufferSubData size mismatches.
     * Zero-fills the unused tail to prevent stale values from causing
     * errors in layers that scan beyond `length` (e.g. PolygonTesselator).
     */
    replaceWith(array: E, length: number): void {
        if (length > this._array.length) {
            this.resize(length);
        }
        this._array.set(array.subarray(0, length) as unknown as E, 0);
        if (length < this._array.length) {
            this._array.fill(0, length);
        }
        this._length = length;
    }

    map(mapper: (v: number) => number): Vec<E> {
        return new Vec<E>(this.ctor, this._array.map(mapper) as E);
    }

    filter(predicate: (v: number) => boolean): Vec<E> {
        return new Vec<E>(this.ctor, this._array.filter(predicate) as E);
    }

    forEach(cb: (value: number, index: number) => void): void {
        for (let i: number = 0; i < this._length; i++) {
            cb(this._array[i], i);
        }
    }

    get length(): number {
        return this._length;
    }

    get array(): E {
        return this._array;
    }
}
