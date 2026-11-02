// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * This shader injection computes the angular distance of a pixel from specified start angle.
 * If the angular difference exceeds end - start, the pixel is discarded and not colored.
 *
 * The gradient is achieved by simply scaling the opacity of the pixel by its percentage of angular distance covered.
 */
export const inject = {
    "vs:#decl": /* glsl */ `\
    in vec2 instanceAngles;
    
    out vec2 vAngles;
    `,
    "vs:#main-end": /* glsl */ `\
    vAngles = instanceAngles;
    `,
    "fs:#decl": /* glsl */ `\
    in vec2 vAngles;
    `,
    "fs:DECKGL_FILTER_COLOR": /* glsl */ `\
    const float TWO_PI = 6.2831853;

    // Compute angle of pixel in uv space.
    float angle = atan(geometry.uv.y, geometry.uv.x);
    // Directed angular difference between start and end (+ cww).
    float sweep = vAngles.y - vAngles.x;

    // Compute the angular distance from the start angle to the pixel angle.
    //
    // When sweep is positive (sweep is going ccw):
    //     1. sign(sweep) is no-op for positive angles.
    //     2. sign(sweep) ensures cw angles stay negative, mod by TWO_PI to get ccw angle and use that as the distance.
    //
    // When sweep is negative (sweep is going cw):
    //     1. sign(sweep) properly maps negative angles to a positive angular distance.
    //     2. sign(sweep) maps positive angles to their reflections, mod by TWO_PI to get cw angle and use that as the distance.
    //    This is equivalent to doing ccwAngle - 360 to get the negative cw rotation, then taking its absolute value to get the angular distance.
    float rel   = mod(sign(sweep) * (angle - vAngles.x), TWO_PI);  // [0, 2π) along sweep
    float span  = abs(sweep);

    if (rel > span) discard;

    float t = rel / span;
    color.a *= t;
    `,
} as const;
