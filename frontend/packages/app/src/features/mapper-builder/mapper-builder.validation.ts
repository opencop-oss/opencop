// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MappingRule } from "@opencop/api";
import type { TreeNode } from "./mapper-builder.parsers";
import { basePath } from "./mapper-builder.util";

// --- Record selector validation -----------------------------------------

/** Check whether the record boundary node still exists in the parsed tree. */
export function validateRecordSelector(recordNodeId: string, parsedTree: TreeNode[]): boolean {
    const target = basePath(recordNodeId);

    function walk(node: TreeNode): boolean {
        if (basePath(node.id) === target) return true;
        return node.children?.some(walk) ?? false;
    }

    return parsedTree.some(walk);
}

// --- Rule validation ----------------------------------------------------

/**
 * Validate each rule's source expression against the current tree and record
 * boundary. Returns a map of ruleId → i18n error key. Rules without errors
 * are omitted from the map.
 */
export function validateRules(
    rules: MappingRule[],
    parsedTree: TreeNode[],
    recordNodeId: string | null,
): Record<string, string> {
    if (parsedTree.length === 0 || !recordNodeId) return {};

    const errors: Record<string, string> = {};

    for (const rule of rules) {
        if (!rule.id) continue;
        const error = validateRule(rule, parsedTree, recordNodeId);
        if (error) errors[rule.id] = error;
    }

    return errors;
}

function validateRule(
    rule: MappingRule,
    parsedTree: TreeNode[],
    recordNodeId: string,
): string | null {
    const src = rule.source;

    // Constant sources are validated locally in RuleCard (on blur)
    if (src.type === "const") return null;

    if (src.type === "csvColumn") {
        return validateCsvColumn(src.column, src.index, parsedTree, recordNodeId);
    }

    // Validate xpath/jsonpath expressions against the tree regardless of format match.
    // If the source type doesn't match the current format (e.g., jsonpath rule with XML file),
    // the expression won't match any tree nodes and will correctly report an error.
    if (src.type === "xpath" || src.type === "jsonpath") {
        return validateExpressionInTree(src.expr, parsedTree, recordNodeId);
    }

    return null;
}

// --- CSV validation -----------------------------------------------------

function validateCsvColumn(
    column: string | undefined,
    index: number | undefined,
    parsedTree: TreeNode[],
    recordNodeId: string,
): string | null {
    const leafNames = collectLeafFieldNames(parsedTree, recordNodeId);

    if (column !== undefined) {
        if (!leafNames.has(column)) return "mapperBuilder.validation.sourceNodeNotFound";
    } else if (index !== undefined) {
        // For index-based CSV columns, check if the index is within range
        const firstRow = parsedTree[0];
        const childCount = firstRow?.children?.length ?? 0;
        if (index >= childCount) return "mapperBuilder.validation.sourceNodeNotFound";
    }

    return null;
}

// --- Tree-based expression validation -----------------------------------

/**
 * Validate a source expression (XPath or JSON Pointer) against the parsed tree.
 * These expressions are relative to the record boundary, so we check if any
 * leaf field within the record scope has a matching path.
 *
 * Returns:
 * - null if the expression resolves to a field inside the record
 * - "sourceNodeOutsideRecord" if it matches a field outside the record
 * - "sourceNodeNotFound" if it doesn't match any field
 */
function validateExpressionInTree(
    expr: string,
    parsedTree: TreeNode[],
    recordNodeId: string,
): string | null {
    // Check if expression matches a leaf inside the record scope
    if (isExpressionInsideRecord(expr, parsedTree, recordNodeId)) {
        return null;
    }

    // Check if expression matches any leaf at all (outside record)
    if (isExpressionInTree(expr, parsedTree)) {
        return "mapperBuilder.validation.sourceNodeOutsideRecord";
    }

    return "mapperBuilder.validation.sourceNodeNotFound";
}

// --- Helpers ------------------------------------------------------------

/**
 * Check if a source expression (relative path) maps to a node inside the
 * record boundary. For xpath/jsonpath rules, the expression is relative to
 * the record element, so we check if any node within the record scope
 * has a matching path suffix. This includes non-leaf nodes (e.g. an object
 * like "geometry") since the backend can extract entire subtrees.
 */
function isExpressionInsideRecord(
    expr: string,
    parsedTree: TreeNode[],
    recordNodeId: string,
): boolean {
    const recordBase = basePath(recordNodeId);
    const nodes = collectAllNodes(parsedTree);

    const exprSegments = normalizeExprSegments(expr);
    if (exprSegments.length === 0) return true; // Root-level expression

    for (const node of nodes) {
        const nodeBase = basePath(node.id);
        // Node must be a descendant of the record
        if (!nodeBase.startsWith(recordBase + "/")) continue;

        // Get relative path from record
        const relPath = nodeBase.slice(recordBase.length + 1);
        const relSegments = relPath.split("/").filter(Boolean);

        // Check if the expression segments match the tail of the relative path
        if (segmentsMatch(exprSegments, relSegments)) return true;
    }

    return false;
}

/** Check if a source expression matches any node in the entire tree. */
function isExpressionInTree(expr: string, parsedTree: TreeNode[]): boolean {
    const nodes = collectAllNodes(parsedTree);
    const exprSegments = normalizeExprSegments(expr);
    if (exprSegments.length === 0) return true;

    for (const node of nodes) {
        const nodeBase = basePath(node.id);
        const nodeSegments = nodeBase.split("/").filter(Boolean);
        if (segmentsMatch(exprSegments, nodeSegments)) return true;
    }

    return false;
}

/** Normalize an expression to comparable path segments. */
export function normalizeExprSegments(expr: string): string[] {
    return expr
        .replace(/\/text\(\)$/, "") // Strip /text() suffix for XML
        .replace(/^@/, "") // Strip leading @ for attributes
        .split("/")
        .filter(Boolean);
}

export function segmentsMatch(exprSegments: string[], nodeSegments: string[]): boolean {
    if (exprSegments.length > nodeSegments.length) return false;

    // Compare from the end — the expression is a relative path
    const offset = nodeSegments.length - exprSegments.length;
    for (let i = 0; i < exprSegments.length; i++) {
        const exprSeg = exprSegments[i].replace(/\[\d+\]/g, "").toLowerCase();
        const nodeSeg = nodeSegments[i + offset].replace(/\[\d+\]/g, "").toLowerCase();
        // Both segments are numeric array indices — treat as structural match.
        // basePath normalizes all indices to "0", so any numeric expression
        // segment (e.g. "1") should match the normalized node segment ("0").
        if (/^\d+$/.test(exprSeg) && /^\d+$/.test(nodeSeg)) continue;
        if (exprSeg !== nodeSeg) return false;
    }

    return true;
}

/** Collect all leaf node names within the record scope. */
function collectLeafFieldNames(tree: TreeNode[], recordNodeId: string): Set<string> {
    const recordBase = basePath(recordNodeId);
    const names = new Set<string>();

    function walk(node: TreeNode) {
        const nodeBase = basePath(node.id);
        const isDescendant = nodeBase.startsWith(recordBase + "/");

        if (isDescendant && (!node.children || node.children.length === 0)) {
            names.add(node.name);
        }

        node.children?.forEach(walk);
    }

    tree.forEach(walk);
    return names;
}

/** Collect all nodes from the tree (regardless of record scope). */
export function collectAllNodes(tree: TreeNode[]): TreeNode[] {
    const nodes: TreeNode[] = [];

    function walk(node: TreeNode) {
        nodes.push(node);
        node.children?.forEach(walk);
    }

    tree.forEach(walk);
    return nodes;
}
