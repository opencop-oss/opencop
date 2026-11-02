// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Layer } from "@deck.gl/core";
import type {
    Primitive,
    PartialPrimitiveOf,
    PrimitiveGeometry,
    PrimitiveStyle,
} from "../primitive";
import type { Batch } from "./Batch";
import type { AttributeTable } from "../gpu/AttributeTable";
import type { Allocation } from "../gpu/Allocator";
import { PickingIndex } from "../picking/PickingIndex";
import type { SceneProps } from "../SceneProps";

export abstract class InstancedBatch<P extends Primitive = Primitive> implements Batch<P> {
    public abstract readonly geometry: PrimitiveGeometry["type"];
    public abstract readonly style: PrimitiveStyle["type"];

    protected abstract readonly table: AttributeTable;
    protected readonly allocations = new Map<string, Allocation>();
    protected readonly pickingIndex?: PickingIndex;

    constructor(picking?: boolean) {
        if (picking)
            this.pickingIndex = new PickingIndex((id, color) => {
                const allocation = this.allocations.get(id);
                if (!allocation) return;
                this.paint(color, allocation);
            });
    }

    public abstract render(props: SceneProps): Layer[] | Layer;

    public add(primitive: P): boolean {
        const id = primitive.id;
        if (this.allocations.get(id)) return false;
        const slots = this.slots(primitive);
        if (!slots) return false;

        const allocation = this.table.allocate(slots);
        this.allocations.set(id, allocation);
        this.pickingIndex?.acquire(id);
        this.onAdd(primitive);
        this.write(primitive, allocation);
        return true;
    }

    public update(partial: PartialPrimitiveOf<P>, patched: P): boolean {
        const id = partial.id;
        const allocation = this.allocations.get(id);
        if (!allocation) return false;

        const prevSlots = allocation.requested;
        const slots = this.slots(patched);
        if (!slots) return false;

        this.table.reallocate(allocation, slots);
        this.onUpdate(patched);
        if (prevSlots !== slots) this.write(patched, allocation);
        else this.patch(partial, allocation);
        return true;
    }

    public remove(id: string): boolean {
        const allocation = this.allocations.get(id);
        if (!allocation) return false;

        this.allocations.delete(id);
        this.table.free(allocation);
        this.pickingIndex?.release(id);
        this.onRemove(id);
        return true;
    }

    public flush(): boolean {
        return this.table.flush();
    }

    public destroy(): void {
        this.table.destroy();
    }

    /**
     * Determine the number of slots needs to store this primitive.
     * @param primitive The primitive to request slots for.
     * @returns The number of slots, or `undefined` if the primitive is malformed.
     */
    protected abstract slots(primitive: P): number | undefined;
    /**
     * Writes the primitives contents into the table.
     * @param primitive The primitive to store into the table.
     * @param allocation The allocation belonging to `primitive`.
     */
    protected abstract write(primitive: P, allocation: Allocation): void;
    /**
     * The partial counterpart to {@link write}.
     * @param partial The primitive to store into the table.
     * @param allocation The allocation belonging to `primitive`.
     */
    protected abstract patch(partial: PartialPrimitiveOf<P>, allocation: Allocation): void;
    /**
     * Writes the picking color for a primitive into the batch's picking
     * attribute column.
     * @param color The picking color assigned to the primitive.
     * @param allocation The allocation belonging to the primitive.
     */
    protected paint(color: ArrayLike<number>, allocation: Allocation) {
        void color;
        void allocation;
    }

    /**
     * Lifecycle hook called after allocation and before {@link write}.
     * @param primitive The primitive that was added.
     */
    protected onAdd(primitive: P) {
        void primitive;
    }
    /**
     * Lifecycle hook called after allocation and before {@link write} or {@link patch}.
     * @param primitive The patched primitive, with the partial updates applied.
     */
    protected onUpdate(primitive: P) {
        void primitive;
    }
    /**
     * Lifecycle hook called at the end of {@link remove}.
     * @param id The id of the removed primitive.
     */
    protected onRemove(id: string) {
        void id;
    }
}
