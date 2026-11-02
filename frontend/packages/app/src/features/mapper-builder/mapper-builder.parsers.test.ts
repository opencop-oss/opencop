// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { CsvFormatConfig } from "@opencop/api";
import { parseCsvToTree, parseJsonToTree, parseXmlToTree } from "./mapper-builder.parsers";

// ---------------------------------------------------------------------------
// parseXmlToTree
// ---------------------------------------------------------------------------

describe("parseXmlToTree", () => {
    it("generates unique indexed IDs for sibling elements", () => {
        const tree = parseXmlToTree(
            "<root><track>a</track><track>b</track><track>c</track></root>",
        );
        const children = tree[0].children!;
        expect(children.map((c) => c.id)).toEqual([
            "root/track[0]",
            "root/track[1]",
            "root/track[2]",
        ]);
    });

    it("parses attributes as child nodes with @ prefix and correct IDs", () => {
        const tree = parseXmlToTree('<root id="123" type="test"><item>val</item></root>');
        const attrs = tree[0].children!.filter((c) => c.nodeType === "attribute");
        expect(attrs).toHaveLength(2);
        expect(attrs.find((a) => a.name === "@id")).toMatchObject({ id: "root/@id", value: "123" });
    });

    it("leaf text elements have value; parent elements do not", () => {
        const tree = parseXmlToTree("<root><parent><child>val</child></parent></root>");
        const parent = tree[0].children![0];
        const child = parent.children![0];
        expect(parent.value).toBeUndefined();
        expect(parent.nodeType).toBe("element");
        expect(child.value).toBe("val");
        expect(child.nodeType).toBe("text");
    });

    it("throws on malformed XML", () => {
        expect(() => parseXmlToTree("<root><unclosed>")).toThrow();
    });
});

// ---------------------------------------------------------------------------
// parseJsonToTree
// ---------------------------------------------------------------------------

describe("parseJsonToTree", () => {
    it("throws on malformed JSON", () => {
        expect(() => parseJsonToTree('{"records": [}')).toThrow();
    });

    it("produces correct nested ID paths through arrays and objects", () => {
        const tree = parseJsonToTree(JSON.stringify({ data: { items: [{ x: 1 }] } }));
        const x = tree[0].children![0].children![0].children![0].children![0];
        expect(x).toMatchObject({
            id: "root/data/items/0/x",
            name: "x",
            value: "1",
            nodeType: "field",
        });
    });

    it("root arrays are unwrapped — elements become top-level nodes", () => {
        const tree = parseJsonToTree(JSON.stringify([{ a: 1 }, { a: 2 }]));
        expect(tree).toHaveLength(2);
        expect(tree[0].id).toBe("root/0");
        expect(tree[0].nodeType).toBe("object");
        expect(tree[1].id).toBe("root/1");
    });
});

// ---------------------------------------------------------------------------
// parseCsvToTree
// ---------------------------------------------------------------------------

describe("parseCsvToTree", () => {
    const defaultConfig: CsvFormatConfig = {
        type: "csv",
        delimiter: ",",
        quote: '"',
        hasHeader: true,
    };

    it("with headers: column names as field names, row-N/columnName IDs", () => {
        const tree = parseCsvToTree("name,age\nAlice,30\nBob,25", defaultConfig);
        expect(tree).toHaveLength(2);
        expect(tree[0]).toMatchObject({ id: "row-0", name: "Row 1", nodeType: "object" });
        expect(tree[0].children![0]).toMatchObject({
            id: "row-0/name",
            name: "name",
            value: "Alice",
        });
    });

    it("without headers: Column N names, row-N/col-N IDs", () => {
        const tree = parseCsvToTree("Alice,30\nBob,25", { ...defaultConfig, hasHeader: false });
        expect(tree[0].children![0]).toMatchObject({
            id: "row-0/col-0",
            name: "Column 0",
            value: "Alice",
        });
    });

    it("preserves empty fields in tab-delimited records", () => {
        const tree = parseCsvToTree("name\tsymbol\nAlpha\t", {
            ...defaultConfig,
            delimiter: "\t",
        });
        expect(tree[0].children).toEqual([
            { id: "row-0/name", name: "name", value: "Alpha", nodeType: "field" },
            { id: "row-0/symbol", name: "symbol", value: "", nodeType: "field" },
        ]);
    });
});
