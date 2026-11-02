// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Framebuffer, Texture, TextureView } from "@luma.gl/core";
import { type ShaderPass } from "@luma.gl/shadertools";

/**
 * The number of neighbors sampled per pixel.
 *
 * Use sparingly – **impacts performance heavily**.
 */
const STEPS = 8;

/**
 * The outline fragment shader scans the entire scene and samples the coverage texture to determine if a pixel is an edge.
 *
 * Each fragment samples `STEPS` surrounding texels along a circle of radius `outline.width`.
 * If the alpha is > 0 in one of the surrounding texels and the center texel has 0 alpha, it is an edge.
 *
 * This method is not 100% accurate, but it keeps sample count low and works good enough at low width levels.
 */
const fs = /* glsl */ `\
uniform sampler2D uCover;

layout(std140) uniform outlineUniforms {
    float downscale;
    vec2 texelSize;
    vec3 color;
    float width;
} outline;

const float pi = 3.1415926;
const int STEPS = ${STEPS};

vec4 outline_sampleColor(sampler2D source, vec2 texSize, vec2 texCoord) {
    vec4 scene = texture(source, texCoord);
    vec4 center = texture(uCover, texCoord);
    float alpha = center.a;

    float result = 0.;
    for (int i = 0; i < STEPS; i++) {
        float a = (2. * pi) * (float(i) / float(STEPS));
        vec2 s = texCoord + vec2(cos(a), sin(a)) * outline.width * outline.texelSize / outline.downscale;
        vec4 cov = texture(uCover, s);

        result = max(result, cov.a);
    }

    // Show the outline only **outside** the shape: (1 - alpha) is 0 inside the
    // silhouette and fractional across the AA'd edge, anti-aliasing where outline meets shape.
    float edge = result * (1. - alpha);
    return mix(scene, vec4(outline.color, 1.), edge);
}`;

export type OutlineModuleProps = {
    fbo: Framebuffer;
    color: readonly [number, number, number];
    width: number;
    depth: TextureView;
    downscale: number;
};

export type OutlineUniforms = {
    texelSize: readonly [number, number];
    color: readonly [number, number, number];
    width: number;
    downscale: number;
};

export type OutlineBindings = {
    uCoverage: TextureView | Texture;
};

export default {
    name: "outline",
    fs,
    getUniforms: ({ fbo, color, width, downscale }) => ({
        resolution: [fbo?.width ?? 1, fbo?.height ?? 1],
        downscale: downscale,
        texelSize: [1 / (fbo?.width ?? 1), 1 / (fbo?.height ?? 1)],
        color,
        width,
        uCover: fbo?.colorAttachments[0],
    }),
    uniformTypes: {
        downscale: "f32",
        texelSize: "vec2<f32>",
        color: "vec3<f32>",
        width: "f32",
    },
    passes: [{ sampler: true }],
} as const satisfies ShaderPass<OutlineModuleProps, OutlineUniforms, OutlineBindings>;
