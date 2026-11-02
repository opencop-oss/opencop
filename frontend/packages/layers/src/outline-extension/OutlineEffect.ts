// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { PostProcessEffect, type EffectContext, type PreRenderOptions } from "@deck.gl/core";
import { CoverPass } from "./CoverPass.ts";
import outline, { type OutlineModuleProps } from "./outline.shader.ts";
import { OutlineExtension, type OutlineExtensionOptions } from "./OutlineExtension.ts";
import { MultisampledFramebuffer } from "../MultisampledFramebuffer.ts";
import { WebGLDevice } from "@luma.gl/webgl";

/** Requested MSAA sample count for the coverage pass; clamped to the device's MAX_SAMPLES. */
const SAMPLES: number = 4;
/** For experimental purposes, may need to downscale in future for performance. */
const DOWNSCALE: number = 1;

export type OutlineEffectProps = Pick<OutlineModuleProps, "color" | "width">;

export class OutlineEffect extends PostProcessEffect<typeof outline> {
    id: string = "outline-effect";
    isActive = true; // On by default.

    private device!: WebGLDevice;
    private fbo!: MultisampledFramebuffer;
    private coverPass!: CoverPass;

    constructor(
        props: OutlineEffectProps = {
            color: [1, 1, 1],
            width: 3,
        },
    ) {
        super(outline, props);
    }

    setup(context: EffectContext): void {
        super.setup(context);
        this.device = context.device as WebGLDevice;
        this.createFbo(1, 1);
        this.coverPass = new CoverPass(this.device, { id: "coverage-pass" });
    }

    getShaderModuleProps() {
        return { cover: { enabled: false } };
    }

    preRender(opts?: PreRenderOptions): void {
        if (!opts) return;

        let extension: OutlineExtension | undefined;
        for (const layer of opts.layers) {
            for (const ex of layer.props.extensions) {
                if (ex instanceof OutlineExtension && ex.opts.enabled) {
                    extension = ex;
                    break;
                }
            }

            if (extension) break;
        }

        this.isActive = !!extension;
        if (!this.isActive) return;

        const { color, width: outlineWidth }: OutlineExtensionOptions = extension!.opts;
        const [width, height] = this.device.getDefaultCanvasContext().getDrawingBufferSize();
        if (this.fbo.width !== width / DOWNSCALE || this.fbo.height !== height / DOWNSCALE) {
            this.destroyFbo();
            this.createFbo(width / DOWNSCALE, height / DOWNSCALE);
        }

        // Draw the cover silhouette into the multisampled target.
        this.coverPass.render({
            ...opts,
            target: this.fbo,
            clearColor: [0, 0, 0, 0],
            pass: "cover",
        });

        // Resolve multi-sampled renderbuffer into texture.
        this.fbo.resolve();
        this.setProps({
            ...(this.props as object),
            fbo: this.fbo,
            downscale: DOWNSCALE,
            color,
            width: outlineWidth,
        });
    }

    cleanup(): void {
        super.cleanup();
        this.destroyFbo();
        this.coverPass.cleanup();
    }

    private createFbo(width: number, height: number): void {
        this.fbo = new MultisampledFramebuffer(this.device, {
            id: "cover-multi-sample",
            width,
            height,
            colorAttachments: ["rgba8unorm"],
            samples: SAMPLES,
        });
    }

    private destroyFbo() {
        this.fbo.destroy();
    }
}
