// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import assertNever from "assert-never";
import { v4 as uuid } from "uuid";
import * as z from "zod";
import type { FieldErrors } from "react-hook-form";

/**
 * The predicate language of filter rules: what is saved and evaluated (`Filter`,
 * `Predicate`) and what a form edits (`FilterDraft`, `PredicateDraft`). Nothing here
 * knows which properties exist — a predicate names a property `path`, and what a filter
 * can be about arrives as fields (`filter-field.ts`).
 */

export type FilterCombinator = "ANY" | "ALL";
export type PredicateOperator = "EQUALS" | "EXISTS" | "IN" | "SUBSTRING_IN";
type BasePredicate = {
    path: string;
    operation: PredicateOperator;
    negate?: boolean;
    caseInsensitive?: boolean;
};
export type EqualsPredicate = BasePredicate & {
    operation: "EQUALS";
    values: [string];
};
export type ExistsPredicate = BasePredicate & {
    operation: "EXISTS";
};
export type InPredicate = BasePredicate & {
    operation: "IN";
    values: string[];
};
export type SubstringInPredicate = BasePredicate & {
    operation: "SUBSTRING_IN";
    values: string[];
    offset: number;
    length: number;
};

export type Predicate = EqualsPredicate | ExistsPredicate | InPredicate | SubstringInPredicate;

export type Filter = {
    combine: FilterCombinator;
    predicates: Predicate[];
};

export const operators = {
    EXISTS: { operator: "EXISTS", label: "filter.operations.EXISTS" },
    EQUALS: { operator: "EQUALS", label: "filter.operations.EQUALS" },
    IN: { operator: "IN", label: "filter.operations.IN" },
    SUBSTRING_IN: { operator: "SUBSTRING_IN", label: "filter.operations.SUBSTRING_IN" },
} as const satisfies Record<PredicateOperator, { operator: PredicateOperator; label: string }>;

/**
 * One row of a filter form. `field` is a field id. A row whose saved predicate no field
 * claimed carries it as `raw` instead (a fallback row): it saves back unchanged and
 * cannot be edited, only deleted.
 */
export type BasePredicateDraft = {
    id: string;
    field?: string;
    negate?: boolean;
    caseInsensitive?: boolean;
    value: unknown;
    raw?: undefined;
};

export type ExistsPredicateDraft = BasePredicateDraft & {
    operator: "EXISTS";
    value: undefined;
};

export type EqualsPredicateDraft = BasePredicateDraft & {
    operator: "EQUALS";
    value: string;
};

export type InPredicateDraft = BasePredicateDraft & {
    operator: "IN";
    value: string[];
};

export type SubstringInPredicateDraft = BasePredicateDraft & {
    operator: "SUBSTRING_IN";
    value: {
        value: string[];
        start: string;
        end: string;
    };
};

export type NonEmptyPredicateDraft =
    | ExistsPredicateDraft
    | EqualsPredicateDraft
    | InPredicateDraft
    | SubstringInPredicateDraft;
export type EmptyPredicateDraft = BasePredicateDraft & {
    operator: undefined;
    value: undefined;
};
/** A saved predicate no field claimed, kept verbatim. */
export type RawPredicateDraft = {
    id: string;
    raw: Predicate;
    field?: undefined;
    operator?: undefined;
    value?: undefined;
    negate?: undefined;
    caseInsensitive?: undefined;
};
export type PredicateDraft = NonEmptyPredicateDraft | EmptyPredicateDraft | RawPredicateDraft;

export type FilterDraft = {
    combinator: FilterCombinator;
    predicates: PredicateDraft[];
};

export function isRawPredicate(predicate: PredicateDraft): predicate is RawPredicateDraft {
    return predicate.raw !== undefined;
}

export function isEmptyPredicate(predicate: PredicateDraft): predicate is EmptyPredicateDraft {
    return (
        !isRawPredicate(predicate) &&
        predicate.operator === undefined &&
        predicate.value === undefined
    );
}

export function isNonEmptyPredicate(
    predicate: PredicateDraft,
): predicate is NonEmptyPredicateDraft {
    return !isRawPredicate(predicate) && !isEmptyPredicate(predicate);
}

export const defaultPredicateDraft = ({
    field,
    operator,
    id,
    negate,
    caseInsensitive,
}: {
    id?: string;
    field?: string;
    operator: PredicateOperator;
    negate?: boolean;
    caseInsensitive?: boolean;
}): NonEmptyPredicateDraft => {
    const base = {
        id: id ?? uuid(),
        field,
        negate,
        caseInsensitive,
    };
    switch (operator) {
        case "EQUALS":
            return { ...base, operator: "EQUALS", value: "" };
        case "EXISTS":
            return { ...base, operator: "EXISTS", value: undefined };
        case "IN":
            return { ...base, operator: "IN", value: [] };
        case "SUBSTRING_IN":
            return { ...base, operator: "SUBSTRING_IN", value: { value: [], start: "", end: "" } };
    }
};

export type Result<Result, Error> =
    | {
          ok: true;
          result: Result;
      }
    | {
          ok: false;
          errors: Error;
      };

export type ErrorOf<Res extends Result<unknown, unknown>> = Extract<Res, { errors: unknown }>;
export type ResultOf<Res extends Result<unknown, unknown>> = Extract<Res, { result: unknown }>;

const parseNonNegativeInteger = (s: string) => (/^\d+$/.test(s) ? Number(s) : undefined);

export type SubstringInValueError = {
    value?: "empty";
    start?: "required" | "invalid-range";
    end?: "required" | "invalid-range";
};
export type SubstringInPayload = Omit<SubstringInPredicate, "path" | "negate" | "caseInsensitive">;
export type ParseSubstringInResult = Result<SubstringInPayload, SubstringInValueError>;
export function parseSubstringInPayload(draft: SubstringInPredicateDraft): ParseSubstringInResult {
    const errors: SubstringInValueError = {};

    const offset = parseNonNegativeInteger(draft.value.start);
    if (offset === undefined) errors.start = "required";

    // The wire model requires a positive length, so end must exceed start.
    const end = parseNonNegativeInteger(draft.value.end);
    if (end === undefined) errors.end = "required";
    else if (offset !== undefined && end <= offset) errors.end = "invalid-range";

    if (draft.value.value.length === 0) errors.value = "empty";

    if (Object.values(errors).length > 0 || offset === undefined || end === undefined)
        return { ok: false, errors };

    return {
        ok: true,
        result: {
            operation: "SUBSTRING_IN",
            values: draft.value.value,
            offset,
            length: end - offset,
        },
    };
}

export type InValueError = "empty";
export type InPayload = Omit<InPredicate, "path" | "negate" | "caseInsensitive">;
export type ParseInResult = Result<InPayload, InValueError>;
export function parseInPayload(draft: InPredicateDraft): ParseInResult {
    if (draft.value.length === 0) return { ok: false, errors: "empty" };

    return { ok: true, result: { operation: "IN", values: draft.value } };
}

export type EqualsValueError = "required";
export type EqualsPayload = Omit<EqualsPredicate, "path" | "negate" | "caseInsensitive">;
export type ParseEqualsResult = Result<EqualsPayload, EqualsValueError>;
export function parseEqualsPayload(draft: EqualsPredicateDraft): ParseEqualsResult {
    if (draft.value === "") return { ok: false, errors: "required" };

    return { ok: true, result: { operation: "EQUALS", values: [draft.value] } };
}

export type ExistsPayload = Omit<ExistsPredicate, "path" | "negate" | "caseInsensitive">;
export type ParseExistsResult = { ok: true; result: ExistsPayload };
export function parseExistsPayload(): ParseExistsResult {
    return { ok: true, result: { operation: "EXISTS" } };
}

export type PredicatePayload = SubstringInPayload | InPayload | EqualsPayload | ExistsPayload;
export type PredicateValueError = SubstringInValueError | EqualsValueError | InValueError;

export type PredicateError = {
    /** `unavailable`: the row's field cannot save it now. */
    field?: "required" | "unavailable";
    operator?: "required";
    value?: PredicateValueError;
};

export type FilterError = Record<string, PredicateError>;

/** A draft's operator and value, parsed. The path comes from its field. */
export function parsePayload(
    draft: NonEmptyPredicateDraft,
): Result<PredicatePayload, PredicateValueError> {
    switch (draft.operator) {
        case "EQUALS":
            return parseEqualsPayload(draft);
        case "EXISTS":
            return parseExistsPayload();
        case "IN":
            return parseInPayload(draft);
        case "SUBSTRING_IN":
            return parseSubstringInPayload(draft);
    }
}

/** A saved predicate as an editable row of the given field. */
export function draftFromPredicate(predicate: Predicate, field: string): NonEmptyPredicateDraft {
    const base = {
        id: uuid(),
        field,
        negate: predicate.negate,
        caseInsensitive: predicate.caseInsensitive,
    };
    switch (predicate.operation) {
        case "EQUALS":
            return { ...base, operator: predicate.operation, value: predicate.values[0] };
        case "EXISTS":
            return { ...base, operator: predicate.operation, value: undefined };
        case "IN":
            return { ...base, operator: predicate.operation, value: predicate.values };
        case "SUBSTRING_IN":
            return {
                ...base,
                operator: predicate.operation,
                value: {
                    value: predicate.values,
                    start: String(predicate.offset),
                    end: String(predicate.offset + predicate.length),
                },
            };
        default:
            return assertNever(predicate);
    }
}

// Whether the field exists among the live fields is deliberately not checked here —
// that would mean rebuilding this schema whenever the fields change. `parseFilter`
// checks it, at submit, with the fields in hand. A fallback row is valid as it is.
export const predicateDraftSchema = z.custom<PredicateDraft>().superRefine((draft, ctx) => {
    if (isRawPredicate(draft)) return;
    if (draft.field === undefined) {
        ctx.addIssue({ code: "custom", path: ["field"], message: "required" });
    }
    if (draft.operator === undefined) {
        ctx.addIssue({ code: "custom", path: ["operator"], message: "required" });
        return;
    }

    const payload = parsePayload(draft);
    if (payload.ok) return;

    if (draft.operator === "SUBSTRING_IN") {
        const errors = payload.errors as SubstringInValueError;
        if (errors.value !== undefined)
            ctx.addIssue({ code: "custom", path: ["value", "value"], message: errors.value });
        if (errors.start !== undefined)
            ctx.addIssue({ code: "custom", path: ["value", "start"], message: errors.start });
        if (errors.end !== undefined)
            ctx.addIssue({ code: "custom", path: ["value", "end"], message: errors.end });
    } else {
        ctx.addIssue({ code: "custom", path: ["value"], message: payload.errors as string });
    }
});

const asRequired = (message: string | undefined): "required" | undefined =>
    message === "required" ? message : undefined;

const asFieldError = (message: string | undefined): "required" | "unavailable" | undefined =>
    message === "required" || message === "unavailable" ? message : undefined;

const asValueError = (message: string | undefined): EqualsValueError | InValueError | undefined =>
    message === "required" || message === "empty" ? message : undefined;

const asEmpty = (message: string | undefined): "empty" | undefined =>
    message === "empty" ? message : undefined;

const asRangeError = (
    message: string | undefined,
): NonNullable<SubstringInValueError["start"]> | undefined =>
    message === "required" || message === "invalid-range" ? message : undefined;

/** The part of one row's react-hook-form error tree this reads. */
type RowFieldErrors = {
    field?: { message?: string };
    operator?: { message?: string };
    value?: unknown;
};

/**
 * Converts one predicate row's slice of react-hook-form's generic FieldErrors
 * tree back into the plain PredicateError shape every filter-row
 * component already knows how to render — the seam between RHF/zod's
 * {type, message} objects and our enum-like error strings.
 */
export function toPredicateError(
    fieldErrors: RowFieldErrors | undefined,
): PredicateError | undefined {
    if (!fieldErrors) return undefined;

    // RHF loses the operator-discriminated value shape because the shared draft
    // base declares `value` as unknown. The tree-shape assertion is confined to
    // this boundary; every message read from it is validated below.
    const value = fieldErrors.value as
        | (FieldErrors<{ value: string[]; start: string; end: string }> & { message?: string })
        | undefined;

    const nestedValue: SubstringInValueError | undefined = value
        ? {
              value: asEmpty(value.value?.message),
              start: asRangeError(value.start?.message),
              end: asRangeError(value.end?.message),
          }
        : undefined;
    const validatedNestedValue =
        nestedValue && Object.values(nestedValue).some((error) => error !== undefined)
            ? nestedValue
            : undefined;

    const result: PredicateError = {
        field: asFieldError(fieldErrors.field?.message),
        operator: asRequired(fieldErrors.operator?.message),
        value: asValueError(value?.message) ?? validatedNestedValue,
    };

    if (result.field === undefined && result.operator === undefined && result.value === undefined)
        return undefined;

    return result;
}
