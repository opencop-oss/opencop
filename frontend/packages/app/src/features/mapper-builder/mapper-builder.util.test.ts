// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it } from "vitest";
import type { FormatConfig, RecordSelector } from "@opencop/api";
import pako from "pako";
import type { TreeNode } from "./mapper-builder.parsers";
import {
    basePath,
    buildDefaultFormatConfig,
    buildSourceRefFromNode,
    decodeGzipBase64,
    detectFormat,
    enrichErrors,
    enrichWarnings,
    findNodeForRecordSelector,
    partitionIssues,
} from "./mapper-builder.util";

// ---------------------------------------------------------------------------
// decodeGzipBase64
// ---------------------------------------------------------------------------

describe("decodeGzipBase64", () => {
    it("round-trips gzip-compressed base64 back to the original string", () => {
        const original = "<tracks><track><lat>45</lat></track></tracks>";
        const compressed = pako.gzip(original);
        let binary = "";
        for (const b of compressed) binary += String.fromCharCode(b);
        const encoded = btoa(binary);
        expect(decodeGzipBase64(encoded)).toBe(original);
    });

    it("falls back to plain UTF-8 for non-compressed base64 input", () => {
        const plain = "this is not gzip data";
        const encoded = btoa(plain);
        expect(decodeGzipBase64(encoded)).toBe(plain);
    });
});

// ---------------------------------------------------------------------------
// findNodeForRecordSelector
// ---------------------------------------------------------------------------

describe("findNodeForRecordSelector", () => {
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
                    children: [
                        {
                            id: "tracks/track[0]/lat[0]",
                            name: "lat",
                            value: "45",
                            nodeType: "text",
                        },
                    ],
                },
            ],
        },
    ];

    it("finds a matching XML node by xpath expression", () => {
        const selector: RecordSelector = {
            source: { type: "xpath", expr: "/tracks/track" },
            onMissing: "NULL",
        };
        const node = findNodeForRecordSelector(selector, xmlTree);
        expect(node?.id).toBe("tracks/track[0]");
    });

    it("returns null when expression matches nothing", () => {
        const selector: RecordSelector = {
            source: { type: "xpath", expr: "/missing/path" },
            onMissing: "NULL",
        };
        expect(findNodeForRecordSelector(selector, xmlTree)).toBeNull();
    });

    it("finds a JSON node by jsonpath expression", () => {
        const jsonTree: TreeNode[] = [
            {
                id: "root",
                name: "root",
                nodeType: "object",
                children: [
                    {
                        id: "root/items",
                        name: "items",
                        nodeType: "array",
                        children: [
                            { id: "root/items/0", name: "[0]", nodeType: "object", children: [] },
                        ],
                    },
                ],
            },
        ];
        const selector: RecordSelector = {
            source: { type: "jsonpath", expr: "/items" },
            onMissing: "NULL",
        };
        const node = findNodeForRecordSelector(selector, jsonTree);
        expect(node?.id).toBe("root/items");
    });

    it("returns the first object node for CSV selectors", () => {
        const csvTree: TreeNode[] = [
            { id: "row-0", name: "Row 1", nodeType: "object", children: [] },
        ];
        const selector: RecordSelector = { source: { type: "csvColumn" }, onMissing: "NULL" };
        const node = findNodeForRecordSelector(selector, csvTree);
        expect(node?.id).toBe("row-0");
    });

    it("returns null for empty tree", () => {
        const selector: RecordSelector = {
            source: { type: "xpath", expr: "/tracks/track" },
            onMissing: "NULL",
        };
        expect(findNodeForRecordSelector(selector, [])).toBeNull();
    });
});

// ---------------------------------------------------------------------------
// basePath
// ---------------------------------------------------------------------------

describe("basePath", () => {
    it.each`
        input                           | expected
        ${"root/tracks[0]/track[2]"}    | ${"root/tracks/track"}
        ${"root/0/lat"}                 | ${"root/0/lat"}
        ${"root/5/lat"}                 | ${"root/0/lat"}
        ${"row-5/name"}                 | ${"row-0/name"}
        ${"root/items[0]/item[3]/name"} | ${"root/items/item/name"}
        ${"root/[0]/child"}             | ${"root/child"}
        ${"root/track[0]/@id"}          | ${"root/track/@id"}
    `("$input → $expected", ({ input, expected }) => {
        expect(basePath(input)).toBe(expected);
    });
});

// ---------------------------------------------------------------------------
// detectFormat
// ---------------------------------------------------------------------------

describe("detectFormat", () => {
    it.each`
        filename       | expected
        ${"data.xml"}  | ${"xml"}
        ${"data.json"} | ${"json"}
        ${"data.csv"}  | ${"csv"}
        ${"data.kml"}  | ${"kml+kmz"}
        ${"data.kmz"}  | ${"kml+kmz"}
        ${"data.txt"}  | ${null}
    `("detects $filename as $expected", ({ filename, expected }) => {
        expect(detectFormat(new File([""], filename))).toBe(expected);
    });
});

// ---------------------------------------------------------------------------
// buildDefaultFormatConfig
// ---------------------------------------------------------------------------

describe("buildDefaultFormatConfig", () => {
    it("detects CSV delimiter and header from content", () => {
        const config = buildDefaultFormatConfig("csv", "name\tage\nAlice\t30\nBob\t25");
        expect(config).toMatchObject({ type: "csv", delimiter: "\t", hasHeader: true });
    });

    it("detects no header when first row is all-numeric", () => {
        const config = buildDefaultFormatConfig("csv", "1,2,3\n4,5,6\n7,8,9");
        if (config.type === "csv") expect(config.hasHeader).toBe(false);
    });

    it("detects XML namespaces from content", () => {
        const config = buildDefaultFormatConfig(
            "xml",
            '<root xmlns:geo="http://example.com/geo"><item/></root>',
        );
        if (config.type === "xml")
            expect(config.namespaces).toEqual({ geo: "http://example.com/geo" });
    });
});

// ---------------------------------------------------------------------------
// buildSourceRefFromNode
// ---------------------------------------------------------------------------

describe("buildSourceRefFromNode", () => {
    // --- XML (requires mocked XPath APIs — happy-dom lacks them) ---

    describe("XML format", () => {
        const xmlConfig: FormatConfig = { type: "xml", namespaces: {}, strict: false };
        const xmlContent = "<tracks><track><lat>45</lat><lon>-93</lon></track></tracks>";
        let originalDOMParser: typeof DOMParser;

        function mockXPathApis(matchCount: number) {
            const fakeResult = {
                snapshotLength: matchCount,
                snapshotItem: (i: number) => (i < matchCount ? ({} as Node) : null),
            };
            const RealDOMParser = originalDOMParser;
            globalThis.DOMParser = class extends RealDOMParser {
                override parseFromString(content: string, type: DOMParserSupportedType) {
                    const doc = super.parseFromString(content, type);
                    (doc as unknown as Record<string, unknown>).createNSResolver = () => null;
                    (doc as unknown as Record<string, unknown>).evaluate = () => fakeResult;
                    return doc;
                }
            };
        }

        beforeEach(() => {
            originalDOMParser = globalThis.DOMParser;
            if (!("XPathResult" in globalThis)) {
                (globalThis as Record<string, unknown>).XPathResult = {
                    ORDERED_NODE_SNAPSHOT_TYPE: 7,
                };
            }
            mockXPathApis(1);
        });
        afterEach(() => {
            globalThis.DOMParser = originalDOMParser;
        });

        it("generates absolute xpath when no recordNodeId", () => {
            const node: TreeNode = {
                id: "tracks/track[0]/lat[0]",
                name: "lat",
                value: "45",
                nodeType: "text",
            };
            const result = buildSourceRefFromNode(node, xmlConfig, xmlContent, 0);
            expect(result!.source).toEqual({ type: "xpath", expr: "/tracks/track/lat" });
        });

        it("generates relative xpath with /text() when recordNodeId is provided", () => {
            const node: TreeNode = {
                id: "tracks/track[0]/lat[0]",
                name: "lat",
                value: "45",
                nodeType: "text",
            };
            const result = buildSourceRefFromNode(
                node,
                xmlConfig,
                xmlContent,
                0,
                "tracks/track[0]",
            );
            expect(result!.source).toEqual({ type: "xpath", expr: "lat/text()" });
        });

        it("generates relative xpath for attributes (no /text())", () => {
            const node: TreeNode = {
                id: "tracks/track[0]/@id",
                name: "@id",
                value: "abc",
                nodeType: "attribute",
            };
            const result = buildSourceRefFromNode(
                node,
                xmlConfig,
                xmlContent,
                0,
                "tracks/track[0]",
            );
            expect(result!.source).toEqual({ type: "xpath", expr: "@id" });
        });

        it("returns matchedCount from XPath evaluation", () => {
            mockXPathApis(3);
            const node: TreeNode = {
                id: "tracks/track[0]",
                name: "track",
                nodeType: "element",
                children: [],
            };
            expect(buildSourceRefFromNode(node, xmlConfig, xmlContent, 0)!.matchedCount).toBe(3);
        });
    });

    // --- JSON ---

    describe("JSON format", () => {
        const jsonConfig: FormatConfig = { type: "json", allowComments: false };
        const jsonContent = JSON.stringify({ items: [{ lat: 45, lon: -93 }] });

        it("generates absolute jsonpath (drops root and array indices)", () => {
            const node: TreeNode = {
                id: "root/items/0/lat",
                name: "lat",
                value: "45",
                nodeType: "field",
            };
            const result = buildSourceRefFromNode(node, jsonConfig, jsonContent, 0);
            expect(result!.source).toEqual({ type: "jsonpath", expr: "/items/lat" });
            expect(result!.matchedCount).toBeGreaterThanOrEqual(1);
        });

        it("generates relative jsonpath when recordNodeId is provided", () => {
            const node: TreeNode = {
                id: "root/items/0/lat",
                name: "lat",
                value: "45",
                nodeType: "field",
            };
            const result = buildSourceRefFromNode(node, jsonConfig, jsonContent, 0, "root/items/0");
            expect(result!.source).toEqual({ type: "jsonpath", expr: "/lat" });
        });

        it("preserves leaf array index in relative jsonpath (coordinates/1)", () => {
            const geoContent = JSON.stringify({
                features: [{ geometry: { coordinates: [-93.0, 45.0] } }],
            });
            const node: TreeNode = {
                id: "root/features/0/geometry/coordinates/1",
                name: "[1]",
                value: "45.0",
                nodeType: "field",
            };
            const result = buildSourceRefFromNode(
                node,
                jsonConfig,
                geoContent,
                0,
                "root/features/0",
            );
            expect(result!.source).toEqual({ type: "jsonpath", expr: "/geometry/coordinates/1" });
        });

        it("preserves leaf array index 0 in relative jsonpath (coordinates/0)", () => {
            const geoContent = JSON.stringify({
                features: [{ geometry: { coordinates: [-93.0, 45.0] } }],
            });
            const node: TreeNode = {
                id: "root/features/0/geometry/coordinates/0",
                name: "[0]",
                value: "-93.0",
                nodeType: "field",
            };
            const result = buildSourceRefFromNode(
                node,
                jsonConfig,
                geoContent,
                0,
                "root/features/0",
            );
            expect(result!.source).toEqual({ type: "jsonpath", expr: "/geometry/coordinates/0" });
        });

        it("generates relative jsonpath for non-leaf object node (geometry)", () => {
            const geoContent = JSON.stringify({
                features: [{ geometry: { type: "Point", coordinates: [-93.0, 45.0] } }],
            });
            const node: TreeNode = {
                id: "root/features/0/geometry",
                name: "geometry",
                nodeType: "object",
                children: [
                    {
                        id: "root/features/0/geometry/type",
                        name: "type",
                        value: "Point",
                        nodeType: "field",
                    },
                ],
            };
            const result = buildSourceRefFromNode(
                node,
                jsonConfig,
                geoContent,
                0,
                "root/features/0",
            );
            expect(result!.source).toEqual({ type: "jsonpath", expr: "/geometry" });
        });
    });

    // --- CSV ---

    describe("CSV format", () => {
        const csvConfig: FormatConfig = {
            type: "csv",
            delimiter: ",",
            quote: '"',
            hasHeader: true,
        };

        it("generates csvColumn with column name for leaf field", () => {
            const node: TreeNode = { id: "row-0/lat", name: "lat", value: "45", nodeType: "field" };
            const result = buildSourceRefFromNode(node, csvConfig, "", 10);
            expect(result!.source).toEqual({ type: "csvColumn", column: "lat" });
            expect(result!.matchedCount).toBe(10);
        });

        it("generates csvColumn without column for row-level node", () => {
            const node: TreeNode = { id: "row-0", name: "Row 1", nodeType: "object", children: [] };
            expect(buildSourceRefFromNode(node, csvConfig, "", 10)!.source).toEqual({
                type: "csvColumn",
            });
        });
    });
});

// ---------------------------------------------------------------------------
// enrichErrors / enrichWarnings
// ---------------------------------------------------------------------------

describe("enrichErrors", () => {
    it("maps known error codes to section keys, passes through unknown", () => {
        const result = enrichErrors([
            { code: "FEATURE_BUILDER_ERROR", message: "bad geom" },
            { code: "ICON_ELEMENT_ERROR", message: "bad symbol" },
            { code: "ICON_LOCATION_ERROR", message: "bad icon location" },
            { code: "UNKNOWN_CODE", message: "something" },
        ]);
        expect(result[0].sectionKey).toBe("geometry");
        expect(result[1].sectionKey).toBe("symbol");
        expect(result[2].sectionKey).toBe("mediaIcon");
        expect(result[3].sectionKey).toBeUndefined();
    });

    it("maps all error codes from the unified CODE_SECTION_MAP", () => {
        const result = enrichErrors([
            { code: "ELEMENT_ERROR", message: "" },
            { code: "COLOR_FORMAT_ERROR", message: "" },
            { code: "ON_MISSING_ERROR", message: "" },
            { code: "RULE_ERROR", message: "" },
        ]);
        expect(result[0].sectionKey).toBe("geometry");
        expect(result[1].sectionKey).toBe("style");
        expect(result[2].sectionKey).toBe("attributes");
        expect(result[3].sectionKey).toBe("attributes");
    });
});

describe("enrichWarnings", () => {
    it("maps known warning codes to section keys, passes through unknown", () => {
        const result = enrichWarnings([
            { code: "FORCE_FIELD_DEFAULT", message: "defaulted" },
            { code: "SOME_OTHER", message: "ok" },
        ]);
        expect(result[0].sectionKey).toBe("style");
        expect(result[1].sectionKey).toBeUndefined();
    });

    it("maps all warning codes from the unified CODE_SECTION_MAP", () => {
        const result = enrichWarnings([
            { code: "TIMESTAMP_DEFAULT", message: "" },
            { code: "DATE_TIME", message: "" },
            { code: "DISPLAY_NAME_ERROR", message: "" },
            { code: "ON_MISSING_DEFAULT", message: "" },
            { code: "RULE_DISABLED", message: "" },
        ]);
        expect(result[0].sectionKey).toBe("time");
        expect(result[1].sectionKey).toBe("time");
        expect(result[2].sectionKey).toBe("identity");
        expect(result[3].sectionKey).toBe("attributes");
        expect(result[4].sectionKey).toBe("attributes");
    });
});

// ---------------------------------------------------------------------------
// partitionIssues
// ---------------------------------------------------------------------------

describe("partitionIssues", () => {
    it("routes items with ruleId to ruleMap", () => {
        const { ruleMap, sectionKeys } = partitionIssues([
            { ruleId: "r1", sectionKey: "style", message: "msg1" },
            { ruleId: "r2", message: "msg2" },
        ]);
        expect(ruleMap).toEqual({ r1: "msg1", r2: "msg2" });
        expect(sectionKeys).toEqual([]);
    });

    it("routes items without ruleId but with sectionKey to sectionKeys", () => {
        const { ruleMap, sectionKeys } = partitionIssues([
            { sectionKey: "geometry", message: "geo err" },
            { sectionKey: "style", message: "style err" },
        ]);
        expect(ruleMap).toEqual({});
        expect(sectionKeys).toEqual(["geometry", "style"]);
    });

    it("ignores items with neither ruleId nor sectionKey", () => {
        const { ruleMap, sectionKeys } = partitionIssues([{ message: "format error" }]);
        expect(ruleMap).toEqual({});
        expect(sectionKeys).toEqual([]);
    });

    it("deduplicates sectionKeys and sorts them", () => {
        const { sectionKeys } = partitionIssues([
            { sectionKey: "style", message: "a" },
            { sectionKey: "geometry", message: "b" },
            { sectionKey: "style", message: "c" },
        ]);
        expect(sectionKeys).toEqual(["geometry", "style"]);
    });

    it("prefers ruleId over sectionKey (item has both)", () => {
        const { ruleMap, sectionKeys } = partitionIssues([
            { ruleId: "r1", sectionKey: "time", message: "msg" },
        ]);
        expect(ruleMap).toEqual({ r1: "msg" });
        expect(sectionKeys).toEqual([]);
    });
});
