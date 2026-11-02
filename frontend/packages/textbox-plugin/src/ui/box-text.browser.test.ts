// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { FontAtlas, FONT_FAMILY, FONT_SETTINGS } from "@opencop/scene";
import { measureAdvanceEm, measureBaselineOffsetEm } from "./wrap-text.util";

/**
 * The plugin breaks its own lines, because a box has to be grown to hold its
 * text a frame *before* `TextBatch` lays that text out — so the two measure the
 * same glyphs independently. They agree only while both read the same metrics
 * from the same font at the same size; nothing in the build couples them.
 *
 * This asserts they do agree. It needs real font metrics, so it cannot run in
 * the happy-dom suite, which has no canvas and falls back to a flat per-
 * character estimate. If the scene's atlas changes how it measures, this fails
 * rather than letting every textbox quietly wrap to the wrong width.
 */

/**
 * `FONT_FAMILY` resolves differently per machine — CI has no Noto Sans and
 * falls through to `sans-serif`. The agreement has to hold whichever font the
 * browser picks, so it is checked against a kerning font and a non-kerning one
 * as well as the real stack.
 */
const FONTS = [FONT_FAMILY, "serif", "monospace"];

const SAMPLES = "AWilo .,gyp1234MN";

for (const fontFamily of FONTS) {
    describe(`glyph advances agree with the scene's atlas (${fontFamily})`, () => {
        it("measures every sampled glyph the same way the atlas does", () => {
            const atlas = new FontAtlas({
                settings: { ...FONT_SETTINGS, fontFamily },
            });

            for (const char of SAMPLES) {
                atlas.acquire(char);
                const placement = atlas.placement(char);
                expect(
                    placement,
                    `atlas has no placement for ${JSON.stringify(char)}`,
                ).toBeDefined();

                const atlasEm = placement!.advance / FONT_SETTINGS.fontSize;
                const pluginEm = measureAdvanceEm(char, fontFamily);

                // Exact in principle; a tolerance so a sub-pixel rounding
                // difference is not reported as drift.
                expect(pluginEm, `advance for ${JSON.stringify(char)}`).toBeCloseTo(atlasEm, 6);
            }
        });

        it("derives the same baseline offset as the atlas", () => {
            // The vertical metrics are what size the box's height, so advances
            // agreeing is only half the contract.
            const atlas = new FontAtlas({ settings: { ...FONT_SETTINGS, fontFamily } });

            expect(measureBaselineOffsetEm(fontFamily)).toBeCloseTo(
                atlas.baselineOffset / FONT_SETTINGS.fontSize,
                6,
            );
        });
    });
}
