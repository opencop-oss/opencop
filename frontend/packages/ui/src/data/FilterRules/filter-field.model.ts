// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ComponentType } from "react";
import { v4 as uuid } from "uuid";
import type { TFunction } from "i18next";
import {
    defaultPredicateDraft,
    draftFromPredicate,
    isRawPredicate,
    operators,
    parsePayload,
    type Filter,
    type FilterCombinator,
    type FilterDraft,
    type FilterError,
    type Predicate,
    type PredicateDraft,
    type PredicateError,
    type PredicateOperator,
    type PredicateValueError,
    type Result,
} from "./predicate.model";

/** What a field is called: a translation key, or — for a name the operator typed — the text. */
export type FilterFieldLabel =
    | { readonly labelKey: string; readonly labelText?: never }
    | { readonly labelText: string; readonly labelKey?: never };

/** What a field's own value editor receives: one row, how to change it, and its value error. */
export interface FilterValueEditorProps {
    readonly predicate: PredicateDraft;
    readonly setPredicate: (predicate: PredicateDraft) => void;
    readonly error?: PredicateValueError;
}

/**
 * Something a filter can be about: a property path, how it is edited, and how it is
 * saved. The generic filter knows only the predicate language; every field-specific
 * behaviour is a member here.
 *
 * Members that are functions or components are contributed code: the host contains
 * them where it accepts the field, so a throw costs only what each member's doc says. A
 * member that keeps throwing is no longer called until the field registers again. What a
 * member returns is checked too: a claim off the field's path, a fold of anything but
 * nothing or the next predicate on that path, companions that are not predicates on the
 * field's path, a summary value that is not a string — each is treated as no answer.
 */
export type FilterField = FilterFieldLabel & {
    /** Unique among the fields. A plugin's is scoped to it: `"<pluginId>:<name>"`. */
    readonly id: string;
    /** The property every predicate of this field addresses. */
    readonly path: string;
    /** Where the field sits in the picker, ascending. */
    readonly order: number;
    /** The operators the field offers; all when absent. With one, the picker is hidden. */
    readonly operators?: readonly PredicateOperator[];
    /** Which modifiers the row offers; both when absent. */
    readonly toggles?: { readonly negate?: boolean; readonly caseInsensitive?: boolean };
    /** Shows a delete button in the field picker, which calls the provider's `removeField`. */
    readonly removable?: boolean;
    /**
     * The row a field starts from when picked — for a field that pins its operator or
     * its value's shape. `undefined` (or a throw) leaves the row unchanged.
     */
    newDraft?(id: string): PredicateDraft | undefined;
    /** Replaces the generic value editor. A throw renders nothing in its place; the row stays deletable. */
    readonly ValueEditor?: ComponentType<FilterValueEditorProps>;
    /**
     * How a complete predicate's value reads in a summary sentence ("is equal to <value>").
     * `undefined` (or a throw) keeps the generic sentence.
     */
    formatValue?(predicate: Predicate, t: TFunction): string | undefined;
    /**
     * Predicates saved right after this field's own, on the field's own path. A field that
     * declares this matches only under `ANY` — even on rows where it returns `[]` — and the
     * builder says so; omit the method on a field that never emits one. Return `[]` for none;
     * `undefined` (or a throw, or anything but predicates on this path) means the field
     * cannot save this row now: the row reports an error and the save is refused.
     */
    companions?(predicate: Predicate): readonly Predicate[] | undefined;
    /**
     * Whether a saved predicate is this field's, and whether the predicate after it is
     * a companion to fold into the same row. `null` (or a throw) leaves it unclaimed.
     */
    claims?(predicate: Predicate, next: Predicate | undefined): { readonly folds: 0 | 1 } | null;
    /** A translation key shown under the builder while a row uses this field. */
    readonly noticeKey?: string;
};

/** A field an owner contributes: its label is always a translation key in its own namespace. */
export type ContributedFilterField = Extract<FilterField, { readonly labelKey: string }>;

/** The field's display name. */
export function fieldLabel(field: FilterField, t: TFunction): string {
    return field.labelKey !== undefined ? t(field.labelKey as never) : field.labelText;
}

export function findField(
    fields: readonly FilterField[],
    id: string | undefined,
): FilterField | undefined {
    return id === undefined ? undefined : fields.find((field) => field.id === id);
}

/**
 * Re-shapes a row when its field changes: a field with `newDraft` starts the row from
 * it; leaving such a field falls back to `EQUALS` so its pinned shape doesn't linger;
 * an operator the new field does not offer is replaced by its first.
 */
export function retargetDraftToField(
    predicate: PredicateDraft,
    fieldId: string | undefined,
    fields: readonly FilterField[],
): PredicateDraft {
    if (isRawPredicate(predicate)) return predicate;
    const next = findField(fields, fieldId);
    if (next?.newDraft) {
        const started = next.newDraft(predicate.id);
        // Only a row of this field, for this row, is taken; anything else leaves it unchanged.
        return started?.id === predicate.id && started.field === fieldId ? started : predicate;
    }

    const previous = findField(fields, predicate.field);
    if (previous?.newDraft)
        return defaultPredicateDraft({
            id: predicate.id,
            field: fieldId,
            operator: next?.operators?.[0] ?? "EQUALS",
            negate: predicate.negate,
        });

    if (next?.operators && predicate.operator && !next.operators.includes(predicate.operator))
        return defaultPredicateDraft({
            id: predicate.id,
            field: fieldId,
            operator: next.operators[0] ?? "EQUALS",
            negate: predicate.negate,
            caseInsensitive: predicate.caseInsensitive,
        });

    return { ...predicate, field: fieldId };
}

export type ParsePredicateResult = Result<Predicate, PredicateError>;

/** One row as a saved predicate. A fallback row parses to the predicate it carries. */
export function parsePredicate(
    draft: PredicateDraft,
    fields: readonly FilterField[],
): ParsePredicateResult {
    if (isRawPredicate(draft)) return { ok: true, result: draft.raw };
    const field = findField(fields, draft.field);
    const base: PredicateError = {};

    if (field === undefined) base.field = "required";
    if (draft.operator === undefined) {
        base.operator = "required";
        return { ok: false, errors: base };
    }

    const payload = parsePayload(draft);
    if (!payload.ok) return { ok: false, errors: { ...base, value: payload.errors } };
    if (field === undefined) return { ok: false, errors: base };

    return {
        ok: true,
        result: {
            path: field.path,
            negate: draft.negate ?? false,
            // EXISTS has nothing to compare, so the flag is left off the saved form entirely.
            caseInsensitive:
                draft.operator === "EXISTS" ? undefined : (draft.caseInsensitive ?? false),
            ...payload.result,
        },
    };
}

export type ParseFilterResult = Result<Filter, FilterError>;

/** The draft as a saved filter, each row followed by its field's companions. */
export function parseFilter(draft: FilterDraft, fields: readonly FilterField[]): ParseFilterResult {
    const predicates: Predicate[] = [];
    const errors: FilterError = {};

    for (const row of draft.predicates) {
        const res = parsePredicate(row, fields);
        if (!res.ok) {
            errors[row.id] = res.errors;
            continue;
        }
        const field = isRawPredicate(row) ? undefined : findField(fields, row.field);
        const companions = field?.companions ? field.companions(res.result) : [];
        if (!isPredicateList(companions, field?.path)) {
            errors[row.id] = { field: "unavailable" };
            continue;
        }
        predicates.push(res.result, ...companions);
    }

    if (Object.keys(errors).length > 0) return { ok: false, errors };
    return { ok: true, result: { combine: draft.combinator, predicates } };
}

/** Whether `value` is a list of predicates on `path`: what a field's companions must be. */
function isPredicateList(value: unknown, path: string | undefined): value is Predicate[] {
    return (
        Array.isArray(value) &&
        value.every((item: unknown) => {
            if (typeof item !== "object" || item === null) return false;
            const { path: itemPath, operation } = item as Partial<Predicate>;
            return (
                itemPath === path &&
                typeof operation === "string" &&
                Object.hasOwn(operators, operation)
            );
        })
    );
}

/**
 * How many following predicates a field's claim folds in, or `null` when it does not
 * claim. A claim is honoured only on the field's own path, folding nothing or one
 * predicate on that path — so a claim can never take another field's predicate, and
 * never swallow ones it did not write.
 */
function acceptedFolds(
    field: FilterField,
    predicate: Predicate,
    next: Predicate | undefined,
): 0 | 1 | null {
    if (predicate.path !== field.path) return null;
    const claim: unknown = field.claims?.(predicate, next);
    if (typeof claim !== "object" || claim === null) return null;
    const folds = (claim as { folds?: unknown }).folds;
    if (folds === 0) return 0;
    if (folds === 1 && next?.path === field.path) return 1;
    return null;
}

/** A saved predicate, resolved: the row it reads back as, and the field it belongs to. */
export interface ResolvedRow {
    readonly draft: PredicateDraft;
    /** `undefined` for a fallback row. */
    readonly field: FilterField | undefined;
}

/**
 * Resolves each saved predicate to a row: a field that claims it (in `order`; a claim may
 * fold the next predicate in as its companion); else a field without `claims` whose path
 * matches; else `fallback`, the provider's field for a predicate no listed field holds
 * (an attribute this browser never saw); else a fallback row that keeps it verbatim.
 */
export function resolveFilter(
    filter: Filter,
    fields: readonly FilterField[],
    fallback?: (predicate: Predicate) => FilterField | undefined,
): { combinator: FilterCombinator; rows: ResolvedRow[] } {
    const claimants = fields.filter((field) => field.claims);
    const byPath = fields.filter((field) => !field.claims);
    const rows: ResolvedRow[] = [];

    for (let i = 0; i < filter.predicates.length; i++) {
        const predicate = filter.predicates[i];
        const next = filter.predicates[i + 1];

        let claimed: FilterField | undefined;
        for (const field of claimants) {
            const folds = acceptedFolds(field, predicate, next);
            if (folds === null) continue;
            claimed = field;
            i += folds;
            break;
        }
        const field =
            claimed ??
            byPath.find((candidate) => candidate.path === predicate.path) ??
            fallback?.(predicate);

        rows.push(
            field
                ? { draft: draftFromPredicate(predicate, field.id), field }
                : { draft: { id: uuid(), raw: predicate }, field: undefined },
        );
    }

    return { combinator: filter.combine, rows };
}

/** A saved filter as the rows a form edits. */
export function draftFromFilter(
    filter: Filter,
    fields: readonly FilterField[],
    fallback?: (predicate: Predicate) => FilterField | undefined,
): FilterDraft {
    const { combinator, rows } = resolveFilter(filter, fields, fallback);
    return { combinator, predicates: rows.map((row) => row.draft) };
}

/** True when a row uses a field whose companions make it match only under `ANY`. */
function requiresAnyCombinator(draft: FilterDraft, fields: readonly FilterField[]): boolean {
    return draft.predicates.some(
        (row) => !isRawPredicate(row) && findField(fields, row.field)?.companions !== undefined,
    );
}

/** True when the draft cannot be saved: such a row under `ALL` matches nothing. */
export function isInertUnderAll(draft: FilterDraft, fields: readonly FilterField[]): boolean {
    return draft.combinator === "ALL" && requiresAnyCombinator(draft, fields);
}

/** The notices of the fields the draft's rows use, each once, in row order. */
export function activeNotices(draft: FilterDraft, fields: readonly FilterField[]): string[] {
    const keys: string[] = [];
    for (const row of draft.predicates) {
        if (isRawPredicate(row)) continue;
        const key = findField(fields, row.field)?.noticeKey;
        if (key && !keys.includes(key)) keys.push(key);
    }
    return keys;
}

/** True when a row was saved by a field that is no longer available. */
export function hasFallbackRow(draft: FilterDraft): boolean {
    return draft.predicates.some(isRawPredicate);
}
