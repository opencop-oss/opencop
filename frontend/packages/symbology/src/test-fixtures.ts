// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Shared SIDC fixtures for symbology tests. Each constant is chosen to
 * exercise a specific wrapper or dispatcher branch — names describe the
 * library category, not the SIDC structure, so tests read naturally.
 *
 * Tree-shaken from production builds (only consumed by `.test.ts` files).
 */

/** Single-point unit symbol, renderable by both milsymbol and milstd. */
export const FRIEND_INFANTRY = "10031000001211000000";

/** Multipoint Control Measure (Light Line) — min 2 points, effectively unbounded max. */
export const LIGHT_LINE = "10032500001102000000";

/** Single-point Control Measure (Action Point) — tactical graphic that
 *  isMultiPoint reports false for. Exercises the editor-ownership gate. */
export const ACTION_POINT = "10032500001301000000";

/** Multipoint Control Measure (Irregular area) — min 3, max 6 points.
 *  One of the few SIDCs with a hard upper cap; exercises too-many-points. */
export const IRREGULAR_AREA = "10032500002411010000";

/** Main Attack — DrawRule AXIS2, min 3, max 50: a centerline from the arrow's
 *  tip, then one point that sets the arrowhead's width. Exercises the `axis`
 *  draw-tool kind. */
export const MAIN_ATTACK = "10032500001514030000";

/** Rectangular Target — DrawRule RECTANGULAR3 (two endpoints + a width
 *  modifier). Exercises the `rectangular-with-width` draw-tool kind. */
export const RECTANGULAR_TARGET = "10032500002408040000";

/** Weapon/Sensor Range Fan, Circular — DrawRule CIRCULAR2. Exercises the
 *  `circular-with-radius` draw-tool kind (shared with CIRCULAR1). */
export const CIRCULAR_RANGE_FAN = "10032500002421000000";

/** Ellipse/Circle area — DrawRule ELLIPSE1. Exercises the `ellipse`
 *  draw-tool kind. */
export const ELLIPSE = "10032500002001010000";

/** Not a SIDC at all — exercises the catch/return-false defensive paths. */
export const GARBAGE = "this-is-not-a-sidc";
