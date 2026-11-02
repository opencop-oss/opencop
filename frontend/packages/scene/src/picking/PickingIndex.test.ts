// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { NULL_PICKING_COLOR, PickingIndex } from "./PickingIndex";

function setup() {
    const painted: [id: string, color: number[]][] = [];
    const index = new PickingIndex((id, color) => painted.push([id, Array.from(color)]));
    return { index, painted };
}

describe("PickingIndex", () => {
    it("assigns dense handles and keeps a primitive's handle until released", () => {
        const { index } = setup();

        expect(index.acquire("a")).toBe(0);
        expect(index.acquire("b")).toBe(1);
        expect(index.acquire("a")).toBe(0);
        expect(index.resolve(0)).toBe("a");
        expect(index.resolve(1)).toBe("b");
    });

    it("recycles released handles before growing", () => {
        const { index } = setup();
        ["a", "b", "c"].forEach((id) => index.acquire(id));
        index.release("b");
        index.release("a");

        expect(index.resolve(1)).toBeUndefined();
        expect(index.acquire("d")).toBe(0);
        expect(index.acquire("e")).toBe(1);
        expect(index.acquire("f")).toBe(3);
        expect(index.resolve(1)).toBe("e");
    });

    it("ignores releasing an unknown primitive", () => {
        const { index } = setup();
        index.acquire("a");
        index.release("missing");

        expect(index.acquire("b")).toBe(1);
    });

    it("encodes handle + 1 little-endian into RGB", () => {
        const { index } = setup();
        for (let i = 0; i < 300; i++) index.acquire(String(i));

        expect(Array.from(index.encode("0"))).toEqual([1, 0, 0, 0]);
        expect(Array.from(index.encode("299"))).toEqual([44, 1, 0, 0]);
    });

    it("encodes an unknown primitive as the null picking color", () => {
        const { index } = setup();

        expect(Array.from(index.encode("missing"))).toEqual(Array.from(NULL_PICKING_COLOR));
    });

    it("decodes a picking color back to the primitive's handle", () => {
        const { index } = setup();
        for (let i = 0; i < 300; i++) index.acquire(String(i));

        for (const id of ["0", "255", "299"]) {
            expect(index.resolve(index.decode(Array.from(index.encode(id)))!)).toBe(id);
        }
        expect(index.decode([0, 0, 0, 0])).toBeUndefined();
        expect(index.decode([0, 0, 1, 0])).toBe(0x10000 - 1);
    });

    it("reuses one array for every encoding", () => {
        const { index } = setup();
        index.acquire("a");
        index.acquire("b");

        expect(index.encode("a")).toBe(index.encode("b"));
    });

    it("paints a masked primitive with the null picking color", () => {
        const { index, painted } = setup();
        index.acquire("a");
        index.acquire("b");
        index.mask(1);

        expect(painted).toEqual([["b", [0, 0, 0, 0]]]);
    });

    it("ignores masking a handle without a primitive", () => {
        const { index, painted } = setup();
        index.mask(0);
        index.unmask();

        expect(painted).toEqual([]);
    });

    it("repaints every masked primitive with its color once on unmask", () => {
        const { index, painted } = setup();
        index.acquire("a");
        index.acquire("b");
        index.mask(0);
        index.mask(1);
        painted.length = 0;

        index.unmask();
        index.unmask();

        expect(painted).toEqual([
            ["a", [1, 0, 0, 0]],
            ["b", [2, 0, 0, 0]],
        ]);
    });

    it("does not repaint a masked primitive after it is released", () => {
        const { index, painted } = setup();
        index.acquire("a");
        index.mask(0);
        index.release("a");
        painted.length = 0;

        index.unmask();

        expect(painted).toEqual([]);
    });
});
