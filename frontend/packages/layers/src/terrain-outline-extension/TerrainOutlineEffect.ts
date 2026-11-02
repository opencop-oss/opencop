/**
 * The `_updateTerrainCover` method is derived from deck.gl v9.3.1:
 * https://github.com/visgl/deck.gl/blob/v9.3.1/modules/extensions/src/terrain/terrain-effect.ts
 *
 * MIT License
 * Copyright Vis.gl contributors.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * OpenCOP modifications in that method: construct `TerrainOutlineCover`
 * instead of `TerrainCover`, enable `drawMask` during cover rendering, and
 * add TypeScript suppressions for the private deck.gl API coupling. The
 * upstream update, picking/render pass, dirty-state, error, and cleanup flow is
 * otherwise retained.
 */

import { TerrainEffect } from "@deck.gl/extensions";
import type { EffectContext, Layer, PreRenderOptions, Viewport } from "@deck.gl/core";
import { TerrainOutlineCover } from "./TerrainOutlineCover";
import type { TerrainOutlineModuleProps } from "./terrain.shader";
import { terrainModule } from "@deck.gl/extensions";
import terrainOutlineModule from "./terrain.shader";
import { TerrainOutlineExtension } from "./TerrainOutlineExtension";

// @ts-expect-error extends TerrainEffect with private outline/color fields + method-signature
// overrides incompatible from TS's view; see docs/public/patches/terrain-internals-export-patch.md
export class TerrainOutlineEffect extends TerrainEffect {
    private outline: boolean = false;
    private color: readonly [number, number, number] = [1, 1, 1];

    setup(ctx: EffectContext): void {
        super.setup(ctx);
        // Swap out the modules.
        ctx.deck._removeDefaultShaderModule(terrainModule);
        ctx.deck._addDefaultShaderModule(terrainOutlineModule);
    }

    preRender(opts: PreRenderOptions): void {
        super.preRender(opts);
        // Turn outline on if any layer has outline on.
        const layer = opts.layers.find((layer) => layer.state.terrainOutline);
        const ex = layer?.props.extensions.find((e) => e instanceof TerrainOutlineExtension);
        this.outline = !!layer;
        this.color = ex?.opts?.color ?? [1, 1, 1];
    }

    // @ts-expect-error Altering deck.gl private method.
    // The internal implementation creates a `TerrainCover`, need to swap out to `TerrainOutlineCover`.
    // Need to make sure terrain cover created is `TerrainOutlineCover` and not `TerrainCover`.
    // Everything else about the method is copied from source code.
    private _updateTerrainCover(
        terrainLayer: Layer,
        drapeLayers: Layer[],
        viewport: Viewport,
        opts: PreRenderOptions,
    ) {
        // @ts-expect-error Accessing `isPicking`, `terrainPickingPass` and `terrainPass per deck.gl source.
        const renderPass = this.isPicking ? this.terrainPickingPass : this.terrainPass;
        // @ts-expect-error Accessing `terrainCovers` per deck.gl source.
        let terrainCover = this.terrainCovers.get(terrainLayer.id);
        if (!terrainCover) {
            terrainCover = new TerrainOutlineCover(terrainLayer);
            // @ts-expect-error Accessing `terrainCovers` per deck.gl source.
            this.terrainCovers.set(terrainLayer.id, terrainCover);
        }
        try {
            const isDirty = terrainCover.shouldUpdate({
                targetLayer: terrainLayer,
                viewport,
                layers: drapeLayers,
            });
            // @ts-expect-error Accessing `isPicking` per deck.gl source.
            if (this.isPicking || terrainCover.isDirty || isDirty) {
                renderPass.renderTerrainCover(terrainCover, {
                    ...opts,
                    layers: drapeLayers,
                    shaderModuleProps: {
                        terrain: {
                            // @ts-expect-error Accessing `dummyHeightMap` per deck.gl source.
                            dummyHeightMap: this.dummyHeightMap,
                            terrainSkipRender: false,
                            drawMask: true,
                        },
                        project: {
                            devicePixelRatio: 1,
                        },
                    },
                });

                // @ts-expect-error Accessing `isPicking` per deck.gl source.
                if (!this.isPicking) {
                    // IsDirty refers to the normal fbo, not the picking fbo.
                    // Only mark it as not dirty if the normal fbo was updated.
                    terrainCover.isDirty = false;
                }
            }
        } catch (err) {
            terrainLayer.raiseError(
                err as Error,
                `Error rendering terrain cover ${terrainCover.id}`,
            );
        }
    }

    getShaderModuleProps(
        layer: Layer,
        otherShaderModuleProps: Record<string, unknown>,
    ): { terrain: TerrainOutlineModuleProps } {
        const props = super.getShaderModuleProps(layer, otherShaderModuleProps);
        return {
            terrain: {
                ...props.terrain,
                outline: this.outline,
                drawMask: false,
                color: this.color,
            },
        };
    }

    cleanup(ctx: EffectContext): void {
        super.cleanup(ctx);
        ctx.deck._removeDefaultShaderModule(terrainOutlineModule);
    }
}
