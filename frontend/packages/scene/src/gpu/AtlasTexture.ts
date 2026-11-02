// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Device, Sampler, SamplerProps, Texture } from "@luma.gl/core";

export type AtlasTextureProps = {
    device: Device;
    atlas: AtlasLike;
    mipLevels?: number;
    size?: number;
    sampler?: Sampler;
    /** Supply a canvas when not running in a DOM (tests, workers, Node). */
    createCanvas?: (size: number) => HTMLCanvasElement | OffscreenCanvas;
};

export type DirtyRect = { x: number; y: number; width: number; height: number };
export type PixelChange =
    | { kind: "none" }
    | { kind: "rects"; rects: DirtyRect[] }
    | { kind: "all" };
export type PlacementChange = { kind: "none" } | { kind: "keys"; keys: string[] } | { kind: "all" };

export type AtlasLike = {
    readonly size: number;
    readonly canvas: HTMLCanvasElement | OffscreenCanvas;
    consumePixels(): PixelChange;
    consumePlacements(): PlacementChange;
};

const DEFAULT_SAMPLER: SamplerProps = {
    magFilter: "linear",
    minFilter: "linear",
    mipmapFilter: "linear",
    // Minimize bleeding at the atlas edges.
    addressModeU: "clamp-to-edge",
    addressModeV: "clamp-to-edge",
};

/**
 * Keeps a GPU texture in step with an atlas' canvas, the way `Column` keeps a buffer in
 * step with its mirror: the atlas records what changed, `flush()` uploads it at draw time.
 *
 * Batching to `flush()` matters — icons land from promises, off-frame, and each upload pass
 * costs one full mipmap regeneration regardless of how many cells it carries.
 */
export class AtlasTexture {
    private readonly device: Device;
    private readonly atlas: AtlasLike;
    private readonly texture: Texture;
    private readonly mipLevels: number;

    /** Staging canvas: the only way to hand WebGL a sub-rect of the atlas. */
    private readonly _blit: CanvasRenderingContext2D | OffscreenCanvasRenderingContext2D;

    constructor(props: AtlasTextureProps) {
        const {
            device,
            atlas,
            mipLevels = 2,
            sampler = DEFAULT_SAMPLER,
            createCanvas,
            size,
        } = props;

        if (atlas.size > device.limits.maxTextureDimension2D) {
            throw new Error(
                `Atlas of ${atlas.size}px exceeds the device limit of ${device.limits.maxTextureDimension2D}px`,
            );
        }

        this.device = device;
        this.atlas = atlas;
        this.mipLevels = mipLevels;
        this.texture = device.createTexture({
            format: "rgba8unorm",
            width: atlas.size,
            height: atlas.size,
            mipLevels,
            sampler,
        });

        const canvas = (createCanvas ?? defaultCanvas)(size ?? 128);
        this._blit = canvas.getContext("2d") as CanvasRenderingContext2D;
    }

    /** Bind this into your shader, alongside `size` to normalize pixel coordinates. */
    public get gpu(): Texture {
        return this.texture;
    }

    public get size(): number {
        return this.atlas.size;
    }

    /**
     * Uploads everything the atlas has drawn since the last call.
     * @returns `true` when an upload took place, `false` otherwise.
     */
    public flush(): boolean {
        const change = this.atlas.consumePixels();
        if (change.kind === "none") return false;
        if (change.kind === "all") this.texture.copyExternalImage({ image: this.atlas.canvas });
        else for (const rect of change.rects) this.blit(rect);
        if (this.mipLevels > 1) this.generateMipmaps();
        return true;
    }

    public destroy(): void {
        this.texture.destroy();
    }

    private blit({ x, y, width, height }: DirtyRect) {
        this._blit.clearRect(0, 0, width, height);
        this._blit.drawImage(this.atlas.canvas, x, y, width, height, 0, 0, width, height);
        this.texture.copyExternalImage({
            image: this._blit.canvas,
            x,
            y,
            width,
            height,
        });
    }

    private generateMipmaps(): void {
        if (this.device.type === "webgl") this.texture.generateMipmapsWebGL();
        else this.device.generateMipmapsWebGPU?.(this.texture);
    }
}

function defaultCanvas(size: number): HTMLCanvasElement | OffscreenCanvas {
    if (typeof OffscreenCanvas !== "undefined") return new OffscreenCanvas(size, size);
    if (typeof document === "undefined") throw new Error("No canvas available; pass createCanvas");
    return Object.assign(document.createElement("canvas"), { width: size, height: size });
}
