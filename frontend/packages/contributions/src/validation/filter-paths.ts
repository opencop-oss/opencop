// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

const ATTRIBUTE_PREFIX = "properties.opencop.attributes.";
const ATTRIBUTE_NAME = /^[A-Za-z0-9_-]{1,64}$/;

/*
 * What the host's filters can address: the property paths, and the host's own fields.
 * Part of the filter-field contribution contract — a contributed field must name a path
 * from here — so it sits beside the rule that checks it, and the host builds its own
 * fields from it. Not in `@opencop/ui`'s filter rules, which are the generic predicate
 * language and name no path.
 */

/**
 * The properties filter rules may address. The watchbox backend refuses any other
 * path (`WatchboxValidationService` in opencop-java core), so a field on another path
 * could be built but never saved; a new filterable property starts there.
 */
const EXACT_PATHS = new Set([
    "properties.opencop.kind",
    "properties.opencop.displayName",
    "properties.opencop.element.symbol",
]);

/** Whether `name` can be an attribute: what the backend accepts after the prefix. */
export function isAttributeName(name: string): boolean {
    return ATTRIBUTE_NAME.test(name);
}

export function attributePath(name: string): string {
    return ATTRIBUTE_PREFIX + name;
}

/** The attribute a path addresses, or `undefined` for any other path. */
export function attributeNameOf(path: string): string | undefined {
    if (!path.startsWith(ATTRIBUTE_PREFIX)) return undefined;
    const name = path.slice(ATTRIBUTE_PREFIX.length);
    return isAttributeName(name) ? name : undefined;
}

/** Whether a filter may address `path`. */
export function isFilterablePath(path: string): boolean {
    return EXACT_PATHS.has(path) || attributeNameOf(path) !== undefined;
}

/**
 * The host's own fields, by identity: what a contributed field may not take. Their labels
 * and order are the host's, beside its translations.
 */
export const HOST_FILTER_FIELDS = {
    kind: { id: "kind", path: "properties.opencop.kind" },
    displayName: { id: "displayName", path: "properties.opencop.displayName" },
} as const;

/** The ids of the host's own fields, which no contributed field may take. */
export const HOST_FILTER_FIELD_IDS: ReadonlySet<string> = new Set(
    Object.values(HOST_FILTER_FIELDS).map(({ id }) => id),
);

/** The paths the host's own fields address. */
export const HOST_FILTER_FIELD_PATHS: ReadonlySet<string> = new Set(
    Object.values(HOST_FILTER_FIELDS).map(({ path }) => path),
);

const ATTRIBUTE_ID_PREFIX = "attribute:";

/** The id of the host's field for a custom attribute. */
export function attributeFieldId(name: string): string {
    return ATTRIBUTE_ID_PREFIX + name;
}

/** The attribute a field id names, or `undefined` for any other field. */
export function attributeNameOfId(id: string): string | undefined {
    return id.startsWith(ATTRIBUTE_ID_PREFIX) ? id.slice(ATTRIBUTE_ID_PREFIX.length) : undefined;
}
