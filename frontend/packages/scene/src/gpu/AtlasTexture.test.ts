// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import type { Device } from "@luma.gl/core";
import {
    AtlasTexture,
    type AtlasLike,
    type AtlasTextureProps,
    type PixelChange,
} from "./AtlasTexture";

function setup(props: Partial<AtlasTextureProps> & { type?: string; pixels?: PixelChange } = {}) {
    const { type = "webgl", pixels = { kind: "none" }, ...rest } = props;
    const texture = { copyExternalImage: vi.fn(), generateMipmapsWebGL: vi.fn(), destroy: vi.fn() };
    const device = {
        type,
        limits: { maxTextureDimension2D: 512 },
        createTexture: vi.fn(() => texture),
        generateMipmapsWebGPU: vi.fn(),
    };
    const atlas: AtlasLike = {
        size: 256,
        canvas: {} as OffscreenCanvas,
        consumePixels: vi.fn(() => pixels),
        consumePlacements: () => ({ kind: "none" }),
    };
    const staging = { canvas: {}, clearRect: vi.fn(), drawImage: vi.fn() };
    const createCanvas = vi.fn(() => ({ getContext: () => staging }) as unknown as OffscreenCanvas);
    const atlasTexture = new AtlasTexture({
        device: device as unknown as Device,
        atlas,
        createCanvas,
        ...rest,
    });
    return { atlasTexture, texture, device, atlas, staging, createCanvas };
}

describe("AtlasTexture", () => {
    it("creates an rgba8 texture the size of the atlas", () => {
        const { atlasTexture, texture, device } = setup({ mipLevels: 3 });

        expect(device.createTexture).toHaveBeenCalledWith(
            expect.objectContaining({
                format: "rgba8unorm",
                width: 256,
                height: 256,
                mipLevels: 3,
            }),
        );
        expect(atlasTexture.gpu).toBe(texture);
        expect(atlasTexture.size).toBe(256);
    });

    it("stages sub-rects through a 128px canvas unless sized", () => {
        expect(setup().createCanvas).toHaveBeenCalledWith(128);
        expect(setup({ size: 64 }).createCanvas).toHaveBeenCalledWith(64);
    });

    it("rejects an atlas larger than the device allows", () => {
        const device = { limits: { maxTextureDimension2D: 128 } } as unknown as Device;

        expect(() => setup({ device })).toThrow(/exceeds the device limit/);
    });

    it("uploads nothing when the atlas has not changed", () => {
        const { atlasTexture, texture } = setup();

        expect(atlasTexture.flush()).toBe(false);
        expect(texture.copyExternalImage).not.toHaveBeenCalled();
        expect(texture.generateMipmapsWebGL).not.toHaveBeenCalled();
    });

    it("uploads the whole canvas when everything changed", () => {
        const { atlasTexture, texture, atlas } = setup({ pixels: { kind: "all" } });

        expect(atlasTexture.flush()).toBe(true);
        expect(texture.copyExternalImage).toHaveBeenCalledExactlyOnceWith({ image: atlas.canvas });
        expect(texture.generateMipmapsWebGL).toHaveBeenCalledOnce();
    });

    it("blits each dirty rect through the staging canvas, then regenerates mipmaps once", () => {
        const rects = [
            { x: 10, y: 20, width: 30, height: 40 },
            { x: 64, y: 0, width: 8, height: 8 },
        ];
        const { atlasTexture, texture, atlas, staging } = setup({
            pixels: { kind: "rects", rects },
        });

        expect(atlasTexture.flush()).toBe(true);

        expect(staging.clearRect).toHaveBeenCalledWith(0, 0, 30, 40);
        expect(staging.drawImage).toHaveBeenCalledWith(atlas.canvas, 10, 20, 30, 40, 0, 0, 30, 40);
        expect(texture.copyExternalImage).toHaveBeenNthCalledWith(1, {
            image: staging.canvas,
            ...rects[0],
        });
        expect(texture.copyExternalImage).toHaveBeenNthCalledWith(2, {
            image: staging.canvas,
            ...rects[1],
        });
        expect(texture.generateMipmapsWebGL).toHaveBeenCalledOnce();
    });

    it("skips mipmaps for a single-level texture", () => {
        const { atlasTexture, texture } = setup({ mipLevels: 1, pixels: { kind: "all" } });

        atlasTexture.flush();

        expect(texture.generateMipmapsWebGL).not.toHaveBeenCalled();
    });

    it("generates mipmaps through the device on WebGPU", () => {
        const { atlasTexture, texture, device } = setup({
            type: "webgpu",
            pixels: { kind: "all" },
        });

        atlasTexture.flush();

        expect(device.generateMipmapsWebGPU).toHaveBeenCalledWith(texture);
        expect(texture.generateMipmapsWebGL).not.toHaveBeenCalled();
    });

    it("destroys its texture", () => {
        const { atlasTexture, texture } = setup();

        atlasTexture.destroy();

        expect(texture.destroy).toHaveBeenCalledOnce();
    });
});
