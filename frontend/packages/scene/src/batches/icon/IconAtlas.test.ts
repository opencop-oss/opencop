// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import { IconAtlas, type DrawableImage, type IconImage } from "./IconAtlas";

const createCanvas = (size: number) =>
    ({
        width: size,
        height: size,
        getContext: () => ({ clearRect() {}, drawImage() {} }),
    }) as unknown as OffscreenCanvas;

const image = (width: number, height = width) => ({ width, height }) as DrawableImage;
const loader = (img: IconImage = image(32)) => vi.fn(() => Promise.resolve(img));
const settle = () => new Promise((resolve) => setTimeout(resolve));

/** An atlas of four 128px cells. */
const atlas = () => new IconAtlas({ size: 256, cellSize: 128, createCanvas });

describe("IconAtlas", () => {
    it("loads an icon id once however often it is acquired", async () => {
        const icons = atlas();
        const load = loader();

        expect(icons.acquire({ id: "a", source: load })).toBe(true);
        expect(icons.acquire({ id: "a", source: load })).toBe(true);
        await settle();

        expect(load).toHaveBeenCalledTimes(1);
        expect(icons.consumePlacements()).toEqual({ kind: "keys", keys: ["a"] });
        expect(icons.consumePlacements()).toEqual({ kind: "none" });
        expect(icons.consumePixels()).toEqual({
            kind: "rects",
            rects: [{ x: 0, y: 0, width: 128, height: 128 }],
        });
    });

    it("centers an image in its padded cell, scaling it down but never up", async () => {
        const icons = atlas();
        icons.acquire({ id: "small", source: loader(image(50, 30)) });
        icons.acquire({ id: "large", source: loader(image(248, 124)) });
        await settle();

        expect(icons.placement("small")).toEqual({
            x: 39,
            y: 49,
            width: 50,
            height: 30,
            anchorX: 25,
            anchorY: 15,
            mask: false,
        });
        // The second cell, scaled by half to fit its 124px interior.
        expect(icons.placement("large")).toEqual({
            x: 130,
            y: 33,
            width: 124,
            height: 62,
            anchorX: 62,
            anchorY: 31,
            mask: false,
        });
    });

    it("prefers the icon's anchor and mask over the loaded image's", async () => {
        const icons = atlas();
        icons.acquire({
            id: "a",
            source: loader({ image: image(32), anchorX: 0, anchorY: 4, mask: false } as never),
            anchorX: 8,
            mask: true,
        });
        await settle();

        expect(icons.placement("a")).toMatchObject({ anchorX: 8, anchorY: 4, mask: true });
    });

    it("keeps a released icon until a full atlas reclaims its cell", async () => {
        const icons = atlas();
        for (const id of ["a", "b", "c", "d"]) icons.acquire({ id, source: loader() });
        await settle();

        icons.release("a");
        expect(icons.placement("a").width).toBe(32);

        const e = loader();
        expect(icons.acquire({ id: "e", source: e })).toBe(true);
        await settle();

        expect(icons.placements.a).toBeUndefined();
        // `a`'s cell.
        expect(icons.placement("e")).toMatchObject({ x: 48, y: 48 });
        expect(Object.keys(icons.placements).sort()).toEqual(["b", "c", "d", "e"]);
    });

    it("refuses an icon when every cell is still referenced", async () => {
        const icons = atlas();
        for (const id of ["a", "b", "c", "d"]) icons.acquire({ id, source: loader() });
        icons.acquire({ id: "a", source: loader() });
        icons.release("a");

        const e = loader();
        expect(icons.acquire({ id: "e", source: e })).toBe(false);
        await settle();

        expect(e).not.toHaveBeenCalled();
        expect(icons.placement("a").width).toBe(32);
    });

    it("re-referencing a released icon spares it from being reclaimed, without reloading", async () => {
        const icons = atlas();
        const a = loader();
        icons.acquire({ id: "a", source: a });
        for (const id of ["b", "c", "d"]) icons.acquire({ id, source: loader() });
        icons.release("a");

        icons.acquire({ id: "a", source: a });

        expect(icons.acquire({ id: "e", source: loader() })).toBe(false);
        await settle();
        expect(a).toHaveBeenCalledTimes(1);
    });

    it("discards a load that lands after its icon's cell was reclaimed", async () => {
        const icons = atlas();
        let resolve!: (img: IconImage) => void;
        icons.acquire({ id: "a", source: () => new Promise((r) => (resolve = r)) });
        for (const id of ["b", "c", "d"]) icons.acquire({ id, source: loader() });
        icons.release("a");
        icons.acquire({ id: "e", source: loader(image(10)) });
        await settle();

        resolve(image(64));
        await settle();

        expect(icons.placements.a).toBeUndefined();
        expect(icons.placement("e").width).toBe(10);
    });

    it("leaves an icon whose load fails unplaced", async () => {
        const icons = atlas();
        icons.acquire({ id: "a", source: () => Promise.reject(new Error("404")) });
        await settle();

        expect(icons.placements.a).toBeUndefined();
        expect(icons.consumePlacements()).toEqual({ kind: "none" });
    });
});
