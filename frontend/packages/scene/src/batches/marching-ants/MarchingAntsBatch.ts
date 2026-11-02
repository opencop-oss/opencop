// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Layer } from "@deck.gl/core";
import type {
    MarchingAntsStyle,
    PartialPrimitiveOf,
    PathGeometry,
    Primitive,
} from "../../primitive";
import { isValidPath } from "../path/PathBatch";
import { PathLayer } from "@deck.gl/layers";
import { MarchingAntsExtension } from "./MarchingAntsExtension";
import type { Batch } from "../Batch";

const MARCHING_ANTS_EXTENSION = new MarchingAntsExtension({ dashSize: 6, gapSize: 4 });
export type MarchingAntsPrimitive = Primitive<PathGeometry, MarchingAntsStyle>;
export const MarchingAntsPrimitive = {
    is: (primitive: Primitive): primitive is MarchingAntsPrimitive =>
        primitive.geometry.type === "path" && primitive.style.type === "marching-ants",
};

/**
 * @provisional Optimization with tables is deferred until a proper context to created for scene/stores —
 * some computations need access to viewport and such.
 */
export class MarchingAntsBatch implements Batch<MarchingAntsPrimitive> {
    public readonly geometry = "path";
    public readonly style = "marching-ants";
    private readonly primitives = new Map<string, MarchingAntsPrimitive>();
    private dirty = false;

    add(primitive: MarchingAntsPrimitive): boolean {
        if (this.primitives.has(primitive.id) || !isValidPath(primitive)) return false;

        this.primitives.set(primitive.id, primitive);
        this.dirty = true;
        return true;
    }

    update(
        partial: PartialPrimitiveOf<MarchingAntsPrimitive>,
        patched: MarchingAntsPrimitive,
    ): boolean {
        if (!this.primitives.has(partial.id) || !isValidPath(patched)) return false;

        this.primitives.set(partial.id, patched);
        this.dirty = true;
        return true;
    }

    remove(id: string): boolean {
        if (!this.primitives.delete(id)) return false;
        this.dirty = true;
        return true;
    }

    flush(): boolean {
        const dirty = this.dirty;
        this.dirty = false;
        return dirty;
    }

    render(): Layer {
        return new PathLayer<MarchingAntsPrimitive>({
            id: "marching-ants-render-store-layer",
            data: [...this.primitives.values()],
            getPath: (d) => d.geometry.positions,
            getColor: [255, 255, 255, 204],
            getWidth: 2,
            widthUnits: "pixels" as const,
            extensions: [MARCHING_ANTS_EXTENSION],
            parameters: {
                depthCompare: "less",
            },
            pickable: false,
        });
    }

    destroy(): void {}
}
