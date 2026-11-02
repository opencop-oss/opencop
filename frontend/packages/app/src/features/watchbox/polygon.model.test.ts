// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    parsePolygon,
    polygonFromPositions,
    ringSelfIntersection,
    type WatchboxPolygonError,
} from "./polygon.model";
import type { WatchboxPolygon } from "./watchbox.model";

describe("watchbox-polygon", () => {
    const createPolygon = (coordinates: GeoJSON.Position[][]) =>
        ({ type: "Polygon", coordinates }) as const;

    function expectError(polygon: WatchboxPolygon, ...errors: WatchboxPolygonError[]) {
        const res = parsePolygon(polygon);
        expect(res.ok).toBe(false);
        errors.forEach((error) =>
            expect((res as { errors: WatchboxPolygonError[] }).errors.includes(error)).toBe(true),
        );
    }

    it("errors on anti-meridian", () => {
        const polygon = createPolygon([
            [
                [170, 0],
                [175, 10],
                [-170, 10],
                [170, 0],
            ],
        ]);

        expectError(polygon, "crosses-antimeridian");
    });
    it("errors on not closed", () => {
        const polygon = createPolygon([
            [
                [170, 0],
                [175, 10],
                [175, 20],
                [171, 0],
            ],
        ]);

        expectError(polygon, "not-closed");
    });
    it("errors on having holes", () => {
        const polygon = createPolygon([[], []]);
        expectError(polygon, "has-holes");
    });
    it("errors on too few vertices", () => {
        const polygon = createPolygon([[]]);
        expectError(polygon, "too-few-vertices");
    });
    it("errors on empty", () => {
        const polygon = createPolygon([]);
        expectError(polygon, "empty");
    });
    it("accounts for many errors", () => {
        const polygon = createPolygon([
            [
                [170, 0],
                [-170, 10],
                [175, 20],
                [171, 0],
            ],
            [],
        ]);

        expectError(polygon, "crosses-antimeridian", "has-holes", "not-closed");
    });
    it("errors on a ring that crosses itself", () => {
        const polygon = createPolygon([
            [
                [0, 0],
                [1, 1],
                [1, 0],
                [0, 1],
                [0, 0],
            ],
        ]);

        expectError(polygon, "self-intersects");
    });

    it("accepts a ring with a repeated vertex", () => {
        // A double-click while drawing repeats a vertex. The server's JTS check
        // accepts it, so rejecting it here would block a shape the API allows.
        const polygon = createPolygon([
            [
                [0, 0],
                [1, 0],
                [1, 0],
                [1, 1],
                [0, 1],
                [0, 0],
            ],
        ]);

        expect(parsePolygon(polygon).ok).toBe(true);
    });

    it("parses a valid polygon", () => {
        const polygon = createPolygon([
            [
                [170, 0],
                [175, 10],
                [175, 20],
                [170, 0],
            ],
        ]);

        expect(parsePolygon(polygon)).toEqual({ ok: true, result: polygon });
    });
});

describe("ring-self-intersection", () => {
    const close = (ring: GeoJSON.Position[]) => [...ring, [...ring[0]]];

    // Each case is pinned against JTS 1.20.0 IsValidOp, which is what the server
    // runs; a disagreement means the client blocks a shape the API would take.
    it.each([
        [
            "a square does not cross itself",
            close([
                [0, 0],
                [1, 0],
                [1, 1],
                [0, 1],
            ]),
            false,
        ],
        [
            "a triangle does not cross itself",
            close([
                [0, 0],
                [1, 0],
                [0.5, 1],
            ]),
            false,
        ],
        [
            "a bowtie crosses itself",
            close([
                [0, 0],
                [1, 1],
                [1, 0],
                [0, 1],
            ]),
            true,
        ],
        [
            "a collinear backtrack crosses itself",
            close([
                [0, 0],
                [2, 0],
                [1, 0],
                [1, 1],
            ]),
            true,
        ],
        [
            "a repeated vertex does not",
            close([
                [0, 0],
                [1, 0],
                [1, 0],
                [1, 1],
                [0, 1],
            ]),
            false,
        ],
        [
            "a doubled closing vertex does not",
            [
                [0, 0],
                [1, 0],
                [1, 1],
                [0, 1],
                [0, 0],
                [0, 0],
            ],
            false,
        ],
    ] as [string, GeoJSON.Position[], boolean][])("%s", (_, ring, crosses) => {
        expect(ringSelfIntersection(ring) !== null).toBe(crosses);
    });

    it("returns null rather than throwing for a degenerate ring", () => {
        expect(ringSelfIntersection([])).toBeNull();
        expect(
            ringSelfIntersection([
                [0, 0],
                [1, 1],
                [0, 0],
            ]),
        ).toBeNull();
        expect(
            ringSelfIntersection([
                [0, 0],
                [0, 0],
                [0, 0],
                [0, 0],
            ]),
        ).toBeNull();
    });
});

describe("polygon-from-positions", () => {
    const ringOf = (positions: readonly number[]) => polygonFromPositions(positions).coordinates[0];

    it("pairs the flat list into vertices and closes the ring", () => {
        expect(polygonFromPositions([170, 0, 175, 10, 175, 20])).toEqual({
            type: "Polygon",
            coordinates: [
                [
                    [170, 0],
                    [175, 10],
                    [175, 20],
                    [170, 0],
                ],
            ],
        });
    });

    it("closes with a copy of the first vertex, not the same reference", () => {
        const ring = ringOf([170, 0, 175, 10, 175, 20]);
        const last = ring[ring.length - 1];

        expect(last).toEqual(ring[0]);
        // A shared reference would let a later mutation of one end move the other,
        // silently unclosing the ring.
        expect(last).not.toBe(ring[0]);
    });

    it("drops a trailing coordinate with no pair", () => {
        expect(ringOf([170, 0, 175, 10, 175, 20, 99])).toEqual([
            [170, 0],
            [175, 10],
            [175, 20],
            [170, 0],
        ]);
    });

    it("returns an empty ring for no positions, with nothing to close", () => {
        expect(polygonFromPositions([])).toEqual({ type: "Polygon", coordinates: [[]] });
    });

    it("returns an empty ring when a lone coordinate cannot form a vertex", () => {
        expect(ringOf([170])).toEqual([]);
    });

    it("closes a single vertex onto itself", () => {
        expect(ringOf([170, 0])).toEqual([
            [170, 0],
            [170, 0],
        ]);
    });

    it("copies coordinates through without normalizing them", () => {
        // Construction never rejects or rewrites geometry — validation is parsePolygon's job.
        expect(ringOf([-122.4194, 37.7749, 190, -0.5])).toEqual([
            [-122.4194, 37.7749],
            [190, -0.5],
            [-122.4194, 37.7749],
        ]);
    });

    it("produces a polygon parsePolygon accepts", () => {
        const polygon = polygonFromPositions([170, 0, 175, 10, 175, 20]);

        expect(parsePolygon(polygon)).toEqual({ ok: true, result: polygon });
    });

    it("produces a too-few-vertices polygon from fewer than three vertices", () => {
        const res = parsePolygon(polygonFromPositions([170, 0, 175, 10]));

        expect(res.ok).toBe(false);
        expect((res as { errors: WatchboxPolygonError[] }).errors).toContain("too-few-vertices");
    });
});
