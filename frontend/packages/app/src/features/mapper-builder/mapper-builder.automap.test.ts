// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { FormatConfig, TargetField } from "@opencop/api";
import type { TreeNode } from "./mapper-builder.parsers";
import { autoMapFields, ERROR_TARGETS, hasUniqueValuesForExpr } from "./mapper-builder.automap";
import { parseJsonToTree } from "./mapper-builder.parsers";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function leaf(
    id: string,
    name: string,
    value?: string,
    nodeType: TreeNode["nodeType"] = "field",
): TreeNode {
    return { id, name, value, nodeType };
}

function treeWithRecord(recordId: string, leaves: TreeNode[]): TreeNode[] {
    return [{ id: recordId, name: "record", nodeType: "object", children: leaves }];
}

/** Run automap on a single leaf and return the first matched target (or undefined) */
function mapSingleField(
    name: string,
    value: string,
    format: FormatConfig = JSON_FORMAT,
    recordId = "root/0",
): TargetField | undefined {
    const tree = treeWithRecord(recordId, [leaf(`${recordId}/${name}`, name, value)]);
    const rules = autoMapFields(tree, recordId, format, new Set());
    return rules[0]?.target;
}

const JSON_FORMAT: FormatConfig = { type: "json", allowComments: false };
const XML_FORMAT: FormatConfig = { type: "xml", namespaces: {}, strict: false };
const CSV_FORMAT: FormatConfig = { type: "csv", delimiter: ",", quote: '"', hasHeader: true };

// ---------------------------------------------------------------------------
// autoMapFields
// ---------------------------------------------------------------------------

describe("autoMapFields", () => {
    it.each`
        fieldName      | value                                                     | expected
        ${"lat"}       | ${"45.0"}                                                 | ${"GEOMETRY_LAT"}
        ${"longitude"} | ${"90.0"}                                                 | ${"GEOMETRY_LON"}
        ${"name"}      | ${"Test"}                                                 | ${"DISPLAY_NAME"}
        ${"uid"}       | ${"550e8400-e29b-41d4-a716-446655440000"}                 | ${"OBJECT_ID"}
        ${"fill"}      | ${"#aabbcc"}                                              | ${"STYLE_FILL_COLOR"}
        ${"geom"}      | ${JSON.stringify({ type: "Point", coordinates: [0, 0] })} | ${"GEOMETRY"}
        ${"position"}  | ${"45.0, -93.0"}                                          | ${"GEOMETRY_POINT"}
        ${"position"}  | ${"18SUJ2337106519"}                                      | ${"GEOMETRY_POINT"}
        ${"speed"}     | ${"14.2"}                                                 | ${"ELEMENT_SPEED"}
        ${"velocity"}  | ${"8.0"}                                                  | ${"ELEMENT_SPEED"}
        ${"sog"}       | ${"6.5"}                                                  | ${"ELEMENT_SPEED"}
        ${"spd"}       | ${"12.0"}                                                 | ${"ELEMENT_SPEED"}
        ${"course"}    | ${"127.5"}                                                | ${"ELEMENT_COURSE"}
        ${"cog"}       | ${"45.0"}                                                 | ${"ELEMENT_COURSE"}
        ${"bearing"}   | ${"270.0"}                                                | ${"ELEMENT_COURSE"}
        ${"heading"}   | ${"130.0"}                                                | ${"ELEMENT_HEADING"}
        ${"hdg"}       | ${"265.0"}                                                | ${"ELEMENT_HEADING"}
        ${"azimuth"}   | ${"180.0"}                                                | ${"ELEMENT_HEADING"}
        ${"icon"}      | ${"https://example.com/icon.png"}                         | ${"ELEMENT_ICON_LOCATION"}
    `("maps '$fieldName' ($value) → $expected", ({ fieldName, value, expected }) => {
        expect(mapSingleField(fieldName, value)).toBe(expected);
    });

    // -----------------------------------------------------------------------
    // STALE_AT requires ISO date value
    // -----------------------------------------------------------------------

    describe("STALE_AT keyword gating", () => {
        it("maps 'stale' only when value is an ISO date", () => {
            expect(mapSingleField("stale", "2024-01-01T00:00:00Z")).toBe("STALE_AT");
            expect(mapSingleField("stale", "never")).toBeUndefined();
        });
    });

    // -----------------------------------------------------------------------
    // Geometry mutual exclusivity
    // -----------------------------------------------------------------------

    describe("geometry mutual exclusivity", () => {
        it("blocks GEOMETRY_LAT/LON when GEOMETRY_POINT is assigned", () => {
            const tree = treeWithRecord("root/0", [
                leaf("root/0/position", "position", "45.0, -93.0"),
                leaf("root/0/lat", "lat", "45.0"),
                leaf("root/0/lon", "lon", "-93.0"),
            ]);
            const targets = new Set(
                autoMapFields(tree, "root/0", JSON_FORMAT, new Set()).map((r) => r.target),
            );
            expect(targets.has("GEOMETRY_POINT")).toBe(true);
            expect(targets.has("GEOMETRY_LAT")).toBe(false);
            expect(targets.has("GEOMETRY_LON")).toBe(false);
        });

        it("blocks GEOMETRY_POINT/GEOMETRY when LAT+LON are assigned", () => {
            const tree = treeWithRecord("root/0", [
                leaf("root/0/lat", "lat", "45.0"),
                leaf("root/0/lon", "lon", "-93.0"),
                leaf("root/0/geometry", "geometry", "unused"),
            ]);
            const targets = new Set(
                autoMapFields(tree, "root/0", JSON_FORMAT, new Set()).map((r) => r.target),
            );
            expect(targets.has("GEOMETRY_LAT")).toBe(true);
            expect(targets.has("GEOMETRY_LON")).toBe(true);
            expect(targets.has("GEOMETRY_POINT")).toBe(false);
            expect(targets.has("GEOMETRY")).toBe(false);
        });
    });

    // -----------------------------------------------------------------------
    // Greedy assignment and exclusion
    // -----------------------------------------------------------------------

    it("assigns each source to at most one target", () => {
        const tree = treeWithRecord("root/0", [
            leaf("root/0/lat", "lat", "45.0"),
            leaf("root/0/lon", "lon", "-93.0"),
            leaf("root/0/name", "name", "Test"),
        ]);
        const rules = autoMapFields(tree, "root/0", JSON_FORMAT, new Set());
        const sourceExprs = rules.map((r) => (r.source.type === "jsonpath" ? r.source.expr : ""));
        expect(new Set(sourceExprs).size).toBe(sourceExprs.length);
    });

    it("respects excludeTargets", () => {
        expect(mapSingleField("lat", "45.0")).toBe("GEOMETRY_LAT");
        const tree = treeWithRecord("root/0", [leaf("root/0/lat", "lat", "45.0")]);
        const rules = autoMapFields(
            tree,
            "root/0",
            JSON_FORMAT,
            new Set<TargetField>(["GEOMETRY_LAT"]),
        );
        expect(rules.find((r) => r.target === "GEOMETRY_LAT")).toBeUndefined();
    });

    // -----------------------------------------------------------------------
    // OBJECT_ID uniqueness guard
    // -----------------------------------------------------------------------

    describe("OBJECT_ID uniqueness guard", () => {
        it("skips OBJECT_ID when field values are not unique across records", () => {
            const tree: TreeNode[] = [
                {
                    id: "records",
                    name: "records",
                    nodeType: "array",
                    children: [
                        {
                            id: "records/0",
                            name: "[0]",
                            nodeType: "object",
                            children: [
                                leaf(
                                    "records/0/candidate_id",
                                    "candidate_id",
                                    "10000000-0000-4000-8000-000000000001",
                                ),
                            ],
                        },
                        {
                            id: "records/1",
                            name: "[1]",
                            nodeType: "object",
                            children: [
                                leaf(
                                    "records/1/candidate_id",
                                    "candidate_id",
                                    "10000000-0000-4000-8000-000000000001",
                                ),
                            ],
                        },
                    ],
                },
            ];
            const rules = autoMapFields(tree, "records/0", JSON_FORMAT, new Set());
            expect(rules.find((r) => r.target === "OBJECT_ID")).toBeUndefined();
        });

        it("maps OBJECT_ID when field values are unique across records", () => {
            const tree: TreeNode[] = [
                {
                    id: "records",
                    name: "records",
                    nodeType: "array",
                    children: [
                        {
                            id: "records/0",
                            name: "[0]",
                            nodeType: "object",
                            children: [
                                leaf(
                                    "records/0/candidate_id",
                                    "candidate_id",
                                    "10000000-0000-4000-8000-000000000001",
                                ),
                            ],
                        },
                        {
                            id: "records/1",
                            name: "[1]",
                            nodeType: "object",
                            children: [
                                leaf(
                                    "records/1/candidate_id",
                                    "candidate_id",
                                    "20000000-0000-4000-8000-000000000002",
                                ),
                            ],
                        },
                    ],
                },
            ];
            const rules = autoMapFields(tree, "records/0", JSON_FORMAT, new Set());
            expect(rules.find((r) => r.target === "OBJECT_ID")).toBeDefined();
        });
    });

    it("maps all three motion fields from a combined source", () => {
        const tree = treeWithRecord("root/0", [
            leaf("root/0/lat", "lat", "36.85"),
            leaf("root/0/lon", "lon", "-75.97"),
            leaf("root/0/speed", "speed", "14.2"),
            leaf("root/0/course", "course", "127.5"),
            leaf("root/0/heading", "heading", "130.0"),
        ]);
        const targets = new Set(
            autoMapFields(tree, "root/0", JSON_FORMAT, new Set()).map((r) => r.target),
        );
        expect(targets.has("ELEMENT_SPEED")).toBe(true);
        expect(targets.has("ELEMENT_COURSE")).toBe(true);
        expect(targets.has("ELEMENT_HEADING")).toBe(true);
    });

    it("never maps to SOURCE_ID", () => {
        expect(mapSingleField("source", "550e8400-e29b-41d4-a716-446655440000")).not.toBe(
            "SOURCE_ID",
        );
    });

    // -----------------------------------------------------------------------
    // Generated rule properties: onMissing + transforms
    // -----------------------------------------------------------------------

    describe("generated rule properties", () => {
        it.each`
            fieldName | value                                     | target                   | onMissing    | defaultValue
            ${"lat"}  | ${"45.0"}                                 | ${"GEOMETRY_LAT"}        | ${"ERROR"}   | ${undefined}
            ${"uid"}  | ${"550e8400-e29b-41d4-a716-446655440000"} | ${"OBJECT_ID"}           | ${"ERROR"}   | ${undefined}
            ${"name"} | ${"Test"}                                 | ${"DISPLAY_NAME"}        | ${"DEFAULT"} | ${""}
            ${"sidc"} | ${"SFGPU"}                                | ${"ELEMENT_SYMBOL_SIDC"} | ${"NULL"}    | ${undefined}
        `(
            "sets onMissing=$onMissing for $target",
            ({ fieldName, value, target, onMissing, defaultValue }) => {
                const tree = treeWithRecord("root/0", [
                    leaf(`root/0/${fieldName}`, fieldName, value),
                ]);
                const rule = autoMapFields(tree, "root/0", JSON_FORMAT, new Set()).find(
                    (r) => r.target === target,
                );
                expect(rule?.onMissing).toBe(onMissing);
                expect(rule?.defaultValue).toBe(defaultValue);
            },
        );

        it("includes OBJECT_ID in the shared ERROR default needed for Watchbox eligibility", () => {
            expect(ERROR_TARGETS.has("OBJECT_ID")).toBe(true);
        });

        it.each`
            fieldName     | value                                                                                   | target              | expectedOp
            ${"updated"}  | ${"2024-01-01T00:00:00Z"}                                                               | ${"UPDATED_AT"}     | ${"PARSE_DATE_TIME"}
            ${"position"} | ${"18SUJ2337106519"}                                                                    | ${"GEOMETRY_POINT"} | ${"MGRS_TO_POINT"}
            ${"geom"}     | ${JSON.stringify({ type: "Polygon", coordinates: [[[0, 0], [1, 0], [1, 1], [0, 0]]] })} | ${"GEOMETRY"}       | ${"GEOMETRY"}
        `("adds $expectedOp transform for $target", ({ fieldName, value, target, expectedOp }) => {
            const tree = treeWithRecord("root/0", [leaf(`root/0/${fieldName}`, fieldName, value)]);
            const rule = autoMapFields(tree, "root/0", JSON_FORMAT, new Set()).find(
                (r) => r.target === target,
            );
            expect(rule?.transforms.map((t) => t.op)).toContain(expectedOp);
        });
    });

    // -----------------------------------------------------------------------
    // Format-specific source ref types
    // -----------------------------------------------------------------------

    it.each`
        format         | recordId            | leafId                     | leafNodeType | expectedType
        ${JSON_FORMAT} | ${"root/0"}         | ${"root/0/lat"}            | ${"field"}   | ${"jsonpath"}
        ${XML_FORMAT}  | ${"root/tracks[0]"} | ${"root/tracks[0]/lat[0]"} | ${"text"}    | ${"xpath"}
        ${CSV_FORMAT}  | ${"row-0"}          | ${"row-0/lat"}             | ${"field"}   | ${"csvColumn"}
    `(
        "generates $expectedType source for $format.type format",
        ({ format, recordId, leafId, leafNodeType, expectedType }) => {
            const tree = treeWithRecord(recordId, [leaf(leafId, "lat", "45.0", leafNodeType)]);
            const rules = autoMapFields(tree, recordId, format, new Set());
            expect(rules[0].source.type).toBe(expectedType);
        },
    );

    // -----------------------------------------------------------------------
    // Parsed JSON regression guard for non-unique UUIDs
    // -----------------------------------------------------------------------

    describe("non-unique UUID regression", () => {
        it("does not auto-map duplicate candidate IDs to OBJECT_ID", () => {
            const sampleJson = JSON.stringify([
                { candidate_id: "10000000-0000-4000-8000-000000000001" },
                { candidate_id: "10000000-0000-4000-8000-000000000001" },
            ]);

            const tree = parseJsonToTree(sampleJson);
            const rules = autoMapFields(tree, "root/0", JSON_FORMAT, new Set());
            const targets = new Set(rules.map((r) => r.target));

            expect(targets.has("OBJECT_ID")).toBe(false);
        });
    });

    // -----------------------------------------------------------------------
    // hasUniqueValuesForExpr — used by UI to warn on non-unique OBJECT_ID
    // -----------------------------------------------------------------------

    describe("hasUniqueValuesForExpr", () => {
        it("returns false for non-unique values across records", () => {
            const tree = parseJsonToTree(
                JSON.stringify([{ id: "aaa" }, { id: "aaa" }, { id: "bbb" }]),
            );
            expect(hasUniqueValuesForExpr(tree, "root/0", "/id")).toBe(false);
        });

        it("returns true for unique values across records", () => {
            const tree = parseJsonToTree(
                JSON.stringify([{ id: "aaa" }, { id: "bbb" }, { id: "ccc" }]),
            );
            expect(hasUniqueValuesForExpr(tree, "root/0", "/id")).toBe(true);
        });

        it("returns true for single record", () => {
            const tree = parseJsonToTree(JSON.stringify([{ id: "aaa" }]));
            expect(hasUniqueValuesForExpr(tree, "root/0", "/id")).toBe(true);
        });

        it("handles nested paths", () => {
            const tree = parseJsonToTree(
                JSON.stringify([{ attrs: { uid: "same" } }, { attrs: { uid: "same" } }]),
            );
            expect(hasUniqueValuesForExpr(tree, "root/0", "/attrs/uid")).toBe(false);
        });
    });
});
