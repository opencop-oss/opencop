// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { type ShaderModule } from "@luma.gl/shadertools";

/** GLSL code to be injected into every shader. */
const inject = {
    /** Declare coverage uniforms, incoming `outlines` attribute, and outgoing `vOutline` varying. */
    "vs:#decl": /* glsl */ `\
    layout(std140) uniform coverUniforms {
        bool enabled;
    } cover;

    in float outlines;
    out float vOutline;`,
    /**
     * When coverage pass is running (coverage.enabled) and the object isn't outlined (outlines < 1),
     * degenerate the triangle so it doesn't cover any fragments and thus doesn't rasterize.
     * This is the same thing that `DataFilterExtension` does.
     */
    "vs:#main-start": /* glsl */ `\
    if (cover.enabled && outlines < 1.) {
        gl_Position = vec4(0.);
        return;
    }

    vOutline = outlines;`,

    /** Declare coverage uniforms and incoming `vOutline` varying. */
    "fs:#decl": /* glsl */ `\
    layout(std140) uniform coverUniforms {
        bool enabled;
    } cover;

    in float vOutline;`,

    /** Draw the silhouette of the outlined object, to be sampled by the post-process shader. */
    "fs:#main-end": /* glsl */ `\
    if (cover.enabled && vOutline > 0.) {
        // Flat screen-space cover: white silhouette filled into att0.
        float edgeAA = step(0.001, fwidth(fragColor.a));
        float a = edgeAA * fragColor.a + (1. - edgeAA);
        fragColor = vec4(vec3(1.), a);
    }`,
} as const;

export type CoverageModuleProps = {
    enabled: boolean;
};

export type CoverageUniforms = {
    enabled: boolean;
};

export default {
    name: "cover",
    inject,
    getUniforms: (props): CoverageUniforms => ({
        enabled: !!props.enabled,
    }),
    uniformTypes: {
        enabled: "i32",
    },
} as const satisfies ShaderModule<CoverageModuleProps>;
