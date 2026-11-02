// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { Line, Point } from "@opencop/elements";
import { createActions } from "@opencop/ingest";
import { MapManager } from "./MapManager";
import { ElementStore } from "../../../../ingest/src/ElementStore";

function createManager(store?: ElementStore) {
    const elementStore = store ?? new ElementStore();
    return {
        manager: new MapManager(elementStore, createActions(elementStore)),
        elementStore,
    };
}

function makePoint(objectId: string, lng: number, lat: number) {
    return Point.create({
        position: [lng, lat],
        meta: { objectId, source: "test", stale: Date.now() + 60_000 },
    });
}

function makeLine(objectId: string, ...positions: [number, number][]) {
    return Line.create({
        positions,
        meta: { objectId, source: "test", stale: Date.now() + 60_000 },
    });
}

describe("MapManager.pickInsidePolygon", () => {
    let manager: MapManager;
    let elementStore: ElementStore;

    // A simple square: (0,0) → (10,0) → (10,10) → (0,10)
    const squareVertices: [number, number][] = [
        [0, 0],
        [10, 0],
        [10, 10],
        [0, 10],
    ];

    beforeEach(() => {
        ({ manager, elementStore } = createManager());
    });

    afterEach(() => {
        manager.dispose();
    });

    it("returns elements whose representative point falls inside the polygon", () => {
        const inside = makePoint("inside", 5, 5);
        elementStore.add(inside);

        const results = manager.pickInsidePolygon(squareVertices);

        expect(results).toHaveLength(1);
        expect(results[0].objectId).toBe("inside");
    });

    it("excludes elements outside the polygon", () => {
        const outside = makePoint("outside", 20, 20);
        elementStore.add(outside);

        expect(manager.pickInsidePolygon(squareVertices)).toHaveLength(0);
    });

    it("returns an empty array when the element store is empty", () => {
        expect(manager.pickInsidePolygon(squareVertices)).toEqual([]);
    });

    it("handles a mix of inside and outside elements correctly", () => {
        elementStore.add(makePoint("a-in", 1, 1));
        elementStore.add(makePoint("b-out", 15, 15));
        elementStore.add(makePoint("c-in", 9, 9));
        elementStore.add(makePoint("d-out", -1, -1));

        const results = manager.pickInsidePolygon(squareVertices);

        expect(results.map((e) => e.objectId).sort()).toEqual(["a-in", "c-in"]);
    });

    it("selects a multi-vertex element when at least one vertex is inside", () => {
        // Line starts outside (20, 20) and ends inside (5, 5)
        elementStore.add(makeLine("partial-in", [20, 20], [5, 5]));

        const results = manager.pickInsidePolygon(squareVertices);

        expect(results).toHaveLength(1);
        expect(results[0].objectId).toBe("partial-in");
    });

    it("excludes a multi-vertex element when all vertices are outside", () => {
        // Both vertices are outside the square
        elementStore.add(makeLine("all-out", [20, 20], [30, 30]));

        expect(manager.pickInsidePolygon(squareVertices)).toHaveLength(0);
    });

    it("skips elements that throw on getPos without propagating the error", () => {
        const good = makePoint("good", 5, 5);
        elementStore.add(good);

        // Inject a bad element directly into the store's internal map by
        // adding a real element then swapping its geometry reference.
        const bad = makePoint("bad", 5, 5);
        elementStore.add(bad);
        const badStored = elementStore.get(bad.key)!;
        Object.defineProperty(badStored, "geometry", {
            get() {
                throw new Error("corrupt geometry");
            },
            configurable: true,
        });

        let results: ReturnType<typeof manager.pickInsidePolygon>;
        expect(() => {
            results = manager.pickInsidePolygon(squareVertices);
        }).not.toThrow();

        expect(results!.map((e) => e.objectId)).toContain("good");
        expect(results!.map((e) => e.objectId)).not.toContain("bad");
    });
});
