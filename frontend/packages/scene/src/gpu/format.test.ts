// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { F64Format, FORMATS, type Format } from "./format";

describe("single-element formats", () => {
    it.each([
        ["i8", Int8Array],
        ["i16", Int16Array],
        ["i32", Int32Array],
        ["u8", Uint8Array],
        ["unorm8", Uint8ClampedArray],
        ["u32", Uint32Array],
        ["f32", Float32Array],
    ] as const)("%s stores one element per component", (type, ArrayType) => {
        const format: Format = new FORMATS[type](3);
        const dst = format.create(2);

        format.encode(dst, [1, 2, 3], 3);

        expect(format).toMatchObject({ size: 3, elements: 3 });
        expect(format.hint).toBeUndefined();
        expect(dst).toBeInstanceOf(ArrayType);
        expect(Array.from(dst)).toEqual([0, 0, 0, 1, 2, 3]);
    });

    it("u8 wraps out-of-range values", () => {
        const format = new FORMATS.u8(2);
        const dst = format.create(1);

        format.encode(dst, [256, -1], 0);

        expect(Array.from(dst)).toEqual([0, 255]);
    });

    it("unorm8 clamps to a byte and rounds", () => {
        const format = new FORMATS.unorm8(4);
        const dst = format.create(1);

        format.encode(dst, [-5, 300, 99.6, 1.2], 0);

        expect(Array.from(dst)).toEqual([0, 255, 100, 1]);
    });

    it("f32 rounds to single precision", () => {
        const format = new FORMATS.f32(1);
        const dst = format.create(1);

        format.encode(dst, [0.1], 0);

        expect(dst[0]).toBe(Math.fround(0.1));
    });
});

describe("F64Format", () => {
    it("uses two f32 elements per component and hints a Float64Array", () => {
        const format = new F64Format(3);
        const dst = format.create(2);

        expect(format).toMatchObject({ size: 3, elements: 6 });
        expect(format.hint).toBeInstanceOf(Float64Array);
        expect(dst).toBeInstanceOf(Float32Array);
        expect(dst).toHaveLength(12);
    });

    it("splits each component into its f32 value and the f32 remainder", () => {
        const format = new F64Format(2);
        const dst = format.create(1);

        format.encode(dst, [1 + 2 ** -30, 5], 0);

        // All high parts, then all low parts.
        expect(Array.from(dst)).toEqual([1, 5, 2 ** -30, 0]);
    });

    it("recovers a coordinate that f32 alone cannot hold", () => {
        const value = -122.4194155;
        const format = new F64Format(1);
        const dst = format.create(1);

        format.encode(dst, [value], 0);
        const [high, low] = dst;

        expect(Math.abs(high - value)).toBeGreaterThan(1e-6);
        expect(Math.abs(high + low - value)).toBeLessThan(1e-12);
    });

    it("encodes several slots from one call, starting at the offset", () => {
        const format = new F64Format(2);
        const dst = format.create(3);

        format.encode(dst, [1, 2, 3, 4], 4);

        expect(Array.from(dst)).toEqual([0, 0, 0, 0, 1, 2, 0, 0, 3, 4, 0, 0]);
    });
});
