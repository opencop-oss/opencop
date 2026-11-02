// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/** Higher is better (e.g. FPS). */
export function higherIsBetter(value: number, good: number, warn: number): string {
    if (value >= good) return "limegreen";
    if (value >= warn) return "yellow";
    return "red";
}

/** Lower is better (e.g. CPU time, ATTR %). */
export function lowerIsBetter(value: number, good: number, warn: number): string {
    if (value <= good) return "limegreen";
    if (value <= warn) return "yellow";
    return "red";
}

/** Mutable ring-buffer that avoids allocations on every push. */
export class RingBuffer {
    private readonly buf: Float64Array;
    private head = 0;
    private len = 0;

    constructor(capacity: number) {
        this.buf = new Float64Array(capacity);
    }

    push(value: number): void {
        this.buf[this.head] = value;
        this.head = (this.head + 1) % this.buf.length;
        if (this.len < this.buf.length) this.len++;
    }

    /** Iterate oldest → newest. */
    forEach(fn: (value: number, index: number) => void): void {
        const start = this.len < this.buf.length ? 0 : this.head;
        for (let i = 0; i < this.len; i++) {
            fn(this.buf[(start + i) % this.buf.length], i);
        }
    }

    map<T>(fn: (value: number, index: number) => T): T[] {
        const res = [];
        const start = this.len < this.buf.length ? 0 : this.head;

        for (let i = 0; i < this.len; i++) {
            res.push(fn(this.buf[(start + i) % this.buf.length], i));
        }

        return res;
    }

    get length(): number {
        return this.len;
    }

    clear(): void {
        this.head = 0;
        this.len = 0;
    }
}
