// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { FontRenderer, type FontMeasurement, type LoadedCharacter } from "./FontRenderer";
import TinySDF from "@mapbox/tiny-sdf";

export type TinySDFRendererProps = {
    fontSize: number;
    fontFamily: string;
    fontWeight: number;
    buffer: number;
    radius: number;
    cutoff: number;
};

const DEFAULT_ASCENT = 0;
const DEFAULT_DESCENT = 0;

export class TinySDFRenderer extends FontRenderer {
    private readonly tinySdf: TinySDF;

    constructor(ctx: FontRenderer["ctx"], settings: FontRenderer["settings"]) {
        super(ctx, settings);
        const { fontSize, buffer, radius, cutoff, fontFamily, fontWeight } = settings;
        this.tinySdf = new TinySDF({
            fontSize,
            buffer,
            radius,
            cutoff,
            fontFamily,
            fontWeight: `${fontWeight}`,
        });
    }

    measure(char?: string): FontMeasurement {
        if (!this.ctx) throw new Error();
        if (char === undefined) {
            const fontMetrics = this.ctx.measureText("A");
            if (fontMetrics.fontBoundingBoxAscent) {
                return {
                    advance: 0,
                    width: 0,
                    ascent: Math.ceil(fontMetrics.fontBoundingBoxAscent),
                    descent: Math.ceil(fontMetrics.fontBoundingBoxDescent),
                };
            }
            return {
                advance: 0,
                width: 0,
                ascent: this.settings.fontSize * DEFAULT_ASCENT,
                descent: this.settings.fontSize * DEFAULT_DESCENT,
            };
        }

        const metrics = this.ctx.measureText(char);
        if (!metrics.actualBoundingBoxAscent) {
            return {
                advance: metrics.width,
                width: metrics.width,
                ascent: this.settings.fontSize * DEFAULT_ASCENT,
                descent: this.settings.fontSize * DEFAULT_DESCENT,
            };
        }

        return {
            advance: metrics.width,
            width: Math.ceil(metrics.actualBoundingBoxRight - metrics.actualBoundingBoxLeft),
            ascent: Math.ceil(metrics.actualBoundingBoxAscent),
            descent: Math.ceil(metrics.actualBoundingBoxDescent),
        };
    }

    draw(char: string): LoadedCharacter {
        const { buffer } = this.settings;
        const { data, width, height } = this.tinySdf.draw(char);
        const imageData = new ImageData(width, height);
        // Populate alpha channel from TinySDF.
        for (let i = 0; i < data.length; i++) {
            imageData.data[4 * i + 3] = data[i];
        }
        return { data: imageData, left: buffer, top: buffer };
    }

    bind(ctx: CanvasRenderingContext2D | OffscreenCanvasRenderingContext2D): void {
        this.ctx = ctx;
    }
}
