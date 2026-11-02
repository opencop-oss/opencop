// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { TerrainCover } from "@deck.gl/extensions";
import type { Framebuffer } from "@luma.gl/core";

/** luma's `updateAttachments` is typed `protected` but is public on the WebGL impl. */
type MutableFramebuffer = Framebuffer & { updateAttachments(): void };

export class TerrainOutlineCover extends TerrainCover {
    getRenderFramebuffer(): Framebuffer | null {
        const fbo = super.getRenderFramebuffer();
        if (fbo && !fbo.colorAttachments[1]) {
            const { device } = this.targetLayer.context;
            const maskTexture = device.createTexture({
                id: this.id + "-mask",
                format: "rgba8unorm",
                width: fbo.width,
                height: fbo.height,
                sampler: { minFilter: "nearest", magFilter: "nearest" },
            });
            fbo.colorAttachments[1] = maskTexture.view;
            // Bind at the GL level.
            (fbo as MutableFramebuffer).updateAttachments();
        }
        return fbo;
    }

    delete(): void {
        const fbo = this.getRenderFramebuffer();
        fbo?.colorAttachments[1]?.destroy();
        super.delete();
    }
}
