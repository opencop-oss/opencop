// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { FontSettings } from "./FontAtlas";

/** Measurement dimensions of a given character. */
export type FontMeasurement = {
    /** Horizontal distance to move the cursor before placing the next glyph, in pixels. */
    advance: number;
    /** Width of the visible glypth bounds, in pixels. */
    width: number;
    /** Distance from the baseline to the top of the glyph, in pixels. */
    ascent: number;
    /** Distance from the baseline to the bottom of the glypth, in pixels. */
    descent: number;
};

/** Rendered character */
export type LoadedCharacter = {
    /** Rasterized glyph pixels. */
    data: ImageData;
    /**
     * x offset from the glyph origin to the left edge of `data`, in pixels.
     * @default 0
     */
    left?: number;
    /**
     * y offset from the glyph origin to the top edge of `data`, in pixels.
     * @default 0
     */
    top?: number;
};

export abstract class FontRenderer {
    protected ctx: CanvasRenderingContext2D | OffscreenCanvasRenderingContext2D;
    protected settings: Required<FontSettings>;
    constructor(
        ctx: CanvasRenderingContext2D | OffscreenCanvasRenderingContext2D,
        settings: Required<FontSettings>,
    ) {
        this.ctx = ctx;
        this.settings = settings;
    }
    abstract measure(char?: string): FontMeasurement;
    abstract draw(char: string): LoadedCharacter;
}
