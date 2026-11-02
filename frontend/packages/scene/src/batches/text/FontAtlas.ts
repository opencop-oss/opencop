// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Constructor } from "type-fest";
import type { FontRenderer } from "./FontRenderer";
import { TinySDFRenderer } from "./TinySDFRenderer";
import type { PixelChange, PlacementChange } from "../../gpu/AtlasTexture";

export type CharacterPlacement = {
    x: number;
    y: number;
    width: number;
    height: number;
    anchorX: number;
    anchorY: number;
    advance: number;
};

export type CharacterPlacements = Record<string, CharacterPlacement>;

export type FontSettings = {
    /** CSS font family
     * @default 'Monaco, monospace'
     */
    fontFamily?: string;
    /** CSS font weight
     * @default 'normal'
     */
    fontWeight?: string | number;
    /** Font size in pixels.
     * @default 64
     */
    fontSize?: number;
    /** Whitespace buffer around each side of the character. In general, bigger `fontSize` requires bigger `buffer`.
     * @default 4
     */
    buffer?: number;
    /** Flag to enable / disable `sdf`.
     * @default false
     */
    sdf?: boolean;
    /** How much of the radius (relative) is used for the inside part the glyph.
     * @default 0.25
     */
    cutoff?: number;
    /** How many pixels around the glyph shape to use for encoding distance. Bigger radius yields higher quality outcome. Only applies when `sdf: true`.
     * @default 12
     */
    radius?: number;
    /**
     * How much smoothing to apply to the text edges. Only applies when `sdf: true`.
     * @default 0.1
     */
    smoothing?: number;
};

export type FontAtlasProps = {
    settings?: FontSettings;
    renderer?: Constructor<FontRenderer>;
    createCanvas?: (size: number) => HTMLCanvasElement | OffscreenCanvas;
    size?: number;
};

export class FontAtlas {
    /** Map from character to reference count */
    private characters: Record<string, number> = {};
    private _placements: Record<string, CharacterPlacement> = {};

    // Current offsets for next placement.
    private xOffset: number = 0;
    private yMinOffset: number = 0;
    private yMaxOffset: number = 0;
    private pixelsChanged: Exclude<PixelChange, { kind: "rects" }> = { kind: "none" };
    private placementsChanged: PlacementChange = { kind: "none" };

    public readonly settings: Required<FontSettings>;
    public readonly baselineOffset: number;
    private readonly renderer: FontRenderer;

    public canvas: HTMLCanvasElement | OffscreenCanvas;
    private readonly _size: number;
    private readonly ctx: CanvasRenderingContext2D | OffscreenCanvasRenderingContext2D;

    constructor(props: FontAtlasProps = {}) {
        const { settings = {}, renderer, size, createCanvas } = props;

        this.settings = {
            fontFamily: settings.fontFamily ?? "Monaco, monospace",
            fontWeight: settings.fontWeight ?? "normal",
            fontSize: settings.fontSize ?? 64,
            buffer: settings.buffer ?? 4,
            sdf: settings.sdf ?? false,
            cutoff: settings.cutoff ?? 0.25,
            radius: settings.radius ?? 12,
            smoothing: settings.smoothing ?? 0.1,
        };
        this._size = size ?? 2048;
        this.canvas = (createCanvas ?? defaultCanvas)(this.size);
        this.ctx = this.canvas.getContext("2d") as CanvasRenderingContext2D;

        const { fontWeight, fontSize, fontFamily } = this.settings;
        // Initialize styling for the context.
        this.ctx.font = `${fontWeight} ${fontSize}px ${fontFamily}`;
        this.ctx.fillStyle = "#000";
        this.ctx.textBaseline = "alphabetic";
        this.ctx.textAlign = "left";

        this.renderer = new (renderer ?? TinySDFRenderer)(this.ctx, this.settings);
        const { ascent, descent } = this.renderer.measure();
        this.baselineOffset = (ascent - descent) / 2;
    }

    public get size(): number {
        return this._size;
    }

    public get placements(): Readonly<Record<string, CharacterPlacement>> {
        return this._placements;
    }

    public acquire(char: string) {
        const refs = this.characters[char];
        if (refs !== undefined) {
            this.characters[char]++;
            return;
        }

        const next = this.getNextPlacement(char);
        if (next) {
            this.place(char, next);
            this.characters[char] = 1;
            if (this.pixelsChanged.kind === "none") this.pixelsChanged = { kind: "all" };
            if (this.placementsChanged.kind === "none")
                this.placementsChanged = { kind: "keys", keys: [char] };
            else if (this.placementsChanged.kind === "keys") this.placementsChanged.keys.push(char);
        } else {
            // Replace all characters to make next fit.
            this.xOffset = 0;
            this.yMinOffset = 0;
            this.yMaxOffset = 0;
            this._placements = {};
            this.ctx.clearRect(0, 0, this.size, this.size);
            const chars = Object.keys(this.characters)
                .filter((c) => this.characters[c] > 0)
                .concat(char);
            const nextChars: Record<string, number> = {};

            for (const char of chars) {
                const next = this.getNextPlacement(char);
                if (!next) continue;

                nextChars[char] = this.characters[char] ?? 1;
                this.place(char, next);
            }

            this.characters = nextChars;
            this.pixelsChanged = { kind: "all" };
            this.placementsChanged = { kind: "all" };
        }
    }

    public release(char: string) {
        const refs = this.characters[char];
        if (refs === undefined || refs === 0) return;
        this.characters[char]--; // may reach 0; entry and placement both kept
    }

    public placement(char: string): Readonly<CharacterPlacement> | undefined {
        return this.placements[char];
    }

    public consumePixels(): typeof this.pixelsChanged {
        const res = { ...this.pixelsChanged };
        this.pixelsChanged = { kind: "none" };
        return res;
    }

    public consumePlacements(): typeof this.placementsChanged {
        const res = { ...this.placementsChanged };
        this.placementsChanged = { kind: "none" };
        return res;
    }

    private getNextPlacement(char: string):
        | {
              placement: CharacterPlacement;
              xOffset: number;
              yMinOffset: number;
              yMaxOffset: number;
          }
        | undefined {
        const { buffer } = this.settings;
        const { advance, width, ascent, descent } = this.renderer.measure(char);
        const height = ascent + descent;

        const overflow = this.xOffset + width + buffer * 2 > this.size;
        const x = (overflow ? 0 : this.xOffset) + buffer;
        const y = (overflow ? this.yMaxOffset : this.yMinOffset) + buffer;

        if (y + height + buffer > this.size) return;

        return {
            placement: {
                x,
                y,
                width,
                height,
                advance,
                anchorX: width / 2,
                anchorY: ascent,
            },
            xOffset: x + width + buffer,
            yMinOffset: y - buffer,
            yMaxOffset: Math.max(this.yMaxOffset, y + height + buffer),
        };
    }

    private place(
        char: string,
        next: Exclude<ReturnType<typeof this.getNextPlacement>, undefined>,
    ) {
        const { placement, xOffset, yMinOffset, yMaxOffset } = next;
        this._placements[char] = placement;
        this.xOffset = xOffset;
        this.yMinOffset = yMinOffset;
        this.yMaxOffset = yMaxOffset;
        this.draw(char);
    }

    private draw(char: string) {
        const { sdf } = this.settings;

        if (sdf) {
            const placement = this.placements[char];
            const measuredWidth = placement.width;
            const { data, left = 0, top = 0 } = this.renderer.draw(char);
            const x = placement.x - left;
            const y = placement.y - top;

            // snap origin to the nearest pixel
            const x0 = Math.max(0, Math.round(x));
            const y0 = Math.max(0, Math.round(y));
            const w = Math.min(data.width, this.size - x0);
            const h = Math.min(data.height, this.size - y0);
            this.ctx.putImageData(data, x0, y0, 0, 0, w, h);

            // The rendered image may extend beyond the measured glyph bounds, e.g. an SDF halo.
            // Use the entire copied image as the icon placement while preserving its layout position.
            placement.x = x0;
            placement.y = y0;
            placement.width = w;
            placement.height = h;
            placement.anchorX += w / 2 - left - measuredWidth / 2; // Resolves to w / 2 - left.
            placement.anchorY += top; // Resolves to ascent + top
        } else {
            const placement = this.placements[char];
            this.ctx.fillText(char, placement.x, placement.y + placement.anchorY);
        }
    }
}

function defaultCanvas(width: number, height = width): HTMLCanvasElement | OffscreenCanvas {
    if (typeof OffscreenCanvas !== "undefined") return new OffscreenCanvas(width, height);
    if (typeof document === "undefined") throw new Error("No canvas available; pass createCanvas");
    return Object.assign(document.createElement("canvas"), { width, height });
}
