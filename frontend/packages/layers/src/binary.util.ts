// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { TypedArray } from "@math.gl/core";
import type { BinaryAttribute as DeckBinaryAttribute } from "@deck.gl/core";

export type BinaryAttribute = { value: TypedArray; size: number } & DeckBinaryAttribute;
// Per deck.gl source.
export type LogicalDataType = Exclude<BinaryAttribute["type"], undefined> | "float64";
type Ctor<T> = new (...args: unknown[]) => T;

/**
 * Grow a {@link BinaryAttribute} if needed.
 *
 * @param attr The {@link BinaryAttribute}.
 * @param numInstances The desired number of instances.
 */
export function grow(attr: BinaryAttribute, numInstances: number): BinaryAttribute {
    // Big enough
    const need = numInstances * attr.size;
    if (attr.value.length >= need) return { value: attr.value, size: attr.size };

    // Resize
    const Ctor = attr.value.constructor as new (n: number) => TypedArray;
    const next = new Ctor(Math.max(need, attr.value.length * 2));
    next.set(attr.value);

    return { value: next, size: attr.size };
}

/**
 * Write a tuple into a {@link BinaryAttribute} a certain number of times.
 *
 * @param attribute The {@link BinaryAttribute} to write to.
 * @param tuple The tuple to repeat.
 * @param index The starting index
 * @param count The times to repeat.
 */
export function writeRepeated(
    attribute: BinaryAttribute,
    tuple: number[],
    index: number,
    count: number,
) {
    if (count === 0 || tuple.length === 0) return;
    const base = index * attribute.size;
    attribute.value.set(tuple, base);

    // Exponentially fill the buffer.
    let filled = 1;
    while (filled < count) {
        const n = Math.min(filled, count - filled);
        attribute.value.copyWithin(base + filled * attribute.size, base, base + n * attribute.size);
        filled += n;
    }
}

/**
 * Copy the tail end of a group of {@link BinaryAttribute}s.
 *
 * @param attributes The {@link BinaryAttribute}s.
 * @param from The index to slice from.
 * @param numInstances The number of instances in each buffer.
 * @returns
 */
export function copyTails<T extends Record<string, BinaryAttribute>>(
    attributes: T,
    from: number,
    numInstances: number,
): T {
    return Object.fromEntries(
        Object.entries(attributes).map(([key, attr]) => [
            key,
            {
                ...attr,
                value: attr.value?.slice(from * attr.size, numInstances * attr.size),
            },
        ]),
    ) as T;
}

export function fill(arr: readonly number[] | TypedArray, to: number, value: number = 0): number[] {
    const filled: number[] = [];
    for (let i = 0; i < to; i++) {
        filled[i] = arr[i] ?? value;
    }
    return filled;
}

const ATTR_TYPE_MAP = {
    float64: Float64Array,
    float32: Float32Array,
    sint16: Int16Array,
    sint32: Int32Array,
    sint8: Int8Array,
    snorm16: Int16Array,
    snorm8: Int8Array,
    uint16: Uint16Array,
    uint32: Uint32Array,
    uint8: Uint8Array,
    unorm16: Uint16Array,
    unorm8: Uint8Array,
} as const satisfies { [K in LogicalDataType]: Ctor<TypedArray> };

export function attr(type: LogicalDataType, size: number = 1, length = 0): BinaryAttribute {
    return { value: new ATTR_TYPE_MAP[type](length), size };
}
