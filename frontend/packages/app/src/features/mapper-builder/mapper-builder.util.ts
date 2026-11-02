// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type {
    CsvFormatConfig,
    DynamicMapper,
    FormatConfig,
    FormatType,
    MappingError,
    MappingRule,
    MappingWarning,
    RecordSelector,
    SourceRef,
    TargetField,
} from "@opencop/api";
import pako from "pako";
import Papa from "papaparse";
import type { TreeNode } from "./mapper-builder.parsers";
import { collectAllNodes, normalizeExprSegments, segmentsMatch } from "./mapper-builder.validation";

// --- Geometry mutual exclusivity ---

/** Lat/Lon discrete geometry targets — mutually exclusive with combined targets */
export const GEOMETRY_DISCRETE = new Set<TargetField>(["GEOMETRY_LAT", "GEOMETRY_LON"]);
/** Point/Geometry combined targets — mutually exclusive with discrete targets */
export const GEOMETRY_COMBINED = new Set<TargetField>(["GEOMETRY_POINT", "GEOMETRY"]);

// --- Format detection ---

const FORMAT_BY_EXTENSION: Record<string, FormatType> = {
    xml: "xml",
    json: "json",
    csv: "csv",
    kml: "kml+kmz",
    kmz: "kml+kmz",
};

export function detectFormat(file: File): FormatType | null {
    const ext = file.name.split(".").pop()?.toLowerCase() ?? "";
    return FORMAT_BY_EXTENSION[ext] ?? null;
}

// --- File reading ---

export function readFileAsText(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result as string);
        reader.onerror = () => reject(reader.error);
        reader.readAsText(file);
    });
}

/** Gzip-compress a file and return the result as a base64-encoded string. */
export async function fileToGzipBase64(file: File): Promise<string> {
    const arrayBuffer = await file.arrayBuffer();
    const compressed = pako.gzip(new Uint8Array(arrayBuffer));
    let binary = "";
    for (const b of compressed) {
        binary += String.fromCharCode(b);
    }
    return btoa(binary);
}

/** Decode a base64-encoded payload back to a UTF-8 string.
 * Tries gzip, then deflate/zlib, then plain UTF-8 — older profiles may use any of these. */
export function decodeGzipBase64(base64: string): string {
    const binaryStr = atob(base64);
    const bytes = new Uint8Array(binaryStr.length);
    for (let i = 0; i < binaryStr.length; i++) {
        bytes[i] = binaryStr.charCodeAt(i);
    }
    try {
        return pako.ungzip(bytes, { to: "string" });
    } catch {
        // not gzip
    }
    try {
        return pako.inflate(bytes, { to: "string" });
    } catch {
        // not deflate/zlib
    }
    return new TextDecoder().decode(bytes);
}

// --- XPath generation ---

function generateXPath(nodePath: string[]): string {
    return "/" + nodePath.join("/");
}

// --- JSON Pointer generation ---

/** Generate a JSON Pointer (RFC 6901) from a tree node path.
 *  Array indices are dropped (the backend iterates arrays automatically).
 *  e.g. ["root", "0", "epicenter_lat"] → "/epicenter_lat"
 *       ["root", "0"]                  → "" (empty pointer = record root) */
function generateJsonPointer(nodePath: string[]): string {
    const parts = nodePath
        .slice(1) // Skip "root"
        .filter((p) => !/^\[\d+\]$/.test(p) && !/^\d+$/.test(p)); // Drop array indices
    if (parts.length === 0) return "";
    return "/" + parts.join("/");
}

// --- Expression evaluation ---

/** Evaluate a JSON Pointer expression against parsed JSON data.
 *  Returns all matched values (iterating into arrays automatically). */
function evaluateJsonPointer(data: unknown, expr: string): unknown[] {
    const segments = expr === "" ? [] : expr.slice(1).split("/"); // strip leading "/"
    return collectValues(data, segments, 0);
}

function collectValues(current: unknown, segments: string[], idx: number): unknown[] {
    if (current == null) return [];

    // If current is an array, recurse into each element at the same segment depth
    if (Array.isArray(current)) {
        return current.flatMap((item) => collectValues(item, segments, idx));
    }

    // Consumed all segments — we've arrived
    if (idx >= segments.length) return [current];

    // Navigate into the next segment
    if (typeof current === "object") {
        const next = (current as Record<string, unknown>)[segments[idx]];
        return collectValues(next, segments, idx + 1);
    }

    return [];
}

function evaluateXPath(doc: Document, expr: string): Node[] {
    const resolver = doc.createNSResolver(doc.documentElement);
    const result = doc.evaluate(expr, doc, resolver, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
    const nodes: Node[] = [];
    for (let i = 0; i < result.snapshotLength; i++) {
        const node = result.snapshotItem(i);
        if (node) nodes.push(node);
    }
    return nodes;
}

// --- Default format config builders ---

function detectCsvFormat(content: string): CsvFormatConfig {
    const sniff = Papa.parse(content, { preview: 10, skipEmptyLines: true });
    const delimiter = sniff.meta.delimiter || ",";

    const sample = content.slice(0, 2000);
    const doubleQuotes = (sample.match(/"/g) || []).length;
    const singleQuotes = (sample.match(/'/g) || []).length;
    const quote = singleQuotes > doubleQuotes * 2 ? "'" : '"';

    const rows = sniff.data as string[][];
    let hasHeader = true;
    if (rows.length >= 2) {
        const firstRow = rows[0];
        const allNonNumeric = firstRow.every((v) => v !== "" && Number.isNaN(Number(v)));
        const allUnique = new Set(firstRow).size === firstRow.length;
        hasHeader = allNonNumeric && allUnique;
    }

    return { type: "csv", delimiter, quote, hasHeader };
}

function detectXmlNamespaces(content: string): Record<string, string> {
    const namespaces: Record<string, string> = {};
    try {
        const parser = new DOMParser();
        const doc = parser.parseFromString(content, "application/xml");
        if (doc.querySelector("parsererror")) return namespaces;

        const root = doc.documentElement;
        for (const attr of Array.from(root.attributes)) {
            if (attr.name.startsWith("xmlns:")) {
                const prefix = attr.name.slice(6);
                namespaces[prefix] = attr.value;
            }
        }
    } catch {
        // Ignore parse errors — return empty
    }
    return namespaces;
}

export function buildDefaultFormatConfig(format: FormatType, content?: string): FormatConfig {
    switch (format) {
        case "xml":
            return {
                type: "xml",
                namespaces: content ? detectXmlNamespaces(content) : {},
                strict: false,
            };
        case "json":
            return { type: "json", allowComments: false };
        case "csv":
            return content
                ? detectCsvFormat(content)
                : { type: "csv", delimiter: ",", quote: '"', hasHeader: true };
        case "kml+kmz":
            return {
                type: "kml+kmz",
                namespaces: content ? detectXmlNamespaces(content) : {},
                strict: false,
            };
    }
}

// --- Build DynamicMapper from builder state ---

interface DynamicMapperInput {
    formatConfig: FormatConfig | null;
    recordSelector: RecordSelector | null;
    rules: MappingRule[];
}

/** Returns false for ATTRIBUTES scalar rules with an empty key (incomplete, not ready to save/preview). */
export function isCompleteRule(r: MappingRule): boolean {
    return !(r.target === "ATTRIBUTES" && r.attributeKey !== undefined && !r.attributeKey);
}

export function buildDynamicMapper(state: DynamicMapperInput): DynamicMapper {
    if (!state.formatConfig || !state.recordSelector) {
        throw new Error("Cannot build DynamicMapper: missing formatConfig or recordSelector");
    }

    const rules = state.rules.filter(isCompleteRule);

    return {
        enabled: true,
        formatConfig: state.formatConfig,
        recordSelector: state.recordSelector,
        rules,
    };
}

// --- Tree path extraction ---

/** Get the path from a tree node ID, stripping index suffixes.
 *  e.g. "root/tracks[0]/track[2]" → ["root", "tracks", "track"] */
function getNodePath(nodeId: string): string[] {
    return nodeId.split("/").map((seg) => seg.replace(/\[\d+\]$/, ""));
}

/** Normalize index segments for structural comparison.
 *  Handles XML bracket indices ([0] → stripped), JSON bare numeric segments (0 → 0),
 *  and CSV row-N indices (row-5 → row-0). */
export function basePath(nodeId: string): string {
    return nodeId
        .split("/")
        .map((seg) => {
            if (/^\[\d+\]$/.test(seg)) return ""; // XML: strip bracket indices
            if (/^\d+$/.test(seg)) return "0"; // JSON: normalize bare numeric segments
            return seg.replace(/\[\d+\]/g, "").replace(/^row-\d+$/, "row-0"); // XML inline + CSV
        })
        .filter(Boolean)
        .join("/");
}

// --- Build SourceRef from tree node ---

/**
 * Build a SourceRef from a dragged tree node.
 *
 * When `recordNodeId` is provided, XPath/JSON expressions are generated
 * **relative to the record boundary** (for mapping rules). When omitted,
 * expressions are absolute (for the record selector itself).
 */
export function buildSourceRefFromNode(
    node: TreeNode,
    formatConfig: FormatConfig,
    sampleContent: string,
    parsedTreeLength: number,
    recordNodeId?: string | null,
): { source: SourceRef; matchedCount: number } | null {
    const nodePath = getNodePath(node.id);

    if (formatConfig.type === "xml" || formatConfig.type === "kml+kmz") {
        // Absolute XPath for evaluating match count
        const absoluteExpr = generateXPath(nodePath);
        const parser = new DOMParser();
        const doc = parser.parseFromString(sampleContent, "application/xml");
        const matchedCount = evaluateXPath(doc, absoluteExpr).length;

        // Store relative expression when inside a record boundary
        const expr = recordNodeId ? buildRelativeXPath(node, recordNodeId) : absoluteExpr;
        const source: SourceRef = { type: "xpath", expr };
        return { source, matchedCount };
    }

    if (formatConfig.type === "json") {
        // Absolute pointer for evaluating match count
        const absoluteExpr = generateJsonPointer(nodePath);
        const data = JSON.parse(sampleContent) as unknown;
        const matchedCount = evaluateJsonPointer(data, absoluteExpr).length;

        // Store relative expression when inside a record boundary
        const expr = recordNodeId ? buildRelativeJsonPointer(node, recordNodeId) : absoluteExpr;
        const source: SourceRef = { type: "jsonpath", expr };
        return { source, matchedCount };
    }

    if (node.nodeType === "object") {
        const source: SourceRef = { type: "csvColumn" };
        return { source, matchedCount: parsedTreeLength };
    }
    const source: SourceRef = { type: "csvColumn", column: node.name };
    return { source, matchedCount: parsedTreeLength };
}

/** Build a relative XPath expression from a node within a record boundary. */
export function buildRelativeXPath(node: TreeNode, recordNodeId: string): string {
    const recordBase = basePath(recordNodeId);
    const nodeBase = basePath(node.id);
    const relativePart = nodeBase.slice(recordBase.length);
    const segments = relativePart.split("/").filter(Boolean);

    if (node.nodeType === "attribute") {
        const attrName = segments[segments.length - 1];
        const parentPath = segments.slice(0, -1).join("/");
        return parentPath ? `${parentPath}/${attrName}` : attrName;
    }

    // Leaf element — use /text() to extract text content
    return segments.join("/") + "/text()";
}

/** Build a relative JSON Pointer from a node within a record boundary. */
export function buildRelativeJsonPointer(node: TreeNode, recordNodeId: string): string {
    const recordBase = basePath(recordNodeId);
    const nodeBase = basePath(node.id);
    const relativePart = nodeBase.slice(recordBase.length);
    const segments = relativePart.split("/").filter((p) => p && !/^\d+$/.test(p)); // Drop array indices

    // Preserve the original array index for leaf elements inside arrays
    // (e.g., coordinates/1 for latitude vs coordinates/0 for longitude).
    // basePath normalizes all indices to 0, so we recover the real index
    // from the raw node ID.
    const isLeaf = !node.children || node.children.length === 0;
    if (isLeaf) {
        const rawSegments = node.id.split("/");
        const lastRaw = rawSegments[rawSegments.length - 1];
        if (/^\d+$/.test(lastRaw)) {
            segments.push(lastRaw);
        }
    }

    if (segments.length === 0) return "";
    return "/" + segments.join("/");
}

// --- Frontend error/warning enrichment ---

/** Maps error/warning codes to the section key they belong to */
const CODE_SECTION_MAP: Record<string, string> = {
    // Geometry
    FEATURE_BUILDER_ERROR: "geometry",
    ELEMENT_ERROR: "geometry",
    // Symbol
    ICON_ELEMENT_ERROR: "symbol",
    // Media icon
    ICON_LOCATION_ERROR: "mediaIcon",
    // Style
    COLOR_FORMAT_ERROR: "style",
    FORCE_FIELD_DEFAULT: "style",
    // Time
    TIMESTAMP_DEFAULT: "time",
    DATE_TIME: "time",
    // Identity
    DISPLAY_NAME_ERROR: "identity",
    OBJECT_ID_NOT_MAPPED: "identity",
    OBJECT_ID_NOT_UNIQUE: "identity",
    // Generic (always have ruleId; section is fallback)
    ON_MISSING_ERROR: "attributes",
    RULE_ERROR: "attributes",
    ON_MISSING_DEFAULT: "attributes",
    RULE_DISABLED: "attributes",
};

/** Enriched issue shape: derives singular ruleId from backend ruleIds array, adds sectionKey */
export type EnrichedIssue<T> = T & { ruleId?: string; sectionKey?: string };

/** Enriches errors: derives ruleId from ruleIds[0] and adds sectionKey when possible */
export function enrichErrors(errors: MappingError[]): EnrichedIssue<MappingError>[] {
    return errors.map((err) => {
        const ruleId = err.ruleIds?.[0];
        const sectionKey = CODE_SECTION_MAP[err.code];
        return { ...err, ruleId, ...(sectionKey && { sectionKey }) };
    });
}

/** Enriches warnings: derives ruleId from ruleIds[0] and adds sectionKey when possible */
export function enrichWarnings(warnings: MappingWarning[]): EnrichedIssue<MappingWarning>[] {
    return warnings.map((w) => {
        const ruleId = w.ruleIds?.[0];
        const sectionKey = CODE_SECTION_MAP[w.code];
        return { ...w, ruleId, ...(sectionKey && { sectionKey }) };
    });
}

/** Partition enriched issues into per-rule and section-level buckets.
 *  Items with a ruleId go to ruleMap; others with sectionKey go to sectionKeys. */
export function partitionIssues<
    T extends { ruleId?: string; sectionKey?: string; message: string },
>(items: T[]): { ruleMap: Record<string, string>; sectionKeys: string[] } {
    const ruleMap: Record<string, string> = {};
    const sectionKeys = new Set<string>();
    for (const item of items) {
        if (item.ruleId) {
            ruleMap[item.ruleId] = item.message;
        } else if (item.sectionKey) {
            sectionKeys.add(item.sectionKey);
        }
    }
    return { ruleMap, sectionKeys: [...sectionKeys].sort() };
}

/** Extract the source expression string from a SourceRef (jsonpath/xpath/csvColumn) */
export function getExprFromSource(src: SourceRef): string | undefined {
    if (src.type === "jsonpath" || src.type === "xpath") return src.expr;
    if (src.type === "csvColumn") return src.column;
    return undefined;
}

/**
 * Walk the parsed tree and return the first node whose normalized path matches
 * the record selector expression. Used in edit mode to reconstruct recordNodeId
 * from the persisted recordSelector.source.expr after the tree is parsed.
 *
 * Returns null if no match is found (e.g. the sample file has changed).
 */
export function findNodeForRecordSelector(
    recordSelector: RecordSelector,
    parsedTree: TreeNode[],
): TreeNode | null {
    const src = recordSelector.source;

    // CSV: record is always the row level — find the first object node
    if (src.type === "csvColumn") {
        const allNodes = collectAllNodes(parsedTree);
        return allNodes.find((n) => n.nodeType === "object") ?? null;
    }

    if (src.type !== "xpath" && src.type !== "jsonpath") return null;

    const exprSegments = normalizeExprSegments(src.expr);
    if (exprSegments.length === 0) return null;

    const allNodes = collectAllNodes(parsedTree);
    for (const node of allNodes) {
        const nodeSegments = basePath(node.id).split("/").filter(Boolean);
        if (segmentsMatch(exprSegments, nodeSegments)) return node;
    }

    return null;
}
