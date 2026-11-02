/**
 * Derived from deck.gl v9.3.1:
 * https://github.com/visgl/deck.gl/blob/v9.3.1/modules/extensions/src/terrain/shader-module.ts
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
 * OpenCOP modifications: add the outline mask attachment, radial edge sampling
 * and color blend, `TerrainOutlineCover`-aware types, outline uniforms and
 * module options, and safe dummy-texture binding while preserving deck.gl
 * terrain modes, projection, drape, and picking behavior.
 */

import type { ShaderModule } from "@luma.gl/shadertools";
import { project, type ProjectProps, type ProjectUniforms } from "@deck.gl/core";

import type { Texture } from "@luma.gl/core";
import type { TerrainOutlineCover } from "./TerrainOutlineCover";

type Bounds = [minX: number, minY: number, maxX: number, maxY: number];

/** Module parameters expected by the terrain shader module */
export type TerrainOutlineModuleProps = {
    project: ProjectProps;
    isPicking: boolean;
    heightMap: Texture | null;
    heightMapBounds?: Bounds | null;
    dummyHeightMap: Texture;
    terrainCover?: TerrainOutlineCover | null;
    drawToTerrainHeightMap?: boolean;
    useTerrainHeightMap?: boolean;
    terrainSkipRender?: boolean;

    outline: boolean;
    drawMask: boolean;
    color: readonly [number, number, number];
};

type TerrainOutlineModuleUniforms = {
    mode: number;
    bounds: [number, number, number, number];

    outline: boolean;
    texelSize: [number, number];
    drawMask: boolean;
    color: readonly [number, number, number];
};

type TerrainModuleBindings = {
    terrain_map: Texture;
    mask_map: Texture;
};

/** A model can have one of the following modes */
export const TERRAIN_MODE = {
    NONE: 0,
    /** A terrain layer rendering encoded ground elevation into the height map */
    WRITE_HEIGHT_MAP: 1,
    /** An offset layer reading encoded ground elevation from the height map */
    USE_HEIGHT_MAP: 2,
    /** A terrain layer rendering to screen, using the cover fbo overlaid with its own texture */
    USE_COVER: 3,
    /** A terrain layer rendering to screen, using the cover fbo as texture */
    USE_COVER_ONLY: 4,
    /** Draped layer is rendered into a texture, and never to screen */
    SKIP: 5,
};

const TERRAIN_MODE_CONSTANTS = Object.keys(TERRAIN_MODE)
    // @ts-expect-error - from deck.gl source
    .map((key) => `const float TERRAIN_MODE_${key} = ${TERRAIN_MODE[key]}.0;`)
    .join("\n");

const uniformBlock =
    /* glsl */ `\
    #ifndef OUTLINE
    #define OUTLINE 0
    #endif
    ` +
    TERRAIN_MODE_CONSTANTS +
    /* glsl */ `\
    layout(std140) uniform terrainUniforms {
        float mode;
        vec4 bounds;
        bool outline;
        vec2 texelSize;
        bool drawMask;
        vec3 color;
    } terrain;

    uniform sampler2D terrain_map;
    uniform sampler2D mask_map;`;

/** The number of neighbors sampled per pixel. */
const STEPS = 8;
const PI = 3.1415926;

const inject = {
    "vs:#decl": /* glsl */ `\
    #if OUTLINE
        in float outlines;

        out float vOutline;
    #endif`,

    "vs:#main-start": /* glsl */ `\
    #if OUTLINE
        vOutline = outlines;
    #endif

    if (terrain.mode == TERRAIN_MODE_SKIP) {
        gl_Position = vec4(0.0);
        return;
    }`,

    "vs:DECKGL_FILTER_GL_POSITION": /* glsl */ `\
    commonPos = geometry.position.xyz;
    if (terrain.mode == TERRAIN_MODE_WRITE_HEIGHT_MAP) {
        vec2 texCoords = (commonPos.xy - terrain.bounds.xy) / terrain.bounds.zw;
        position = vec4(texCoords * 2.0 - 1.0, 0.0, 1.0);
        commonPos.z += project.commonOrigin.z;
    }
    if (terrain.mode == TERRAIN_MODE_USE_HEIGHT_MAP) {
        vec3 anchor = geometry.worldPosition;
        anchor.z = 0.0;
        vec3 anchorCommon = project_position(anchor);
        vec2 texCoords = (anchorCommon.xy - terrain.bounds.xy) / terrain.bounds.zw;
        if (texCoords.x >= 0.0 && texCoords.y >= 0.0 && texCoords.x <= 1.0 && texCoords.y <= 1.0) {
            float terrainZ = texture(terrain_map, texCoords).r;
            geometry.position.z += terrainZ;
            position = project_common_position_to_clipspace(geometry.position);
        }
    }`,

    "fs:#decl": /* glsl */ `\
    #if OUTLINE
        in float vOutline;
        layout(location = 1) out vec4 maskColor;
    #endif`,

    "fs:#main-start": /* glsl */ `\
    if (terrain.mode == TERRAIN_MODE_WRITE_HEIGHT_MAP) {
        fragColor = vec4(commonPos.z, 0.0, 0.0, 1.0);
        return;
    }`,

    "fs:#main-end": /* glsl */ `\
    #if OUTLINE
        if (vOutline > 0.) {
            // Flat screen-space cover: white silhouette filled into att0.
            float edgeAA = step(0.001, fwidth(fragColor.a));
            float mask_a = edgeAA * fragColor.a + (1. - edgeAA);
            maskColor = vec4(vec3(1.), mask_a);
        }
    #endif`,

    "fs:DECKGL_FILTER_COLOR": /* glsl */ `\
    if ((terrain.mode == TERRAIN_MODE_USE_COVER) || (terrain.mode == TERRAIN_MODE_USE_COVER_ONLY)) {
        vec2 texCoord = (commonPos.xy - terrain.bounds.xy) / terrain.bounds.zw;
        vec4 cover = texture(terrain_map, texCoord);
        vec4 mask = texture(mask_map, texCoord);
        float edge = 0.;

        if (terrain.outline) {
            float alpha = mask.a;
            float result = 0.;
            for (int i = 0; i < ${STEPS}; i++) {
                float a = (2. * ${PI}) * (float(i) / float(${STEPS}));
                // TODO: Promote width to uniform.
                vec2 s = texCoord + vec2(cos(a), sin(a)) * 4. * terrain.texelSize;
                vec4 cov = texture(mask_map, s);

                result = max(result, cov.a);
            }

            edge = result * (1. - alpha);
        }
        
        if (terrain.mode == TERRAIN_MODE_USE_COVER_ONLY) {
            color = mix(cover, vec4(terrain.color, 1.), edge);
        } else {
            // pixel is premultiplied
            color = mix(cover + color * (1.0 - cover.a), vec4(terrain.color, 1.), edge);
        }

        return;
    }`,
} as const;

export default {
    name: "terrain",
    dependencies: [project],
    vs: uniformBlock + /* glsl */ "out vec3 commonPos;",
    fs: uniformBlock + /* glsl */ "in vec3 commonPos;",
    inject,
    getUniforms: (opts: Partial<TerrainOutlineModuleProps> = {}) => {
        if (!opts.dummyHeightMap) {
            // TerrainEffect has not provided props (e.g. not set up yet, or this pass
            // doesn't include the terrain effect in its effects list)
            return {};
        }

        if ("terrainSkipRender" in opts || "drawToTerrainHeightMap" in opts) {
            const {
                drawToTerrainHeightMap,
                heightMap,
                heightMapBounds,
                dummyHeightMap,
                terrainCover,
                useTerrainHeightMap,
                terrainSkipRender,
                outline,
                drawMask,
            } = opts;
            const projectUniforms = project.getUniforms(opts.project) as ProjectUniforms;
            const { commonOrigin } = projectUniforms;

            let mode: number = terrainSkipRender ? TERRAIN_MODE.SKIP : TERRAIN_MODE.NONE;
            // height map if case USE_HEIGHT_MAP, terrain cover if USE_COVER, otherwise empty
            let sampler: Texture = dummyHeightMap;
            let mask: Texture | undefined = dummyHeightMap;
            // height map bounds if case USE_HEIGHT_MAP, terrain cover bounds if USE_COVER, otherwise null
            let bounds: number[] | null = null;
            if (drawToTerrainHeightMap) {
                mode = TERRAIN_MODE.WRITE_HEIGHT_MAP;
                bounds = heightMapBounds!;
            } else if (useTerrainHeightMap && heightMap) {
                mode = TERRAIN_MODE.USE_HEIGHT_MAP;
                sampler = heightMap;
                bounds = heightMapBounds!;
            } else if (terrainCover) {
                // This is a terrain layer
                const fbo = opts.isPicking
                    ? terrainCover.getPickingFramebuffer()
                    : terrainCover.getRenderFramebuffer();
                const coverTexture = fbo?.colorAttachments[0].texture;
                const maskTexture = fbo?.colorAttachments[1]?.texture;
                if (opts.isPicking) {
                    mode = TERRAIN_MODE.SKIP;
                }
                if (coverTexture) {
                    sampler = coverTexture;
                    mode =
                        mode === TERRAIN_MODE.SKIP
                            ? TERRAIN_MODE.USE_COVER_ONLY
                            : TERRAIN_MODE.USE_COVER;
                    bounds = terrainCover.bounds;
                } else if (opts.isPicking && !terrainSkipRender) {
                    // terrain+draw layer without cover FBO: render own picking colors
                    mode = TERRAIN_MODE.NONE;
                }

                if (maskTexture) mask = maskTexture;
            }

            return {
                mode,
                terrain_map: sampler,
                mask_map: mask,
                texelSize: [1 / sampler.width, 1 / sampler.height],
                // Convert bounds to the common space, as [minX, minY, width, height]
                bounds: bounds
                    ? [
                          bounds[0] - commonOrigin[0],
                          bounds[1] - commonOrigin[1],
                          bounds[2] - bounds[0],
                          bounds[3] - bounds[1],
                      ]
                    : [0, 0, 0, 0],
                outline, // pass outline only to the terrain layer.
                drawMask,
                color: opts.color,
            };
        }
        // No terrain-specific props provided but dummyHeightMap is available
        // (e.g. non-terrain render pass where the effect still provides props).
        // Provide the dummy texture to satisfy the terrain_map binding.
        return {
            mode: TERRAIN_MODE.NONE,
            terrain_map: opts.dummyHeightMap,
            mask_map: opts.dummyHeightMap,
            bounds: [0, 0, 0, 0],
            outline: false,
            texelSize: [0, 0],
            drawMask: opts.drawMask,
            color: opts.color,
        };
    },
    uniformTypes: {
        mode: "f32",
        bounds: "vec4<f32>",
        outline: "i32",
        texelSize: "vec2<f32>",
        drawMask: "i32",
        color: "vec3<f32>",
    },
} as const satisfies ShaderModule<
    TerrainOutlineModuleProps,
    TerrainOutlineModuleUniforms,
    TerrainModuleBindings
>;
