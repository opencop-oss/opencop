// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Layer } from "@deck.gl/core";

import type {
    PartialPrimitiveOf,
    Primitive,
    PrimitiveGeometry,
    PrimitiveStyle,
} from "../primitive";
import type { SceneProps } from "../SceneProps";

/**
 * Maintains the GPU representation of a collection of primitives and exposes
 * that representation through a deck.gl layer.
 *
 * A render store owns the resources and state required to store and update a
 * particular kind of primitive. The `Scene` assigns each primitive an id,
 * which the render store uses to track it internally.
 *
 * Changes made through {@link add}, {@link update}, and {@link remove} are
 * applied to the GPU when {@link flush} is called. {@link render} produces the
 * deck.gl layer that consumes the store's current render data.
 */
export interface Batch<P extends Primitive = Primitive> {
    readonly geometry: PrimitiveGeometry["type"];
    readonly style: PrimitiveStyle["type"];

    /**
     * Adds a primitive to the batch.
     * @param primitive The primitive to add.
     * @returns `true` if added, `false` if the batch rejected it (duplicate id or malformed geometry).
     */
    add(primitive: P): boolean;

    /**
     * Updates a primitive in the batch.
     * @param primitive The updated primitive.
     * @returns `true` if updated, `false` if the batch rejected it.
     */
    update(partial: PartialPrimitiveOf<P>, patched: P): boolean;

    /**
     * Removes a primitive from the render store.
     * @param id The id of the primitive, assigned by the `Scene`.
     * @returns `true` if removed, `false` if it didn't exist in the batch.
     */
    remove(id: string): boolean;

    /**
     * Applies pending changes from the render store to the GPU.
     * @returns `true` if changes were applied, `false` otherwise.
     */
    flush(): boolean;

    /**
     * Creates a deck.gl layer representing the render store's current
     * contents.
     * @param props The scene's props, from which the batch takes its own.
     * @returns The layer responsible for performing the actual rendering.
     */
    render(props: SceneProps): Layer[] | Layer;

    /** Destroy any GPU resources before garbage collection. */
    destroy(): void;
}
