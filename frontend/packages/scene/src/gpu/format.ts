// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Constructor } from "type-fest";

export type TypedArray =
    | Int8Array
    | Int16Array
    | Int32Array
    | Uint8Array
    | Uint8ClampedArray
    | Uint32Array
    | Float32Array
    | Float64Array;

/**
 * Describes the logical and physical representation of column values.
 *
 * `size` is the number of components per value; `elements` is the number of
 * underlying array elements used to store each value.
 *
 * In the vast majority of cases, `size` is equal to `elements`.
 */
export interface Format {
    readonly size: number;
    readonly elements: number;
    /** Value handed to deck.gl alongside buffer so it infers this layout. */
    readonly hint?: TypedArray;

    encode(dst: TypedArray, values: ArrayLike<number>, offset: number): void;
    create(capacity: number): TypedArray;
}

function formatFor(array: Constructor<TypedArray>) {
    return class implements Format {
        public readonly size: number;
        public readonly elements: number;

        constructor(size: number) {
            this.size = size;
            this.elements = size;
        }

        encode(dst: TypedArray, values: ArrayLike<number>, offset: number): void {
            dst.set(values, offset);
        }

        create(capacity: number): TypedArray {
            return new array(capacity * this.elements);
        }
    };
}

const F64_HINT = new Float64Array(0);

export const I8Format = formatFor(Int8Array);
export const I16Format = formatFor(Int16Array);
export const I32Format = formatFor(Int32Array);
export const U8Format = formatFor(Uint8Array);
export const UNorm8Format = formatFor(Uint8ClampedArray);
export const U32Format = formatFor(Uint32Array);
export const F32Format = formatFor(Float32Array);
export class F64Format implements Format {
    public readonly size: number;
    public readonly elements: number;
    public readonly hint = F64_HINT;

    constructor(size: number) {
        this.size = size;
        this.elements = 2 * size;
    }

    encode(dst: TypedArray, values: ArrayLike<number>, offset: number): void {
        const slots = values.length / this.size;

        for (let s = 0; s < slots; s++) {
            const base = offset + s * this.elements;
            for (let c = 0; c < this.size; c++) {
                const value = values[s * this.size + c];
                const high = Math.fround(value);
                dst[base + c] = high;
                dst[base + c + this.size] = value - high;
            }
        }
    }

    create(capacity: number): TypedArray {
        return new Float32Array(capacity * this.elements);
    }
}

export const FORMATS = {
    i8: I8Format,
    i16: I16Format,
    i32: I32Format,
    u8: U8Format,
    unorm8: UNorm8Format,
    u32: U32Format,
    f32: F32Format,
    f64: F64Format,
} as const satisfies Record<string, Constructor<Format>>;

export type ColumnType = keyof typeof FORMATS;
