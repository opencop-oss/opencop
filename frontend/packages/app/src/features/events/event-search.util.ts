// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Element } from "@opencop/elements";

/** Below this length, matches are so broad (nearly every live element) that
 * running a search isn't worth the cost. */
export const MIN_QUERY_LENGTH = 3;

export interface MatchedField {
    label: string;
    value: string;
}

export interface SearchResult {
    element: Element;
    /** Fields shown only when matched (excludes the always-visible name and source). */
    matchedFields: MatchedField[];
}

function collectMatchedFields(element: Element, normalizedQuery: string): MatchedField[] {
    const matched: MatchedField[] = [];
    const matches = (s: string) => s.toLocaleLowerCase().includes(normalizedQuery);

    // Cards don't print the object ID (only a Copy ID button), so a match on it is shown here.
    if (matches(element.objectId)) matched.push({ label: "objectId", value: element.objectId });

    if (matches(element.id)) matched.push({ label: "id", value: element.id });

    if (matches(element.type)) matched.push({ label: "type", value: element.type });

    const code = element.getCode();
    if (code && matches(code)) matched.push({ label: "code", value: code });

    // Live Graphic elements carry real modifier: values from the WS transport
    // (see GraphicMapper), so this loop matches them correctly. Live Symbol
    // elements do not: standard backend event construction (FeatureBuilder,
    // Symbol mapping profiles) never populates modifier target fields, so
    // getModifiers() is always {} for them today — not a bug in this loop,
    // just no data to search yet. Don't advertise modifier search as working
    // for Symbols in UI copy until the backend populates it.
    for (const [key, value] of Object.entries(element.getModifiers())) {
        if (matches(key) || matches(value)) matched.push({ label: key, value });
    }

    return matched;
}

function matchesPrimaryFields(element: Element, normalizedQuery: string): boolean {
    return [element.objectId, element.id, element.getDisplayName(), element.source].some((v) =>
        v.toLocaleLowerCase().includes(normalizedQuery),
    );
}

export function searchElements(elements: readonly Element[], query: string): SearchResult[] {
    const normalizedQuery = query.trim().toLocaleLowerCase();
    if (!normalizedQuery) return [];

    return elements
        .map((element) => ({
            element,
            matchedFields: collectMatchedFields(element, normalizedQuery),
            isPrimaryMatch: matchesPrimaryFields(element, normalizedQuery),
            isExactMatch:
                element.objectId.toLocaleLowerCase() === normalizedQuery ||
                element.id.toLocaleLowerCase() === normalizedQuery,
        }))
        .filter(({ isPrimaryMatch, matchedFields }) => isPrimaryMatch || matchedFields.length > 0)
        .sort((left, right) => {
            if (left.isExactMatch !== right.isExactMatch) return left.isExactMatch ? -1 : 1;
            return left.element.getDisplayName().localeCompare(right.element.getDisplayName());
        })
        .map(({ element, matchedFields }) => ({
            element,
            matchedFields,
        }));
}
