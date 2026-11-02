// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type {
    FormatConfig,
    MappingRule,
    OnMissing,
    SourceRef,
    TargetField,
    TransformStep,
} from "@opencop/api";
import { TARGET_FIELD_META } from "@opencop/api";
import { v4 as uuid } from "uuid";
import type { TreeNode } from "./mapper-builder.parsers";
import {
    basePath,
    buildRelativeJsonPointer,
    buildRelativeXPath,
    GEOMETRY_COMBINED,
    GEOMETRY_DISCRETE,
} from "./mapper-builder.util";

/** Keyword → target field mapping for name-based matching */
const KEYWORD_MAP: Record<string, TargetField> = {
    lat: "GEOMETRY_LAT",
    latitude: "GEOMETRY_LAT",
    lon: "GEOMETRY_LON",
    lng: "GEOMETRY_LON",
    longitude: "GEOMETRY_LON",
    point: "GEOMETRY_POINT",
    location: "GEOMETRY_POINT",
    position: "GEOMETRY_POINT",
    coordinates: "GEOMETRY_POINT",
    coord: "GEOMETRY_POINT",
    coords: "GEOMETRY_POINT",
    geom: "GEOMETRY",
    geometry: "GEOMETRY",
    name: "DISPLAY_NAME",
    displayname: "DISPLAY_NAME",
    title: "DISPLAY_NAME",
    label: "DISPLAY_NAME",
    id: "OBJECT_ID",
    objectid: "OBJECT_ID",
    uid: "OBJECT_ID",
    updated: "UPDATED_AT",
    updatedat: "UPDATED_AT",
    timestamp: "UPDATED_AT",
    time: "UPDATED_AT",
    stale: "STALE_AT",
    staleat: "STALE_AT",
    expiry: "STALE_AT",
    expire: "STALE_AT",
    expires: "STALE_AT",
    expiration: "STALE_AT",
    remove: "STALE_AT",
    removeat: "STALE_AT",
    delete: "STALE_AT",
    deleteat: "STALE_AT",
    start: "START_AT",
    startat: "START_AT",
    begin: "START_AT",
    color: "ELEMENT_POINT_COLOR",
    pointcolor: "ELEMENT_POINT_COLOR",
    sidc: "ELEMENT_SYMBOL_SIDC",
    symbol: "ELEMENT_SYMBOL_SIDC",
    icon: "ELEMENT_ICON_LOCATION",
    stroke: "STYLE_STROKE_COLOR",
    strokecolor: "STYLE_STROKE_COLOR",
    strokewidth: "STYLE_STROKE_WIDTH",
    linewidth: "STYLE_STROKE_WIDTH",
    fill: "STYLE_FILL_COLOR",
    fillcolor: "STYLE_FILL_COLOR",
    fillopacity: "STYLE_FILL_OPACITY",
    opacity: "STYLE_FILL_OPACITY",
    styleurl: "RAW_STYLE_URL",
    source: "SOURCE_ID",
    sourceid: "SOURCE_ID",
    speed: "ELEMENT_SPEED",
    velocity: "ELEMENT_SPEED",
    spd: "ELEMENT_SPEED",
    sog: "ELEMENT_SPEED",
    groundspeed: "ELEMENT_SPEED",
    course: "ELEMENT_COURSE",
    bearing: "ELEMENT_COURSE",
    cog: "ELEMENT_COURSE",
    heading: "ELEMENT_HEADING",
    hdg: "ELEMENT_HEADING",
    azimuth: "ELEMENT_HEADING",
};

/**
 * Targets whose newly created rules default to ERROR onMissing.
 * OBJECT_ID uses this default because ERROR is one requirement for Watchbox eligibility.
 */
export const ERROR_TARGETS: Set<TargetField> = new Set([
    "GEOMETRY",
    "GEOMETRY_LAT",
    "GEOMETRY_LON",
    "GEOMETRY_POINT",
    "OBJECT_ID",
]);

/** Targets that use DEFAULT onMissing */
export const DEFAULT_TARGETS: Map<TargetField, string> = new Map([["DISPLAY_NAME", ""]]);

/** Minimum score threshold for auto-mapping */
const MIN_SCORE = 1;

/** Normalize a field name for comparison: strip XML attribute prefix (@), remove non-alphanumeric chars, lowercase */
function normalizeFieldName(name: string): string {
    return name
        .replace(/^@/, "")
        .replace(/[^a-z0-9]/gi, "")
        .toLowerCase();
}

// --- Value-type heuristic detectors ---

function looksLikeFloat(val: string): number | null {
    const n = Number(val.trim());
    return !Number.isNaN(n) && Number.isFinite(n) ? n : null;
}

function looksLikeIsoDate(val: string): boolean {
    return /^\d{4}-\d{2}-\d{2}[T ]/.test(val.trim());
}

function looksLikeUuid(val: string): boolean {
    return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(val.trim());
}

function looksLikeHexColor(val: string): boolean {
    return /^#([0-9a-f]{6}|[0-9a-f]{8})$/i.test(val.trim());
}

function looksLikeCoordinatePair(val: string): boolean {
    const trimmed = val.trim();
    // "lat,lon" or "lat, lon"
    if (/^-?\d+\.?\d*\s*,\s*-?\d+\.?\d*$/.test(trimmed)) return true;
    // WKT POINT(...)
    if (/^POINT\s*\(/i.test(trimmed)) return true;
    return false;
}

function looksLikeMgrs(val: string): boolean {
    // MGRS: e.g. "18SUJ2337106519" — zone + grid letters + easting/northing
    return /^\d{1,2}[A-Z]{3}\d{4,10}$/i.test(val.trim());
}

function looksLikeGeoJson(val: string): boolean {
    const trimmed = val.trim();
    if (!trimmed.startsWith("{")) return false;
    try {
        const parsed = JSON.parse(trimmed) as Record<string, unknown>;
        return (
            typeof parsed.type === "string" &&
            [
                "Point",
                "LineString",
                "Polygon",
                "MultiPoint",
                "MultiLineString",
                "MultiPolygon",
                "GeometryCollection",
            ].includes(parsed.type) &&
            ("coordinates" in parsed || "geometries" in parsed)
        );
    } catch {
        return false;
    }
}

// --- Scoring ---

/** Score a source field against a target field */
function scoreMatch(
    fieldName: string,
    fieldValue: string | undefined,
    target: TargetField,
): number {
    const norm = normalizeFieldName(fieldName);
    let score = 0;

    // Name-based scoring
    const keywordTarget = KEYWORD_MAP[norm];
    if (keywordTarget === target) {
        // STALE_AT requires the value to actually look like a date
        if (target === "STALE_AT") {
            if (fieldValue && looksLikeIsoDate(fieldValue)) {
                score += 10;
            }
        } else {
            score += 10; // Exact keyword match
        }
    } else {
        // Partial match: check if the normalized target contains the field name or vice versa
        const targetNorm = normalizeFieldName(target);
        if (targetNorm.includes(norm) || norm.includes(targetNorm)) {
            score += 3;
        }
    }

    // Value-type heuristic scoring (bonus)
    if (fieldValue !== undefined) {
        const val = fieldValue.trim();
        if (val.length > 0) {
            const num = looksLikeFloat(val);
            if (num !== null) {
                if (target === "GEOMETRY_LAT" && Math.abs(num) <= 90) score += 2;
                if (target === "GEOMETRY_LON" && Math.abs(num) <= 180) score += 2;
            }
            if (looksLikeGeoJson(val) && target === "GEOMETRY") score += 8;
            if (looksLikeCoordinatePair(val) && target === "GEOMETRY_POINT") score += 5;
            if (looksLikeMgrs(val) && target === "GEOMETRY_POINT") score += 5;
            if (looksLikeIsoDate(val) && (target === "UPDATED_AT" || target === "START_AT"))
                score += 3;
            if (looksLikeUuid(val) && (target === "OBJECT_ID" || target === "SOURCE_ID"))
                score += 3;
            if (
                looksLikeHexColor(val) &&
                (target === "ELEMENT_POINT_COLOR" ||
                    target === "STYLE_STROKE_COLOR" ||
                    target === "STYLE_FILL_COLOR")
            )
                score += 3;
        }
    }

    return score;
}

// --- Transform and onMissing builders ---

/** Build transforms for a given target field */
function buildTransforms(target: TargetField, fieldValue: string | undefined): TransformStep[] {
    const meta = TARGET_FIELD_META[target];
    const transforms: TransformStep[] = [];

    // Always TRIM for string/numeric/instant types
    if (["STRING", "DOUBLE", "INSTANT", "UUID", "POINT"].includes(meta.valueType)) {
        transforms.push({ op: "TRIM" });
    }

    // PARSE_DATE_TIME for instant targets
    if (meta.valueType === "INSTANT") {
        transforms.push({ op: "PARSE_DATE_TIME" });
    }

    // MGRS_TO_POINT if value looks like MGRS
    if (target === "GEOMETRY_POINT" && fieldValue && looksLikeMgrs(fieldValue)) {
        transforms.push({ op: "MGRS_TO_POINT" });
    }

    // Add GEOMETRY transform to parse GeoJSON string
    if (target === "GEOMETRY" && fieldValue && looksLikeGeoJson(fieldValue)) {
        transforms.push({ op: "GEOMETRY" });
    }

    return transforms;
}

/** Build onMissing and defaultValue for a target */
function buildOnMissing(target: TargetField): { onMissing: OnMissing; defaultValue?: string } {
    if (ERROR_TARGETS.has(target)) return { onMissing: "ERROR" };
    const defaultVal = DEFAULT_TARGETS.get(target);
    if (defaultVal !== undefined) return { onMissing: "DEFAULT", defaultValue: defaultVal };
    return { onMissing: "NULL" };
}

// --- Tree traversal helpers ---

/**
 * Collect all values for a given field across every record in the sample tree.
 * Uses basePath to match the same structural field across different record instances
 * (e.g., records/0/candidate_id and records/1/candidate_id share basePath
 * records/0/candidate_id).
 */
function collectValuesAcrossRecords(tree: TreeNode[], fieldBasePath: string): string[] {
    const values: string[] = [];

    function walk(node: TreeNode) {
        if (basePath(node.id) === fieldBasePath && node.value !== undefined) {
            values.push(node.value);
        }
        if (node.children) {
            for (const child of node.children) walk(child);
        }
    }

    for (const root of tree) walk(root);
    return values;
}

/** Check whether a field's values are unique across all sample records */
function hasUniqueValues(tree: TreeNode[], node: TreeNode): boolean {
    const fieldBP = basePath(node.id);
    const values = collectValuesAcrossRecords(tree, fieldBP);
    if (values.length <= 1) return true;
    return new Set(values).size === values.length;
}

/**
 * Get total and unique value counts for a source expression across all sample records —
 * the concrete numbers behind the non-unique OBJECT_ID warning.
 */
export function getValueCountsForExpr(
    tree: TreeNode[],
    recordNodeId: string,
    sourceExpr: string,
): { total: number; unique: number } {
    const exprSegments = sourceExpr
        .replace(/\/text\(\)$/, "") // Strip XML /text() suffix
        .split("/")
        .filter(Boolean);
    if (exprSegments.length === 0) return { total: 0, unique: 0 };
    const fieldBP = basePath(recordNodeId) + "/" + exprSegments.join("/");
    const values = collectValuesAcrossRecords(tree, fieldBP);
    return { total: values.length, unique: new Set(values).size };
}

/**
 * Check whether a source expression resolves to unique values across all sample records.
 * @internal Exported for tests — production code uses getValueCountsForExpr directly.
 */
export function hasUniqueValuesForExpr(
    tree: TreeNode[],
    recordNodeId: string,
    sourceExpr: string,
): boolean {
    const { total, unique } = getValueCountsForExpr(tree, recordNodeId, sourceExpr);
    return total <= 1 || unique === total;
}

/** Collect leaf field nodes within the record scope */
function collectLeafFields(tree: TreeNode[], recordNodeId: string): TreeNode[] {
    const recordBase = basePath(recordNodeId);
    const leaves: TreeNode[] = [];

    function walk(node: TreeNode) {
        const nodeBase = basePath(node.id);
        const isDescendant = nodeBase.startsWith(recordBase + "/");

        if (isDescendant && (!node.children || node.children.length === 0)) {
            leaves.push(node);
        }

        if (node.children) {
            for (const child of node.children) {
                walk(child);
            }
        }
    }

    for (const root of tree) {
        walk(root);
    }

    return leaves;
}

/** Build a SourceRef for a leaf field node, using the same expression builders as manual drag */
function buildSourceRefForField(
    node: TreeNode,
    recordNodeId: string,
    formatConfig: FormatConfig,
): SourceRef {
    if (formatConfig.type === "csv") {
        return { type: "csvColumn", column: node.name };
    }
    if (formatConfig.type === "xml" || formatConfig.type === "kml+kmz") {
        return { type: "xpath", expr: buildRelativeXPath(node, recordNodeId) };
    }
    return { type: "jsonpath", expr: buildRelativeJsonPointer(node, recordNodeId) };
}

// --- Auto-map engine ---

/**
 * Auto-map source fields to target fields based on name similarity and value heuristics.
 * Returns mapping rules for fields that could be confidently matched.
 */
export function autoMapFields(
    tree: TreeNode[],
    recordNodeId: string,
    formatConfig: FormatConfig,
    excludeTargets: Set<TargetField>,
): MappingRule[] {
    const leaves = collectLeafFields(tree, recordNodeId);
    const allTargets = Object.keys(TARGET_FIELD_META) as TargetField[];

    // Build all (source, target, score) candidates
    const candidates: { node: TreeNode; target: TargetField; score: number }[] = [];

    for (const leaf of leaves) {
        for (const target of allTargets) {
            if (excludeTargets.has(target)) continue;
            // Skip SOURCE_ID — always overridden by the system
            if (target === "SOURCE_ID") continue;

            const score = scoreMatch(leaf.name, leaf.value, target);
            if (score >= MIN_SCORE) {
                candidates.push({ node: leaf, target, score });
            }
        }
    }

    // Sort by score descending
    candidates.sort((a, b) => b.score - a.score);

    // Greedy assignment — seed usedTargets with excludeTargets so mutual exclusivity
    // checks respect pre-existing rules (e.g. GEOMETRY already mapped blocks LAT/LON)
    const usedSources = new Set<string>();
    const usedTargets = new Set<TargetField>(excludeTargets);
    const rules: MappingRule[] = [];

    for (const { node, target } of candidates) {
        if (usedSources.has(node.id) || usedTargets.has(target)) continue;

        // OBJECT_ID must have unique values across all sample records;
        // non-unique values cause records to collapse into fewer events.
        if (target === "OBJECT_ID" && !hasUniqueValues(tree, node)) continue;

        // Mutual exclusivity: if GEOMETRY_POINT is assigned, skip LAT/LON and vice versa
        if (GEOMETRY_COMBINED.has(target)) {
            if ([...GEOMETRY_DISCRETE].some((t) => usedTargets.has(t))) continue;
        }
        if (GEOMETRY_DISCRETE.has(target)) {
            if ([...GEOMETRY_COMBINED].some((t) => usedTargets.has(t))) continue;
        }

        const { onMissing, defaultValue } = buildOnMissing(target);
        const source = buildSourceRefForField(node, recordNodeId, formatConfig);

        const rule: MappingRule = {
            id: uuid(),
            enabled: true,
            target,
            source,
            transforms: buildTransforms(target, node.value),
            onMissing,
            defaultValue,
        };

        rules.push(rule);
        usedSources.add(node.id);
        usedTargets.add(target);

        // Enforce mutual exclusivity after assignment
        if (GEOMETRY_COMBINED.has(target)) {
            for (const t of GEOMETRY_DISCRETE) usedTargets.add(t);
            // Also block other COMBINED targets (GEOMETRY vs GEOMETRY_POINT are redundant)
            for (const t of GEOMETRY_COMBINED) usedTargets.add(t);
        }
        if (
            GEOMETRY_DISCRETE.has(target) &&
            [...GEOMETRY_DISCRETE].every((t) => usedTargets.has(t))
        ) {
            for (const t of GEOMETRY_COMBINED) usedTargets.add(t);
        }
    }

    return rules;
}
