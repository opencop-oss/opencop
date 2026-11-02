// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { PathLayer } from "@deck.gl/layers";
import { MarchingAntsBatch, type MarchingAntsPrimitive } from "./MarchingAntsBatch";
import { MarchingAntsExtension } from "./MarchingAntsExtension";

function ants(id: string, positions = [0, 0, 0, 1, 1, 0]): MarchingAntsPrimitive {
    return { id, geometry: { type: "path", positions }, style: { type: "marching-ants" } };
}

const render = (batch: MarchingAntsBatch) => batch.render() as PathLayer<MarchingAntsPrimitive>;

describe("MarchingAntsBatch", () => {
    it("renders one animated path per live primitive", () => {
        const batch = new MarchingAntsBatch();
        batch.add(ants("a"));
        batch.add(ants("b", [2, 2, 0, 3, 3, 0]));

        const layer = render(batch);

        expect(layer.props.data).toEqual([ants("a"), ants("b", [2, 2, 0, 3, 3, 0])]);
        expect(layer.props.getPath(ants("b"), {} as never)).toEqual([0, 0, 0, 1, 1, 0]);
        expect(layer.props.extensions[0]).toBeInstanceOf(MarchingAntsExtension);
        expect(layer.props.pickable).toBe(false);
    });

    it("refuses a path with fewer than two vertices", () => {
        const batch = new MarchingAntsBatch();

        expect(batch.add(ants("a", [0, 0, 0]))).toBe(false);
        expect(render(batch).props.data).toEqual([]);
    });

    it("replaces a primitive on update and drops it on remove", () => {
        const batch = new MarchingAntsBatch();
        batch.add(ants("a"));
        const moved = ants("a", [5, 5, 0, 6, 6, 0]);

        batch.update({ id: "a", geometry: moved.geometry }, moved);
        expect(render(batch).props.data).toEqual([moved]);

        batch.remove("a");
        expect(render(batch).props.data).toEqual([]);
    });

    it("refuses a duplicate id, and an update or remove of an unknown or invalid path", () => {
        const batch = new MarchingAntsBatch();
        batch.add(ants("a"));

        expect(batch.add(ants("a", [5, 5, 0, 6, 6, 0]))).toBe(false);
        expect(batch.update({ id: "b" }, ants("b"))).toBe(false);
        expect(batch.update({ id: "a" }, ants("a", [0, 0, 0]))).toBe(false);
        expect(batch.remove("b")).toBe(false);
        expect(render(batch).props.data).toEqual([ants("a")]);
    });

    it("asks to re-render only when its primitives change", () => {
        const batch = new MarchingAntsBatch();
        expect(batch.flush()).toBe(false);

        batch.add(ants("a"));
        expect(batch.flush()).toBe(true);
        expect(batch.flush()).toBe(false);

        batch.remove("a");
        expect(batch.flush()).toBe(true);
        expect(batch.flush()).toBe(false);
    });
});
