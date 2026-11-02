// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import assertNever from "assert-never";
import type { TFunction } from "i18next";
import { isRawPredicate, type Predicate, type PredicateDraft } from "./predicate.model";
import { fieldLabel, parsePredicate, type FilterField } from "./filter-field.model";

export type PredicateSummaryValue = {
    key:
        | "filter.summary.equals"
        | "filter.summary.equalsNegated"
        | "filter.summary.exists"
        | "filter.summary.existsNegated"
        | "filter.summary.in"
        | "filter.summary.inNegated"
        | "filter.summary.substringIn"
        | "filter.summary.substringInNegated";
    values: {
        field: string;
        value?: string;
        values?: string;
        start?: string;
        end?: string;
    };
    /** Set for a fallback row: the field that saved it is not available, so it reads by path. */
    unavailable?: true;
};

/**
 * The translated sentence and interpolation values for a collapsed row, or `undefined`
 * while the row is incomplete, so the caller keeps its editor open. A fallback row reads
 * by its property path, marked unavailable.
 */
export function summarizePredicate(
    row: PredicateDraft,
    field: FilterField | undefined,
    t: TFunction,
): PredicateSummaryValue | undefined {
    if (isRawPredicate(row)) return summarizeUnavailable(row.raw);
    if (field === undefined) return undefined;
    const parsed = parsePredicate(row, [field]);
    if (!parsed.ok) return undefined;

    const label = fieldLabel(field, t);
    // A named value reads as an equality.
    const value: unknown = field.formatValue?.(parsed.result, t);
    if (typeof value === "string")
        return { key: "filter.summary.equals", values: { field: label, value } };
    return sentenceFor(parsed.result, label);
}

/** A saved predicate whose field is unavailable, read by its property path. */
export function summarizeUnavailable(predicate: Predicate): PredicateSummaryValue {
    return { ...sentenceFor(predicate, predicate.path), unavailable: true };
}

function sentenceFor(predicate: Predicate, field: string): PredicateSummaryValue {
    const negate = !!predicate.negate;
    switch (predicate.operation) {
        case "EXISTS":
            return {
                key: negate ? "filter.summary.existsNegated" : "filter.summary.exists",
                values: { field },
            };
        case "EQUALS":
            return {
                key: negate ? "filter.summary.equalsNegated" : "filter.summary.equals",
                values: { field, value: predicate.values[0] },
            };
        case "IN":
            return {
                key: negate ? "filter.summary.inNegated" : "filter.summary.in",
                values: { field, values: predicate.values.join(", ") },
            };
        case "SUBSTRING_IN":
            return {
                key: negate ? "filter.summary.substringInNegated" : "filter.summary.substringIn",
                values: {
                    field,
                    values: predicate.values.join(", "),
                    start: String(predicate.offset),
                    end: String(predicate.offset + predicate.length),
                },
            };
        default:
            return assertNever(predicate);
    }
}
