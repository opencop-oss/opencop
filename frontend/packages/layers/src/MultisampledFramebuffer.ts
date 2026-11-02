// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { textureFormatDecoder, type FramebufferProps, type TextureFormat } from "@luma.gl/core";
import { GL, WebGLDevice, WEBGLFramebuffer } from "@luma.gl/webgl";

export type MultisampledFramebufferProps = {
    /**
     * Requested MSAA sample count, clamped to what the device/formats support.
     * @default 4
     */
    samples?: number;
} & FramebufferProps;

export class MultisampledFramebuffer extends WEBGLFramebuffer {
    /** Actual sample count after clamping to device/format support. */
    readonly samples: number;

    /** Luma-built fbo holding the sampleable textures. */
    private readonly resolveHandle: WebGLFramebuffer;
    private readonly renderbuffers: WebGLRenderbuffer[] = [];

    private readonly depthBlitBits: number;

    /** Performance hints for GPU, prevents write-back to VRAM. */
    private readonly invalidateAttachments: number[];

    constructor(device: WebGLDevice, props: MultisampledFramebufferProps) {
        const { samples: requestedSamples = 4 } = props;
        super(device, props);

        const { gl } = this;
        const colorFormats = this.colorAttachments.map((view) => view.texture.glInternalFormat);

        const depthAttachment = this.depthStencilAttachment?.texture;
        const depthFormat = depthAttachment?.glInternalFormat;
        const depthAttachmentWebGL = depthAttachment
            ? getDepthStencilAttachmentWebGL(depthAttachment.format)
            : undefined;
        this.depthBlitBits = depthAttachmentWebGL ? depthBlitBits(gl, depthAttachmentWebGL) : 0;
        // All renderbuffers must share one sample count or the framebuffer is
        // FRAMEBUFFER_INCOMPLETE_MULTISAMPLE — clamp to the min across formats.
        this.samples = [...colorFormats, ...(depthFormat ? [depthFormat] : [])].reduce(
            (count, format) => Math.min(count, maxSupportedSamples(gl, format, requestedSamples)),
            requestedSamples,
        );

        // Renderbuffer-only MSAA fbo. This is what draws will actually hit.
        const msaaHandle = gl.createFramebuffer();
        gl.bindFramebuffer(gl.FRAMEBUFFER, msaaHandle);

        colorFormats.forEach((format, i) => {
            this.renderbuffers.push(this.createRenderbuffer(format, gl.COLOR_ATTACHMENT0 + i));
        });
        if (depthFormat && depthAttachmentWebGL) {
            this.renderbuffers.push(this.createRenderbuffer(depthFormat, depthAttachmentWebGL));
        }

        gl.bindFramebuffer(gl.FRAMEBUFFER, null);

        this.invalidateAttachments = [
            ...colorFormats.map((_, i) => gl.COLOR_ATTACHMENT0 + i),
            ...(depthAttachmentWebGL ? [depthAttachmentWebGL] : []),
        ];

        // Swap out the handle.
        this.resolveHandle = this.handle;
        (this as { handle: WebGLFramebuffer }).handle = msaaHandle;
    }

    /**
     * Create a multisampled renderbuffer and attach it to the currently bound fbo.
     *
     * @param internalFormat The internal WebGL format.
     * @param attachmentPoint The WebGL attachment point.
     * @returns WebGLRenderbuffer bound to current WebGLFramebuffer.
     */
    private createRenderbuffer(internalFormat: number, attachmentPoint: number): WebGLRenderbuffer {
        const { gl } = this;
        const rb = gl.createRenderbuffer();
        gl.bindRenderbuffer(gl.RENDERBUFFER, rb);
        gl.renderbufferStorageMultisample(
            gl.RENDERBUFFER,
            this.samples,
            internalFormat,
            this.width,
            this.height,
        );
        gl.framebufferRenderbuffer(gl.FRAMEBUFFER, attachmentPoint, gl.RENDERBUFFER, rb);
        gl.bindRenderbuffer(gl.RENDERBUFFER, null);
        return rb;
    }

    /** Blits the MSAA renderbuffers into the resolve textures. */
    resolve(): void {
        const { gl } = this;
        const count = this.colorAttachments.length;
        const prev = gl.getParameter(gl.DRAW_FRAMEBUFFER_BINDING) as WebGLFramebuffer | null;

        gl.bindFramebuffer(gl.READ_FRAMEBUFFER, this.handle);
        gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER, this.resolveHandle);
        for (let i = 0; i < count; i++) {
            this.blitAttachment(i, i, gl.COLOR_BUFFER_BIT | (i === 0 ? this.depthBlitBits : 0));
        }
        gl.invalidateFramebuffer(gl.READ_FRAMEBUFFER, this.invalidateAttachments);
        gl.bindFramebuffer(gl.FRAMEBUFFER, prev);
    }

    /**
     * Blit color attachments.
     * @param read index of color attachment to be read.
     * @param draw index of color attachment to drawn.
     * @param bits the bits to copy.
     */
    private blitAttachment(read: number, draw: number, bits: number) {
        const { gl } = this;
        gl.readBuffer(gl.COLOR_ATTACHMENT0 + read);
        gl.drawBuffers([...Array(draw).fill(gl.NONE), gl.COLOR_ATTACHMENT0 + draw]);
        gl.blitFramebuffer(
            0,
            0,
            this.width,
            this.height,
            0,
            0,
            this.width,
            this.height,
            bits,
            gl.NEAREST,
        );
    }

    override destroy(): void {
        if (!this.destroyed) {
            const { gl } = this;
            gl.deleteFramebuffer(this.handle);
            gl.deleteFramebuffer(this.resolveHandle);
            for (const rb of this.renderbuffers) {
                gl.deleteRenderbuffer(rb);
            }
        }

        super.destroy();
    }

    /** Renderbuffer sizes are immutable; recreate the framebuffer instead. */
    protected override resizeAttachments(): void {
        throw new Error(`${this.id}: resize is not supported, destroy and recreate`);
    }
}

/**
 * Get max supported sample count.
 *
 * @param gl The WebGL2 context.
 * @param internalFormat The internal WebGL format.
 * @param requested Requested number of samples.
 * @returns The maximum supported sampled.
 */
function maxSupportedSamples(
    gl: WebGL2RenderingContext,
    internalFormat: number,
    requested: number,
): number {
    // Returns supported counts in descending order.
    const supported = (gl.getInternalformatParameter(gl.RENDERBUFFER, internalFormat, gl.SAMPLES) ??
        new Int32Array()) as Int32Array;
    for (const count of supported) {
        if (count <= requested) return count;
    }

    throw new Error(`format 0x${internalFormat.toString(16)} does not support multisampling`);
}

/**
 * Get the WebGL attachment point for luma `depthStencilAttachment`.
 *
 * @param format The `TextureFormat`.
 * @returns The attachment point for `format`.
 */
function getDepthStencilAttachmentWebGL(
    format: TextureFormat,
): GL.DEPTH_ATTACHMENT | GL.STENCIL_ATTACHMENT | GL.DEPTH_STENCIL_ATTACHMENT {
    const formatInfo = textureFormatDecoder.getInfo(format);
    switch (formatInfo.attachment) {
        case "depth":
            return GL.DEPTH_ATTACHMENT;
        case "stencil":
            return GL.STENCIL_ATTACHMENT;
        case "depth-stencil":
            return GL.DEPTH_STENCIL_ATTACHMENT;
        default:
            throw new Error(`Not a depth stencil format: ${format}`);
    }
}

/**
 * Get the blit bits for `depthStencilAttachment`.
 *
 * @param gl The WebGL2 context.
 * @param attachment The WebGL attachment point.
 * @returns The bits to copy.
 */
function depthBlitBits(
    gl: WebGL2RenderingContext,
    attachment: ReturnType<typeof getDepthStencilAttachmentWebGL>,
): number {
    return !attachment
        ? 0
        : attachment === GL.DEPTH_STENCIL_ATTACHMENT
          ? gl.DEPTH_BUFFER_BIT | gl.STENCIL_BUFFER_BIT
          : attachment === GL.STENCIL_ATTACHMENT
            ? gl.STENCIL_BUFFER_BIT
            : gl.DEPTH_BUFFER_BIT;
}
