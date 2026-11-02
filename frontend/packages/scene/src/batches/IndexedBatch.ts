// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Layer } from "@deck.gl/core";
import type { Allocation } from "../gpu/Allocator";
import type {
    PartialPrimitiveOf,
    Primitive,
    PrimitiveGeometry,
    PrimitiveStyle,
} from "../primitive";
import type { Batch } from "./Batch";
import { VariableTable } from "../gpu/VariableTable";
import { type Device, Buffer } from "@luma.gl/core";
import { PickingIndex } from "../picking/PickingIndex";
import type { SceneProps } from "../SceneProps";

export type IndexedAllocation = {
    vertex: Allocation;
    index: Allocation;
};

export type IndexedTableProps = {
    device: Device;
    capacity?: number;
    pickable?: boolean;
};

const INDEX_USAGE = Buffer.COPY_DST | Buffer.INDEX;

export abstract class IndexedBatch<P extends Primitive = Primitive> implements Batch<P> {
    public abstract readonly geometry: PrimitiveGeometry["type"];
    public abstract readonly style: PrimitiveStyle["type"];

    protected abstract readonly vertexTable: VariableTable;
    protected readonly indexTable: VariableTable;
    protected readonly allocations = new Map<string, IndexedAllocation>();
    protected readonly pickingIndex?: PickingIndex;

    constructor(props: IndexedTableProps) {
        const { device, capacity, pickable } = props;
        this.indexTable = new VariableTable({
            device,
            capacity,
            attributes: {
                indices: {
                    type: "u32",
                    size: 3,
                    tombstoneValue: [0, 0, 0],
                    bufferConfig: {
                        indexType: "uint32",
                        usage: INDEX_USAGE,
                    },
                },
            },
        });

        if (pickable)
            this.pickingIndex = new PickingIndex((id, color) => {
                const allocation = this.allocations.get(id);
                if (!allocation) return;
                this.paint(color, allocation);
            });
    }

    public abstract render(props: SceneProps): Layer | Layer[];

    public add(primitive: P): boolean {
        const { id } = primitive;
        if (this.allocations.get(id)) return false;
        const vertices = this.vertices(primitive);
        if (!vertices) return false;
        const indices = this.indices(vertices, primitive);
        const vertex = this.vertexTable.allocate(vertices);
        const index = this.indexTable.allocate(indices);

        const allocation = { vertex, index };
        this.allocations.set(id, allocation);
        this.pickingIndex?.acquire(id);
        this.onAdd(primitive);
        this.write(primitive, allocation);
        return true;
    }

    public update(partial: PartialPrimitiveOf<P>, patched: P): boolean {
        const { id } = partial;
        const allocation = this.allocations.get(id);
        if (!allocation) return false;

        const { vertex: prevVertex, index: prevIndex } = allocation;
        const prevVertices = allocation.vertex.requested;
        const prevIndices = allocation.index.requested;
        const vertices = this.vertices(patched);
        if (!vertices) return false;
        const indices = this.indices(vertices, patched);
        this.vertexTable.reallocate(prevVertex, vertices);
        this.indexTable.reallocate(prevIndex, indices);

        this.onUpdate(patched);
        if (prevVertices !== vertices || prevIndices !== indices) this.write(patched, allocation);
        else this.patch(partial, allocation);
        return true;
    }

    public remove(id: string): boolean {
        const allocation = this.allocations.get(id);
        if (!allocation) return false;
        const { vertex, index } = allocation;

        this.allocations.delete(id);
        this.vertexTable.free(vertex);
        this.indexTable.free(index);
        this.pickingIndex?.release(id);
        this.onRemove(id);
        return true;
    }

    public flush(): boolean {
        const vertex = this.vertexTable.flush();
        const index = this.indexTable.flush();
        return vertex || index;
    }

    public destroy(): void {
        this.vertexTable.destroy();
        this.indexTable.destroy();
    }

    /**
     * Determine the number of vertices needs to store this primitive.
     * @param primitive The primitive to request vertices for.
     * @returns The number of vertices, or `undefined` if the primitive is malformed.
     */
    protected abstract vertices(primitive: P): number | undefined;
    /**
     * Determine the number of indices needed to store this primitive.
     * @param vertices The number of vertices requested for the primitive.
     * @param primitive The primitive to request indices for.
     * @returns The number of indices.
     */
    protected abstract indices(vertices: number, primitive: P): number;
    protected abstract write(primitive: P, allocation: IndexedAllocation): void;
    protected abstract patch(primitive: PartialPrimitiveOf<P>, allocation: IndexedAllocation): void;

    protected onAdd(primitive: P) {
        void primitive;
    }
    protected onUpdate(primitive: P) {
        void primitive;
    }
    protected onRemove(id: string) {
        void id;
    }
    protected paint(color: ArrayLike<number>, allocation: IndexedAllocation) {
        void color;
        void allocation;
    }
}
