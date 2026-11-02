// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

// --- String literal union types ---

export type OnMissing = "NULL" | "DEFAULT" | "ERROR";

export type MapperFormat = "XML" | "JSON" | "CSV" | "KML_KMZ";

export type ValueType =
    | "STRING"
    | "UUID"
    | "INT"
    | "LONG"
    | "DOUBLE"
    | "BOOLEAN"
    | "INSTANT"
    | "OBJECT"
    | "ARRAY"
    | "POINT"
    | "GEOMETRY"
    | "MAP";

export type TargetField =
    | "GEOMETRY"
    | "GEOMETRY_LAT"
    | "GEOMETRY_LON"
    | "GEOMETRY_POINT"
    | "ELEMENT_SPEED"
    | "ELEMENT_COURSE"
    | "ELEMENT_HEADING"
    | "ELEMENT_POINT_COLOR"
    | "ELEMENT_SYMBOL_SIDC"
    | "ELEMENT_ICON_LOCATION"
    | "UPDATED_AT"
    | "STALE_AT"
    | "START_AT"
    | "DISPLAY_NAME"
    | "OBJECT_ID"
    | "SOURCE_ID"
    | "STYLE_STROKE_STYLE"
    | "STYLE_STROKE_COLOR"
    | "STYLE_STROKE_WIDTH"
    | "STYLE_FILL_COLOR"
    | "STYLE_FILL_OPACITY"
    | "RAW_STYLE_URL"
    | "ATTRIBUTES";

/** Maps TargetField → its JSON path and expected value type */
export const TARGET_FIELD_META: Record<TargetField, { path: string; valueType: ValueType }> = {
    GEOMETRY: { path: "geometry", valueType: "GEOMETRY" },
    GEOMETRY_LAT: { path: "geometry.lat", valueType: "DOUBLE" },
    GEOMETRY_LON: { path: "geometry.lon", valueType: "DOUBLE" },
    GEOMETRY_POINT: { path: "geometry.point", valueType: "POINT" },
    ELEMENT_SPEED: { path: "properties.element.speed", valueType: "DOUBLE" },
    ELEMENT_COURSE: { path: "properties.element.course", valueType: "DOUBLE" },
    ELEMENT_HEADING: { path: "properties.element.heading", valueType: "DOUBLE" },
    ELEMENT_POINT_COLOR: { path: "element.point.color", valueType: "STRING" },
    ELEMENT_SYMBOL_SIDC: { path: "element.symbol.sidc", valueType: "STRING" },
    ELEMENT_ICON_LOCATION: { path: "element.icon.location", valueType: "STRING" },
    UPDATED_AT: { path: "properties.updatedAt", valueType: "INSTANT" },
    STALE_AT: { path: "properties.staleAt", valueType: "INSTANT" },
    START_AT: { path: "properties.startAt", valueType: "INSTANT" },
    DISPLAY_NAME: { path: "properties.displayName", valueType: "STRING" },
    OBJECT_ID: { path: "properties.objectId", valueType: "STRING" },
    SOURCE_ID: { path: "properties.source", valueType: "UUID" },
    STYLE_STROKE_STYLE: { path: "properties.style.strokeStyle", valueType: "STRING" },
    STYLE_STROKE_COLOR: { path: "properties.style.strokeColor", valueType: "STRING" },
    STYLE_STROKE_WIDTH: { path: "properties.style.strokeWidth", valueType: "DOUBLE" },
    STYLE_FILL_COLOR: { path: "properties.style.fillColor", valueType: "STRING" },
    STYLE_FILL_OPACITY: { path: "properties.style.fillOpacity", valueType: "DOUBLE" },
    RAW_STYLE_URL: { path: "properties.rawStyleUrl", valueType: "STRING" },
    ATTRIBUTES: { path: "properties.attributes", valueType: "MAP" },
};

export type TransformOperation =
    | "TRIM"
    | "LOWER"
    | "UPPER"
    | "REPLACE_REGEX"
    | "TO_INT"
    | "TO_DOUBLE"
    | "PARSE_DATE_TIME"
    | "DEFAULT_NOW"
    | "ADD_DURATION"
    | "DEFAULT_IF_BLANK"
    | "MAP_VALUES"
    | "MGRS_TO_POINT"
    | "GEOMETRY"
    | "KPH_TO_MPS"
    | "MPH_TO_MPS"
    | "KNOTS_TO_MPS"
    | "FPS_TO_MPS"
    | "RAD_TO_DEGREES"
    | "GRAD_TO_DEGREES"
    | "MILS_TO_DEGREES";

// --- Format configs (discriminated union on "type") ---

export interface XmlFormatConfig {
    type: "xml";
    namespaces: Record<string, string>;
    strict: boolean;
}

export interface JsonFormatConfig {
    type: "json";
    allowComments: boolean;
}

export interface CsvFormatConfig {
    type: "csv";
    delimiter: string;
    quote: string;
    hasHeader: boolean;
}

export interface KmlFormatConfig {
    type: "kml+kmz";
    namespaces: Record<string, string>;
    strict: boolean;
}

export type FormatConfig = XmlFormatConfig | JsonFormatConfig | CsvFormatConfig | KmlFormatConfig;

export type FormatType = FormatConfig["type"];

// --- Source refs (discriminated union on "type") ---

export interface XPathSource {
    type: "xpath";
    expr: string;
}

export interface JsonPathSource {
    type: "jsonpath";
    expr: string;
}

export interface CsvColumnSource {
    type: "csvColumn";
    column?: string;
    index?: number;
}

export interface ConstSource {
    type: "const";
    value: unknown;
}

export type SourceRef = XPathSource | JsonPathSource | CsvColumnSource | ConstSource;

// --- Transform ---

export interface TransformStep {
    op: TransformOperation;
    pattern?: string;
    replacement?: string;
    format?: string;
    value?: string;
    mapValues?: Record<string, string>;
    mapDefault?: string;
}

// --- Mapping rules ---

export interface MappingRule {
    id?: string;
    enabled: boolean;
    /** Serialized as the TargetField name string (e.g. "GEOMETRY_LAT") */
    target: TargetField;
    /** Scalar-entry mode only (target === "ATTRIBUTES"): the key under properties.attributes */
    attributeKey?: string;
    contextRef?: string;
    source: SourceRef;
    transforms: TransformStep[];
    onMissing: OnMissing;
    defaultValue?: string;
}

// --- Record selector ---

export interface RecordSelector {
    source: SourceRef;
    onMissing: OnMissing;
}

// --- Dynamic mapper (top-level) ---

export interface DynamicMapper {
    id?: string;
    enabled: boolean;
    formatConfig: FormatConfig;
    recordSelector: RecordSelector;
    rules: MappingRule[];
}

// --- Preview request/response ---

export interface PreviewMappingRequest {
    profile: DynamicMapper;
    /** Gzip-compressed, base64-encoded file bytes */
    payloadBytes: string;
}

export interface MappingResponse {
    profileId?: string;
    format: MapperFormat;
    records: MappedRecord[];
    errors: MappingError[];
    warnings: MappingWarning[];
}

export interface MappedRecord {
    index: number;
    output: unknown[];
    warnings: MappingWarning[];
}

export interface MappingError {
    payload?: string;
    code: string;
    message: string;
    ruleIds?: string[];
    recordIndex?: number;
}

export interface MappingWarning {
    code: string;
    message: string;
    ruleIds?: string[];
    recordIndex?: number;
}
