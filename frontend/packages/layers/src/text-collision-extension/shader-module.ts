/**
 * Derived from deck.gl v9.2.8:
 * https://github.com/visgl/deck.gl/blob/v9.2.8/modules/extensions/src/collision-filter/shader-module.ts
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
 * OpenCOP modifications: accept per-instance pixel offsets in collision
 * coordinates, inject after main so TextLayer instance positions are available,
 * preserve fade/sort behavior, and expose bindings/types compatible with the
 * local collision extension.
 */

import { Framebuffer, Texture, TextureView } from "@luma.gl/core";
import type { ShaderModule } from "@luma.gl/shadertools";
import { project } from "@deck.gl/core";

const vs = /* glsl */ `
    in float collisionPriorities;

    // Per-instance opt-out of the visibility test. Inverted so that zero — the
    // value an unsupplied attribute takes — is the existing behaviour, and a
    // layer that does not supply it collides exactly as before.
    in float instanceNoCollide;

    uniform sampler2D collision_texture;

    uniform collisionUniforms {
    bool sort;
    bool enabled;
    } collision;

    // Modified for pixel offset
    vec2 collision_getCoords(vec4 position, vec2 offset) {
        vec4 collision_clipspace = project_common_position_to_clipspace(position);

        // Add offset
        collision_clipspace.xy += offset;

        return (1.0 + collision_clipspace.xy / collision_clipspace.w) / 2.0;
    }

    float collision_match(vec2 tex, vec3 pickingColor) {
        vec4 collision_pickingColor = texture(collision_texture, tex);
        float delta = dot(abs(collision_pickingColor.rgb - pickingColor), vec3(1.0));
        float e = 0.001;
        return step(delta, e);
    }

    float collision_isVisible(vec2 texCoords, vec3 pickingColor) {
        if (!collision.enabled) {
            return 1.0;
        }

        // Visibility test, sample area of 5x5 pixels in order to fade in/out.
        // Due to the locality, the lookups will be cached
        // This reduces the flicker present when objects are shown/hidden
        const int N = 2;
        float accumulator = 0.0;
        vec2 step = vec2(1.0 / project.viewportSize);

        const float floatN = float(N);
        vec2 delta = -floatN * step;
        for(int i = -N; i <= N; i++) {
            delta.x = -step.x * floatN;
            for(int j = -N; j <= N; j++) {
            accumulator += collision_match(texCoords + delta, pickingColor);
            delta.x += step.x;
            }
            delta.y += step.y;
        }

        float W = 2.0 * floatN + 1.0;
        return pow(accumulator / (W * W), 2.2);
    }
`;

const inject = {
    "vs:#decl": /* glsl */ `
        float collision_fade = 1.0;
    `,
    // Inject at end of main to retrieve instancePositions
    "vs:#main-end": /* glsl */ `
    if (collision.sort) {
        float collisionPriority = collisionPriorities;
        geometry.position.z = -0.001 * collisionPriority * geometry.position.w; // Support range -1000 -> 1000
    }

    // The test samples one texel — this instance's anchor — so a block of text
    // is kept or dropped whole by whatever owns that point. Text that is an
    // object rather than a label opts out and always draws; it still wrote to
    // the collision map above, so labels beneath it still declutter.
    if (collision.enabled && instanceNoCollide < 0.5) {
        vec4 collision_common_position = project_position(vec4(geometry.worldPosition, 1.0));

        // Account for pixel offset
        vec2 offset = instancePixelOffset;
        offset.y *= -1.;
        vec2 collision_texCoords = collision_getCoords(collision_common_position, project_pixel_size_to_clipspace(offset.xy));

        collision_fade = collision_isVisible(collision_texCoords, geometry.pickingColor / 255.0);
        if (collision_fade < 0.0001) {
        // Position outside clip space bounds to discard
        gl_Position = vec4(0.0, 0.0, 2.0, 1.0);
        }
    }
    `,
    "vs:DECKGL_FILTER_COLOR": /* glsl */ `
        color.a *= collision_fade;
    `,
};

export type CollisionModuleProps = {
    enabled: boolean;
    collisionFBO?: Framebuffer;
    drawToCollisionMap?: boolean;
    dummyCollisionMap?: Texture;
};

type CollisionUniforms = {
    enabled?: boolean;
    sort?: boolean;
};

type CollisionBindings = {
    collision_texture?: TextureView | Texture;
};

const getCollisionUniforms = (
    opts: CollisionModuleProps | object,
): CollisionBindings & CollisionUniforms => {
    if (!opts || !("dummyCollisionMap" in opts)) {
        return {};
    }
    const { enabled, collisionFBO, drawToCollisionMap, dummyCollisionMap } = opts;
    return {
        enabled: enabled && !drawToCollisionMap,
        sort: Boolean(drawToCollisionMap),
        collision_texture:
            !drawToCollisionMap && collisionFBO
                ? collisionFBO.colorAttachments[0]
                : dummyCollisionMap,
    };
};

// @ts-expect-error - from deck.gl source.
export default {
    name: "collision",
    dependencies: [project],
    vs,
    inject,
    getUniforms: getCollisionUniforms,
    uniformTypes: {
        sort: "i32",
        enabled: "i32",
    },
} as ShaderModule<CollisionModuleProps>;
