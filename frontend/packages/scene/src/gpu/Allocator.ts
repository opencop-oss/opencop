// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/** A contiguous range of slots managed by an {@link Allocator}. */
export type Allocation = {
    /** The starting slot of the allocation. */
    readonly offset: number;

    /**
     * The number of slots reserved by the allocation.
     *
     * This may be greater than the number of slots originally requested.
     */
    readonly length: number;

    /**
     * The number of slots originally requested by the caller.
     *
     * This may differ from {@link length} when the allocator reserves
     * additional capacity.
     */
    readonly requested: number;

    /** A value identifying the current generation of this allocation. Incremented when the allocation moves or increases in size. */
    readonly generation: number;
};

/**
 * Mutable counterpart to {@link Allocation}, intended for allocator
 * implementations that need to update allocation metadata internally.
 */
export type MutableAllocation = {
    -readonly [K in keyof Allocation]: Allocation[K];
};

/** Constructor type for an {@link Allocator}. */
export type AllocatorConstructor = new (props: AllocatorOptions) => Allocator;

/** Configuration used to construct an {@link Allocator}. */
export type AllocatorOptions = {
    /** The total number of slots available in the allocator's address space. */
    capacity: number;
};

/** Manages allocation of contiguous ranges within a fixed-size address space. */
export interface Allocator {
    /** The total size of the address space, in slots. */
    get capacity(): number;

    /**
     * The end of the highest live allocation, in slots.
     *
     * All slots at or beyond this point are unallocated.
     */
    get extent(): number;

    /**
     * Allocate a contiguous block of at least the requested number of slots.
     *
     * The returned allocation may contain more slots than requested,
     * depending on the allocator's allocation strategy.
     *
     * @param slots The minimum number of slots to allocate.
     * @returns The allocated range.
     */
    allocate(slots: number): Allocation;

    /**
     * Resize an existing allocation to fit at least the requested number
     * of slots.
     *
     * @param allocation The allocation to resize.
     * @param slots The minimum number of slots the allocation must hold.
     */
    reallocate(allocation: Allocation, slots: number): void;

    /**
     * Release an allocation, making its slots available for future
     * allocations.
     *
     * @param allocation The allocation to release.
     */
    free(allocation: Allocation): void;

    /**
     * Shrink the address space toward `capacity` slots, never below {@link extent},
     * so every live allocation keeps its offset.
     *
     * @param capacity The smallest capacity to shrink to.
     */
    shrink(capacity: number): void;
}
