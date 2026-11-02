// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { CsvFormatConfig } from "@opencop/api";
import Papa from "papaparse";

/** Parsed tree node used for rendering, validation, automap, and source ref building */
export interface TreeNode {
    id: string;
    name: string;
    /** Display value (leaf nodes only) */
    value?: string;
    /** Node type hint for rendering */
    nodeType: "element" | "attribute" | "text" | "array" | "object" | "field";
    children?: TreeNode[];
}

// --- XML parsing (browser DOMParser) ---

export function parseXmlToTree(content: string): TreeNode[] {
    const parser = new DOMParser();
    const doc = parser.parseFromString(content, "application/xml");

    const parseError = doc.querySelector("parsererror");
    if (parseError) {
        // DOMParser wraps the real error in boilerplate like "This page contains
        // the following errors: … Below is a rendering of the page up to the first error."
        // Extract just the "error on line …" portion when possible.
        const raw = parseError.textContent ?? "";
        const match = raw.match(/error on line \d+.*?(?=\s*Below is a rendering|$)/i);
        throw new Error(match ? match[0] : raw.trim());
    }

    return [xmlNodeToTree(doc.documentElement, doc.documentElement.nodeName)];
}

function xmlNodeToTree(node: Element, idPrefix: string): TreeNode {
    const children: TreeNode[] = [];
    let textValue: string | undefined;

    // Track sibling name counts to produce unique IDs (e.g. track[0], track[1])
    const nameCount = new Map<string, number>();

    for (const child of Array.from(node.childNodes)) {
        if (child.nodeType === Node.ELEMENT_NODE) {
            const el = child as Element;
            const count = nameCount.get(el.nodeName) ?? 0;
            nameCount.set(el.nodeName, count + 1);
            const childId = `${idPrefix}/${el.nodeName}[${count}]`;
            children.push(xmlNodeToTree(el, childId));
        } else if (child.nodeType === Node.TEXT_NODE) {
            const text = child.textContent?.trim();
            if (text) {
                textValue = text;
            }
        }
    }

    // Add attributes as child nodes
    for (const attr of Array.from(node.attributes)) {
        children.push({
            id: `${idPrefix}/@${attr.name}`,
            name: `@${attr.name}`,
            value: attr.value,
            nodeType: "attribute",
        });
    }

    return {
        id: idPrefix,
        name: node.nodeName,
        value: children.length === 0 ? textValue : undefined,
        nodeType: children.length > 0 ? "element" : "text",
        children: children.length > 0 ? children : undefined,
    };
}

// --- JSON parsing ---

export function parseJsonToTree(content: string): TreeNode[] {
    const data = JSON.parse(content) as unknown;
    // Root array: unwrap elements as top-level nodes (like CSV rows)
    if (Array.isArray(data)) {
        return data.map((item, i) => jsonValueToTree(item, `[${i}]`, `root/${i}`));
    }
    return [jsonValueToTree(data, "root", "root")];
}

function jsonValueToTree(value: unknown, key: string, idPrefix: string): TreeNode {
    if (Array.isArray(value)) {
        return {
            id: idPrefix,
            name: key,
            nodeType: "array",
            children: value.map((item, i) => jsonValueToTree(item, `[${i}]`, `${idPrefix}/${i}`)),
        };
    }

    if (value !== null && typeof value === "object") {
        return {
            id: idPrefix,
            name: key,
            nodeType: "object",
            children: Object.entries(value as Record<string, unknown>).map(([k, v]) =>
                jsonValueToTree(v, k, `${idPrefix}/${k}`),
            ),
        };
    }

    return {
        id: idPrefix,
        name: key,
        value: String(value),
        nodeType: "field",
    };
}

// --- CSV parsing (papaparse) ---

export function parseCsvToTree(content: string, config: CsvFormatConfig): TreeNode[] {
    const result = Papa.parse(content, {
        delimiter: config.delimiter,
        quoteChar: config.quote,
        header: config.hasHeader,
        skipEmptyLines: true,
    });

    if (result.errors.length > 0 && result.data.length === 0) {
        throw new Error(`CSV parse error: ${result.errors[0].message}`);
    }

    if (config.hasHeader) {
        // result.data is Record<string, string>[]
        const rows = result.data as Record<string, string>[];
        return rows.map((row, i) => ({
            id: `row-${i}`,
            name: `Row ${i + 1}`,
            nodeType: "object" as const,
            children: Object.entries(row).map(([col, val]) => ({
                id: `row-${i}/${col}`,
                name: col,
                value: val,
                nodeType: "field" as const,
            })),
        }));
    }

    // No header — result.data is string[][]
    const rows = result.data as string[][];
    return rows.map((row, i) => ({
        id: `row-${i}`,
        name: `Row ${i + 1}`,
        nodeType: "object" as const,
        children: row.map((val, j) => ({
            id: `row-${i}/col-${j}`,
            name: `Column ${j}`,
            value: val,
            nodeType: "field" as const,
        })),
    }));
}
