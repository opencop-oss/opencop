// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ShaderModule } from "@luma.gl/shadertools";

/**
 * `vPathPosition.y` (aliased as `geometry.uv.y` in the fragment shader) is only continuous
 * *within one path segment* — each segment's local coordinate frame restarts at 0. `PathStyleExtension`
 * solves this for its `highPrecisionDash` mode by adding a per-segment `instanceDashOffsets` attribute
 * holding the cumulative distance to the start of that segment; we mirror the same technique via
 * `instanceDistanceOffsets` so the dash pattern reads continuously around the whole path instead of
 * restarting (and visibly seaming) at every vertex.
 */
const inject = {
    "vs:#decl": /* glsl */ `\
    in float instanceDistanceOffsets;
    out float vDistanceOffset;`,

    "vs:#main-end": /* glsl */ `\
    vDistanceOffset = instanceDistanceOffsets / width.x;`,

    "fs:#decl": /* glsl */ `\
    layout(std140) uniform marchingAntsUniforms {
        float dashSize;
        float gapSize;
        float phase;
    } marchingAnts;
    in float vDistanceOffset;`,

    // Punch a fixed black dash into the layer's own stroke color (the "gap" is left untouched,
    // so it shows through as whatever `getColor` already is) — one color decision, not a palette.
    "fs:DECKGL_FILTER_COLOR": /* glsl */ `\
    float marchingAntsPeriod = marchingAnts.dashSize + marchingAnts.gapSize;
    float marchingAntsDistance = geometry.uv.y + vDistanceOffset - marchingAnts.phase;
    if (mod(marchingAntsDistance, marchingAntsPeriod) < marchingAnts.dashSize) {
        color.rgb = vec3(0.0);
    }`,
} as const;

export type MarchingAntsModuleProps = {
    /** Length of the black dash, relative to the stroke width. */
    dashSize: number;
    /** Length of the gap (shows the layer's own stroke color), relative to the stroke width. */
    gapSize: number;
    /** Animated crawl offset, in the same width-relative units as `dashSize`/`gapSize`. */
    phase: number;
};

export type MarchingAntsUniforms = MarchingAntsModuleProps;

export default {
    name: "marchingAnts",
    inject,
    getUniforms: (props: Partial<MarchingAntsModuleProps> = {}): MarchingAntsUniforms => ({
        dashSize: props.dashSize ?? 3,
        gapSize: props.gapSize ?? 2,
        phase: props.phase ?? 0,
    }),
    uniformTypes: {
        dashSize: "f32",
        gapSize: "f32",
        phase: "f32",
    },
} as const satisfies ShaderModule<MarchingAntsModuleProps, MarchingAntsUniforms>;
