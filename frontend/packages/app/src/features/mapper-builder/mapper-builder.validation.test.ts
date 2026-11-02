// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { MappingRule } from "@opencop/api";
import type { TreeNode } from "./mapper-builder.parsers";
import { validateRecordSelector, validateRules } from "./mapper-builder.validation";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function leaf(id: string, name: string, value?: string): TreeNode {
    return { id, name, value, nodeType: "field" };
}

function makeTree(): TreeNode[] {
    return [
        {
            id: "root",
            name: "root",
            nodeType: "object",
            children: [
                {
                    id: "root/0",
                    name: "[0]",
                    nodeType: "object",
                    children: [
                        leaf("root/0/lat", "lat", "45.0"),
                        leaf("root/0/lon", "lon", "-93.0"),
                        leaf("root/0/name", "name", "Test"),
                    ],
                },
            ],
        },
    ];
}

function rule(
    overrides: Partial<MappingRule> & Pick<MappingRule, "id" | "target" | "source">,
): MappingRule {
    return { enabled: true, transforms: [], onMissing: "NULL", ...overrides };
}

// ---------------------------------------------------------------------------
// validateRecordSelector
// ---------------------------------------------------------------------------

describe("validateRecordSelector", () => {
    it("returns true when node exists, false when path is absent", () => {
        expect(validateRecordSelector("root/0", makeTree())).toBe(true);
        expect(validateRecordSelector("root/0/missing", makeTree())).toBe(false);
    });

    it("matches by basePath (ignores index differences)", () => {
        // "root/1" normalizes the same as "root/0" (bare numeric → "0")
        expect(validateRecordSelector("root/1", makeTree())).toBe(true);
    });
});

// ---------------------------------------------------------------------------
// validateRules
// ---------------------------------------------------------------------------

describe("validateRules", () => {
    it("returns empty map for empty tree or null recordNodeId", () => {
        const r = rule({
            id: "r1",
            target: "GEOMETRY_LAT",
            source: { type: "jsonpath", expr: "/lat" },
        });
        expect(validateRules([r], [], "root/0")).toEqual({});
        expect(validateRules([r], makeTree(), null)).toEqual({});
    });

    describe("jsonpath rules", () => {
        it("valid inside record → no error; nonexistent → sourceNodeNotFound", () => {
            const good = rule({
                id: "r1",
                target: "GEOMETRY_LAT",
                source: { type: "jsonpath", expr: "/lat" },
            });
            const bad = rule({
                id: "r2",
                target: "GEOMETRY_LAT",
                source: { type: "jsonpath", expr: "/nope" },
            });
            const errors = validateRules([good, bad], makeTree(), "root/0");
            expect(errors["r1"]).toBeUndefined();
            expect(errors["r2"]).toBe("mapperBuilder.validation.sourceNodeNotFound");
        });
    });

    describe("xpath rules", () => {
        const xmlTree: TreeNode[] = [
            {
                id: "tracks",
                name: "tracks",
                nodeType: "element",
                children: [
                    {
                        id: "tracks/track[0]",
                        name: "track",
                        nodeType: "element",
                        children: [leaf("tracks/track[0]/lat[0]", "lat", "45.0")],
                    },
                ],
            },
        ];

        it("valid inside record → no error", () => {
            const r = rule({
                id: "r1",
                target: "GEOMETRY_LAT",
                source: { type: "xpath", expr: "lat/text()" },
            });
            expect(validateRules([r], xmlTree, "tracks/track[0]")).toEqual({});
        });

        it("field outside record boundary → sourceNodeOutsideRecord", () => {
            const tree: TreeNode[] = [
                {
                    id: "root",
                    name: "root",
                    nodeType: "element",
                    children: [
                        leaf("root/metadata[0]", "metadata", "some-value"),
                        {
                            id: "root/items[0]",
                            name: "items",
                            nodeType: "element",
                            children: [
                                {
                                    id: "root/items[0]/item[0]",
                                    name: "item",
                                    nodeType: "element",
                                    children: [leaf("root/items[0]/item[0]/lat[0]", "lat", "45")],
                                },
                            ],
                        },
                    ],
                },
            ];
            const r = rule({
                id: "r1",
                target: "DISPLAY_NAME",
                source: { type: "xpath", expr: "metadata/text()" },
            });
            expect(validateRules([r], tree, "root/items[0]/item[0]")["r1"]).toBe(
                "mapperBuilder.validation.sourceNodeOutsideRecord",
            );
        });
    });

    describe("csvColumn rules", () => {
        const csvTree: TreeNode[] = [
            {
                id: "row-0",
                name: "Row 1",
                nodeType: "object",
                children: [leaf("row-0/lat", "lat", "45.0"), leaf("row-0/lon", "lon", "-93.0")],
            },
        ];

        it("valid column → no error; unknown column → sourceNodeNotFound", () => {
            const good = rule({
                id: "r1",
                target: "GEOMETRY_LAT",
                source: { type: "csvColumn", column: "lat" },
            });
            const bad = rule({
                id: "r2",
                target: "GEOMETRY_LAT",
                source: { type: "csvColumn", column: "altitude" },
            });
            const errors = validateRules([good, bad], csvTree, "row-0");
            expect(errors["r1"]).toBeUndefined();
            expect(errors["r2"]).toBe("mapperBuilder.validation.sourceNodeNotFound");
        });

        it("out-of-range column index → sourceNodeNotFound", () => {
            const r = rule({
                id: "r1",
                target: "GEOMETRY_LAT",
                source: { type: "csvColumn", index: 99 },
            });
            expect(validateRules([r], csvTree, "row-0")["r1"]).toBe(
                "mapperBuilder.validation.sourceNodeNotFound",
            );
        });
    });

    it("const source always passes validation", () => {
        const r = rule({
            id: "r1",
            target: "DISPLAY_NAME",
            source: { type: "const", value: "fixed" },
        });
        expect(validateRules([r], makeTree(), "root/0")).toEqual({});
    });

    describe("non-leaf node expressions (jsonpath)", () => {
        const geoTree: TreeNode[] = [
            {
                id: "root",
                name: "root",
                nodeType: "object",
                children: [
                    {
                        id: "root/0",
                        name: "[0]",
                        nodeType: "object",
                        children: [
                            {
                                id: "root/0/geometry",
                                name: "geometry",
                                nodeType: "object",
                                children: [
                                    {
                                        id: "root/0/geometry/coordinates",
                                        name: "coordinates",
                                        nodeType: "array",
                                        children: [
                                            leaf("root/0/geometry/coordinates/0", "[0]", "-93.0"),
                                            leaf("root/0/geometry/coordinates/1", "[1]", "45.0"),
                                        ],
                                    },
                                ],
                            },
                            leaf("root/0/name", "name", "Test"),
                        ],
                    },
                ],
            },
        ];

        it("non-leaf object expression (/geometry) is valid inside record", () => {
            const r = rule({
                id: "r1",
                target: "GEOMETRY",
                source: { type: "jsonpath", expr: "/geometry" },
            });
            expect(validateRules([r], geoTree, "root/0")).toEqual({});
        });

        it("numeric array index expression (/coordinates/1) is valid inside record", () => {
            const r = rule({
                id: "r1",
                target: "GEOMETRY_LAT",
                source: { type: "jsonpath", expr: "/coordinates/1" },
            });
            expect(validateRules([r], geoTree, "root/0/geometry")).toEqual({});
        });

        it("numeric array index expression (/coordinates/0) is valid inside record", () => {
            const r = rule({
                id: "r1",
                target: "GEOMETRY_LON",
                source: { type: "jsonpath", expr: "/coordinates/0" },
            });
            expect(validateRules([r], geoTree, "root/0/geometry")).toEqual({});
        });
    });
});
