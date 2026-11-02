// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { SourceRef } from "@opencop/api";

/**
 * True when the dragged node resolves to a scalar (non-object) value for the given format.
 * Determines whether the ATTRIBUTES drop creates a scalar-entry rule (with attributeKey)
 * or a flat-map rule (no attributeKey).
 */
export function isScalarNode(nodeType: string, formatType: string): boolean {
    if (formatType === "json") return nodeType === "field";
    if (formatType === "xml" || formatType === "kml+kmz")
        return nodeType === "text" || nodeType === "attribute";
    // CSV: all columns are scalar
    return true;
}

/**
 * Derive a default attributeKey from the dragged node and its resolved source ref.
 *
 * JSONPath  /batteryLevel        → "batteryLevel"
 * JSONPath  /device/status       → "status"
 * XPath     Props/Battery/text() → "Battery"
 * XPath     @name                → "name"
 * csvColumn battery_level        → "battery_level"
 * csvColumn (index 3)            → "column_3"
 * Fallback: node.name
 */
export function deriveAttributeKey(nodeData: { name: string }, sourceRef: SourceRef): string {
    if (sourceRef.type === "jsonpath") {
        const parts = sourceRef.expr.split("/").filter(Boolean);
        return parts[parts.length - 1] ?? nodeData.name;
    }

    if (sourceRef.type === "xpath") {
        const stripped = sourceRef.expr.replace(/\/text\(\)$/, "").replace(/^@/, "");
        const parts = stripped.split("/").filter(Boolean);
        const last = parts[parts.length - 1] ?? nodeData.name;
        // Strip trailing predicate brackets like [0] or [@attr='x']
        return last.replace(/\[.*\]$/, "");
    }

    if (sourceRef.type === "csvColumn") {
        if (sourceRef.column) return sourceRef.column;
        if (sourceRef.index !== undefined) return `column_${sourceRef.index}`;
    }

    return nodeData.name;
}
