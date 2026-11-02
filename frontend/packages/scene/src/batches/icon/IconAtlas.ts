// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import Logger from "js-logger";
import type { PixelChange, PlacementChange } from "../../gpu/AtlasTexture";

const logger = Logger.get("scene");

type Entry = {
    cell: number;
    refs: number;
};

export type DrawableImage = CanvasImageSource & { width: number; height: number };
export type RawImage = {
    data: Uint8Array | Uint8ClampedArray;
    width: number;
    height: number;
    compressed?: boolean;
};
export type IconImage = DrawableImage | RawImage;
export type LoadedIcon = {
    image: IconImage;
    anchorX?: number;
    anchorY?: number;
    mask?: boolean;
};

export type IconFetch = (url: string) => Promise<LoadedIcon | IconImage>;
export type IconLoader = () => Promise<LoadedIcon | IconImage>;
export type Icon = {
    id: string;
    source: string | IconLoader;
    anchorX?: number;
    anchorY?: number;
    mask?: boolean;
};

export const MISSING_ICON: IconPlacement = {
    x: 0,
    y: 0,
    width: 0,
    height: 0,
    anchorX: 0,
    anchorY: 0,
    mask: false,
};

export type IconPlacement = {
    x: number;
    y: number;
    width: number;
    height: number;
    anchorX: number;
    anchorY: number;
    mask: boolean;
};

export type DirtyCell = { x: number; y: number; size: number };

export type IconAtlasOptions = {
    size?: number;
    cellSize?: number;
    padding?: number;
    fetch?: IconFetch;
    createCanvas?: (size: number) => HTMLCanvasElement | OffscreenCanvas;
};

export class IconAtlas {
    readonly canvas: HTMLCanvasElement | OffscreenCanvas;
    readonly size: number;
    readonly cellSize: number;
    readonly capacity: number;
    readonly placements: Record<string, IconPlacement> = {};

    private readonly padding: number;
    private readonly inner: number;
    private readonly cols: number;
    private readonly ctx: CanvasRenderingContext2D | OffscreenCanvasRenderingContext2D;
    private readonly fetch?: IconFetch;

    private readonly free: number[];
    private readonly entries = new Map<string, Entry>();
    private readonly unreferenced = new Set<string>();

    private pixelsChanged: Exclude<PixelChange, { kind: "all" }> = { kind: "none" };
    private placementsChanged: Exclude<PlacementChange, { kind: "all" }> = { kind: "none" };

    constructor(opts: IconAtlasOptions = {}) {
        this.size = opts.size ?? 4096;
        this.cellSize = opts.cellSize ?? 128;
        this.padding = opts.padding ?? 2;
        this.fetch = opts.fetch;

        if (this.size % this.cellSize) throw new Error("size must be a multiple of cellSize");

        this.inner = this.cellSize - this.padding * 2;
        this.cols = this.size / this.cellSize;
        this.capacity = this.cols * this.cols;
        this.free = Array.from({ length: this.capacity }, (_, i) => this.capacity - 1 - i);

        this.canvas = (opts.createCanvas ?? defaultCanvas)(this.size);
        this.ctx = this.canvas.getContext("2d") as CanvasRenderingContext2D;
    }

    public placement(id: string): IconPlacement {
        return this.placements[id] ?? MISSING_ICON;
    }

    public acquire(icon: Icon): boolean {
        const existing = this.entries.get(icon.id);
        if (existing) {
            existing.refs++;
            this.unreferenced.delete(icon.id);
            return true;
        }

        const cell = this.allocate();
        if (cell === undefined) return false;

        const entry: Entry = { cell, refs: 1 };
        this.entries.set(icon.id, entry);
        void this.run(icon, entry);
        return true;
    }

    public release(id: string): void {
        const e = this.entries.get(id);
        if (!e || e.refs === 0) return;
        if (--e.refs === 0) this.unreferenced.add(id);
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

    /** Takes a free cell, reclaiming unreferenced icons only when space runs out. */
    private allocate(): number | undefined {
        const cell = this.free.pop();
        if (cell !== undefined) return cell;

        for (const id of this.unreferenced) {
            const e = this.entries.get(id)!;
            this.entries.delete(id);
            delete this.placements[id];
            this.free.push(e.cell);
        }

        this.unreferenced.clear();
        return this.free.pop();
    }

    private async run(icon: Icon, entry: Entry) {
        try {
            const result = await this.resolve(icon.source);
            const loaded: LoadedIcon = "image" in result ? result : { image: result };

            // Discard if the icon was freed, replaced again, or its cell reused meanwhile.
            if (this.entries.get(icon.id) !== entry) return;

            const next = this.draw(entry.cell, {
                image: loaded.image,
                anchorX: icon.anchorX ?? loaded.anchorX,
                anchorY: icon.anchorY ?? loaded.anchorY,
                mask: icon.mask ?? loaded.mask,
            });

            const prev = this.placements[icon.id];
            const changed = !prev || !samePlacement(prev, next);
            if (prev)
                Object.assign(prev, next); // keep object identity
            else this.placements[icon.id] = next;

            if (changed) {
                if (this.placementsChanged.kind === "none")
                    this.placementsChanged = { kind: "keys", keys: [icon.id] };
                else this.placementsChanged.keys.push(icon.id);
            }
        } catch (error) {
            logger.warn(`Failed to load icon "${icon.id}"`, error);
        }
    }

    private resolve(source: string | IconLoader): Promise<LoadedIcon | IconImage> {
        if (typeof source !== "string") return source();
        if (!this.fetch) throw new Error("String icon sources need an options.fetch");
        return this.fetch(source);
    }

    /** Clears the whole cell (padding included) and draws the image centered, never upscaled. */
    private draw(cell: number, icon: LoadedIcon): IconPlacement {
        const image = toDrawable(icon.image);
        const x = (cell % this.cols) * this.cellSize;
        const y = Math.floor(cell / this.cols) * this.cellSize;

        const scale = Math.min(1, this.inner / image.width, this.inner / image.height);
        const w = Math.max(1, Math.round(image.width * scale));
        const h = Math.max(1, Math.round(image.height * scale));
        const dx = this.padding + ((this.inner - w) >> 1);
        const dy = this.padding + ((this.inner - h) >> 1);

        const ctx = this.ctx;
        ctx.clearRect(x, y, this.cellSize, this.cellSize);
        ctx.imageSmoothingEnabled = true;
        ctx.imageSmoothingQuality = "high";
        ctx.drawImage(image, x + dx, y + dy, w, h);
        if (this.pixelsChanged.kind === "none") {
            this.pixelsChanged = {
                kind: "rects",
                rects: [{ x, y, width: this.cellSize, height: this.cellSize }],
            };
        } else {
            this.pixelsChanged.rects.push({ x, y, width: this.cellSize, height: this.cellSize });
        }

        return {
            x: x + dx,
            y: y + dy,
            width: w,
            height: h,
            anchorX: (icon.anchorX ?? image.width / 2) * scale,
            anchorY: (icon.anchorY ?? image.height / 2) * scale,
            mask: icon.mask ?? false,
        };
    }
}

/** Converts raw pixel data into something drawable; passes everything else through. */
function toDrawable(image: IconImage): DrawableImage {
    if (!isRawImage(image)) return image;
    if (image.compressed) {
        throw new Error("Compressed textures cannot be drawn into an icon atlas");
    }

    const { data, width, height } = image;
    const channels = data.length / (width * height);
    let rgba: Uint8ClampedArray<ArrayBuffer>;

    if (channels === 4) {
        rgba = new Uint8ClampedArray(data.buffer as ArrayBuffer, data.byteOffset, data.length);
    } else if (channels === 3) {
        rgba = new Uint8ClampedArray(width * height * 4);
        for (let i = 0, j = 0; i < data.length; i += 3, j += 4) {
            rgba[j] = data[i];
            rgba[j + 1] = data[i + 1];
            rgba[j + 2] = data[i + 2];
            rgba[j + 3] = 255;
        }
    } else {
        throw new Error(`Unsupported raw image: ${channels} channels at ${width}x${height}`);
    }

    const canvas = defaultCanvas(width, height);
    const ctx = canvas.getContext("2d") as CanvasRenderingContext2D;
    ctx.putImageData(new ImageData(rgba, width, height), 0, 0);
    return canvas as DrawableImage;
}

function isRawImage(image: IconImage): image is RawImage {
    return ArrayBuffer.isView((image as RawImage).data);
}

function samePlacement(a: IconPlacement, b: IconPlacement): boolean {
    return (
        a.x === b.x &&
        a.y === b.y &&
        a.width === b.width &&
        a.height === b.height &&
        a.anchorX === b.anchorX &&
        a.anchorY === b.anchorY &&
        a.mask === b.mask
    );
}

function defaultCanvas(width: number, height = width): HTMLCanvasElement | OffscreenCanvas {
    if (typeof OffscreenCanvas !== "undefined") return new OffscreenCanvas(width, height);
    if (typeof document === "undefined") throw new Error("No canvas available; pass createCanvas");
    return Object.assign(document.createElement("canvas"), { width, height });
}
