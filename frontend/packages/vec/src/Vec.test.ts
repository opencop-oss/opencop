// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import Vec from "./Vec";

describe("Vec.replaceWith", () => {
    it("copies into existing array when new data fits", () => {
        const vec = new Vec(Float32Array, 10);
        vec.push(1, 2, 3, 4, 5);
        const originalRef = vec.array;

        const replacement = new Float32Array([10, 20, 30]);
        vec.replaceWith(replacement, 3);

        // Must reuse the same TypedArray reference (deck.gl GPU buffer stability)
        expect(vec.array).toBe(originalRef);
        expect(vec.length).toBe(3);
        expect(vec.get(0)).toBe(10);
        expect(vec.get(1)).toBe(20);
        expect(vec.get(2)).toBe(30);
    });

    it("zero-fills the tail after copy to prevent stale data", () => {
        const vec = new Vec(Float32Array, 8);
        vec.push(1, 2, 3, 4, 5, 6, 7, 8);

        const replacement = new Float32Array([10, 20]);
        vec.replaceWith(replacement, 2);

        expect(vec.length).toBe(2);
        // Tail must be zeroed — stale NaN values crash PolygonTesselator
        expect(vec.array[2]).toBe(0);
        expect(vec.array[3]).toBe(0);
        expect(vec.array[7]).toBe(0);
    });

    it("grows via resize when new data exceeds capacity, never exposes external array", () => {
        const vec = new Vec(Float32Array, 2);
        vec.push(1, 2);
        const originalRef = vec.array;

        const replacement = new Float32Array([10, 20, 30, 40, 50]);
        vec.replaceWith(replacement, 5);

        // Reference changes (resize allocated a new array), but is NOT the
        // worker's transferred buffer — it's a Vec-owned array with doubling capacity.
        expect(vec.array).not.toBe(originalRef);
        expect(vec.array).not.toBe(replacement);
        expect(vec.array.length).toBeGreaterThanOrEqual(5); // doubling strategy
        expect(vec.length).toBe(5);
        expect(vec.get(0)).toBe(10);
        expect(vec.get(4)).toBe(50);
    });

    it("handles empty replacement", () => {
        const vec = new Vec(Float32Array, 4);
        vec.push(1, 2, 3, 4);
        const originalRef = vec.array;

        vec.replaceWith(new Float32Array(0), 0);

        expect(vec.array).toBe(originalRef);
        expect(vec.length).toBe(0);
        // Entire array should be zeroed
        expect(vec.array[0]).toBe(0);
        expect(vec.array[3]).toBe(0);
    });

    it("works with Uint8Array for color attributes", () => {
        const vec = new Vec(Uint8Array, 8);
        vec.push(255, 0, 0, 255, 0, 255, 0, 255);

        const replacement = new Uint8Array([100, 100, 100, 200]);
        vec.replaceWith(replacement, 4);

        expect(vec.length).toBe(4);
        expect(vec.get(0)).toBe(100);
        expect(vec.get(3)).toBe(200);
        expect(vec.array[4]).toBe(0); // tail zeroed
    });
});
