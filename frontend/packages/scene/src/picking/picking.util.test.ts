// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { GetPickingInfoParams, PickingInfo } from "@deck.gl/core";
import { ScatterplotLayer } from "@deck.gl/layers";
import { PickingIndex } from "./PickingIndex";
import { pickable, pickableScatterplot, primitiveId } from "./picking.util";

const info = (overrides: Partial<PickingInfo>) => ({ index: -1, ...overrides }) as PickingInfo;

describe("primitiveId", () => {
    it("reads the primitive id from a pickable layer's picked object", () => {
        expect(primitiveId(info({ object: { primitiveId: "a" } }))).toBe("a");
    });

    it("is undefined when nothing, or a foreign object, was picked", () => {
        expect(primitiveId(info({}))).toBeUndefined();
        expect(primitiveId(info({ object: null }))).toBeUndefined();
        expect(primitiveId(info({ object: { id: "a" } }))).toBeUndefined();
    });
});

describe("pickable", () => {
    function setup() {
        const painted: [string, number[]][] = [];
        const pickingIndex = new PickingIndex((id, color) => painted.push([id, Array.from(color)]));
        pickingIndex.acquire("a");
        pickingIndex.acquire("b");
        const layer = pickableScatterplot({ id: "points", data: [], pickable: true, pickingIndex });
        return { layer, painted };
    }

    it("extends the given layer class", () => {
        expect(setup().layer).toBeInstanceOf(ScatterplotLayer);
        expect(new (pickable(ScatterplotLayer))({ id: "x" })).toBeInstanceOf(ScatterplotLayer);
    });

    it("resolves the picked index to its primitive id", () => {
        const { layer } = setup();
        const picked = layer.getPickingInfo({
            info: info({ index: 1, x: 5 }),
            mode: "hover",
        } as GetPickingInfoParams);

        expect(picked).toMatchObject({ index: 1, x: 5 });
        expect(primitiveId(picked)).toBe("b");
    });

    it("masks a picked index and restores it through the picking index", () => {
        const { layer, painted } = setup();
        layer.disablePickingIndex(1);
        layer.restorePickingColors();

        expect(painted).toEqual([
            ["b", [0, 0, 0, 0]],
            ["b", [2, 0, 0, 0]],
        ]);
    });
});
