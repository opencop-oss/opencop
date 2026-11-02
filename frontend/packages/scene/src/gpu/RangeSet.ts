// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

export type Range = { start: number; end: number };

/** Tracks a single bounding dirty span. */
export class RangeSet {
    private start?: number;
    private end?: number;

    public add(start: number, end: number): void {
        if (start === end) return;
        if (this.isEmpty()) {
            this.start = start;
            this.end = end;
        } else {
            if (this.start && start < this.start) this.start = start;
            if (this.end && end > this.end) this.end = end;
        }
    }

    public isEmpty(): boolean {
        return this.start === undefined || this.end === undefined;
    }

    public consume(): Range | undefined {
        if (this.isEmpty()) return;
        const range = { start: this.start!, end: this.end! };
        this.clear();
        return range;
    }

    /** Empties the set. */
    public clear(): void {
        this.start = undefined;
        this.end = undefined;
    }
}
