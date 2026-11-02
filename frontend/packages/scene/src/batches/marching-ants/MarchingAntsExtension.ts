// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Layer, type LayerContext, LayerExtension, type UpdateParameters } from "@deck.gl/core";
import { PathLayer } from "@deck.gl/layers";
import { vec3 } from "@math.gl/core";
import marchingAnts from "./marching-ants.shader";

export type MarchingAntsExtensionOptions = {
    dashSize?: number;
    gapSize?: number;
    speed?: number;
};

/**
 * Animates a `PathLayer` with a marching-ants pattern over its stroke.
 */
export class MarchingAntsExtension extends LayerExtension<Required<MarchingAntsExtensionOptions>> {
    static extensionName = "MarchingAntsExtension";

    constructor(opts: MarchingAntsExtensionOptions = {}) {
        const { dashSize = 3, gapSize = 2, speed = 10 } = opts;
        super({ dashSize, gapSize, speed });
    }

    getShaders(this: Layer) {
        return { modules: [marchingAnts] };
    }

    initializeState(this: Layer, _context: LayerContext, extension: this): void {
        if (!(this instanceof PathLayer)) {
            throw new Error(
                `MarchingAntsExtension only supports PathLayer, got "${this.constructor.name}"`,
            );
        }

        const am = this.getAttributeManager();
        if (am) {
            am.addInstanced({
                instanceDistanceOffsets: {
                    size: 1,
                    accessor: "getPath",
                    transform: extension.getDistanceOffsets.bind(this),
                },
            });
        }
    }

    updateState(this: Layer, _params: UpdateParameters<Layer>, extension: this): void {
        // Runs after the layer's own `updateState` (where PathLayer (re)creates its model), so
        // this is the earliest point a freshly-(re)built model can receive the current uniforms
        // — including the very first frame, before `draw`'s first tick.
        const { dashSize, gapSize } = extension.opts;
        this.setShaderModuleProps({ marchingAnts: { dashSize, gapSize, phase: 0 } });
    }

    draw(this: Layer, _params: unknown, extension: this): void {
        const { dashSize, gapSize, speed } = extension.opts;
        const period = dashSize + gapSize;
        const phase = ((this.context.timeline.getTime() / 1000) * speed) % period;
        this.setShaderModuleProps({ marchingAnts: { dashSize, gapSize, phase } });
        if (this.getNumInstances() > 0) this.setNeedsRedraw();
    }

    /**
     * Cumulative projected distance to the start of each segment, in the same "common space"
     * units `projectPosition` produces. Mirrors `PathStyleExtension.getDashOffsets` — the vertex
     * shader divides by `width.x` to convert into the width-relative units `dashSize`/`gapSize`
     * already use, matching `vPathPosition.y`.
     */
    getDistanceOffsets(
        this: Layer<{ positionFormat?: "XY" | "XYZ" }>,
        path: number[] | number[][],
    ): number[] {
        const positionSize = this.props.positionFormat === "XY" ? 2 : 3;
        const isNested = Array.isArray(path[0]);
        const geometrySize = isNested ? path.length : (path as number[]).length / positionSize;
        if (geometrySize === 0) return [];

        const result: number[] = [0];
        let p: number[];
        let prevP: number[] = [];
        for (let i = 0; i < geometrySize - 1; i++) {
            p = isNested
                ? (path as number[][])[i]
                : (path as number[]).slice(i * positionSize, i * positionSize + positionSize);
            p = this.projectPosition(p);
            if (i > 0) {
                result[i] = result[i - 1] + vec3.dist(prevP, p);
            }
            prevP = p;
        }
        result[geometrySize - 1] = 0;
        return result;
    }
}
