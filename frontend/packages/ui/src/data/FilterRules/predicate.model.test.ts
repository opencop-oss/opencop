// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    defaultPredicateDraft,
    draftFromPredicate,
    isEmptyPredicate,
    isNonEmptyPredicate,
    parseEqualsPayload,
    parseExistsPayload,
    parseInPayload,
    parseSubstringInPayload,
    predicateDraftSchema,
    toPredicateError,
    type EmptyPredicateDraft,
    type EqualsPredicateDraft,
    type ErrorOf,
    type InPredicateDraft,
    type ParseSubstringInResult,
    type ResultOf,
    type SubstringInPredicateDraft,
    type FilterDraft,
    type PredicateDraft,
} from "./predicate.model";
import {
    parseFilter,
    parsePredicate,
    type FilterField,
    type ParseFilterResult,
    type ParsePredicateResult,
} from "./filter-field.model";
import type { FieldErrors } from "react-hook-form";

describe("substring-in", () => {
    const baseDraft = {
        id: "1",
        operator: "SUBSTRING_IN",
    } as const;

    function expectError(
        draft: SubstringInPredicateDraft,
        value: keyof SubstringInPredicateDraft["value"],
    ) {
        const res = parseSubstringInPayload(draft);
        expect(res.ok).toBe(false);
        expect((res as ErrorOf<ParseSubstringInResult>).errors[value]).not.toBe(undefined);
    }

    it("errors out with empty value", () => {
        const draft: SubstringInPredicateDraft = {
            ...baseDraft,
            value: {
                value: [],
                start: "1",
                end: "2",
            },
        };

        expectError(draft, "value");
    });
    it("errors out with invalid start", () => {
        const draft: SubstringInPredicateDraft = {
            ...baseDraft,
            value: {
                value: ["first"],
                start: "-1",
                end: "1",
            },
        };

        expectError(draft, "start");
    });
    it("errors out with invalid range", () => {
        const draft: SubstringInPredicateDraft = {
            ...baseDraft,
            value: {
                value: ["first"],
                start: "1",
                end: "0",
            },
        };

        expectError(draft, "end");
    });
    it("errors out when end equals start", () => {
        const draft: SubstringInPredicateDraft = {
            ...baseDraft,
            value: {
                value: ["first"],
                start: "2",
                end: "2",
            },
        };

        expectError(draft, "end");
    });
    it("errors out with non-integer bounds", () => {
        const draft: SubstringInPredicateDraft = {
            ...baseDraft,
            value: {
                value: ["first"],
                start: "1.5",
                end: "abc",
            },
        };

        expectError(draft, "start");
        expectError(draft, "end");
    });
    it("reports every error at once", () => {
        const draft: SubstringInPredicateDraft = {
            ...baseDraft,
            value: {
                value: [],
                start: "",
                end: "",
            },
        };

        const res = parseSubstringInPayload(draft);
        expect(res.ok).toBe(false);
        expect((res as ErrorOf<ParseSubstringInResult>).errors).toEqual({
            value: "empty",
            start: "required",
            end: "required",
        });
    });
    it("parses start/end into offset/length", () => {
        const draft: SubstringInPredicateDraft = {
            ...baseDraft,
            value: {
                value: ["first", "second"],
                start: "2",
                end: "5",
            },
        };

        const res = parseSubstringInPayload(draft);
        expect(res.ok).toBe(true);
        expect((res as ResultOf<ParseSubstringInResult>).result).toEqual({
            operation: "SUBSTRING_IN",
            values: ["first", "second"],
            offset: 2,
            length: 3,
        });
    });
    it("accepts a zero start", () => {
        const draft: SubstringInPredicateDraft = {
            ...baseDraft,
            value: {
                value: ["first"],
                start: "0",
                end: "1",
            },
        };

        const res = parseSubstringInPayload(draft);
        expect(res.ok).toBe(true);
        expect((res as ResultOf<ParseSubstringInResult>).result).toMatchObject({
            offset: 0,
            length: 1,
        });
    });
});

describe("in", () => {
    const baseDraft = {
        id: "1",
        operator: "IN",
    } as const;

    it("errors out on empty value", () => {
        const draft: InPredicateDraft = { ...baseDraft, value: [] };

        const res = parseInPayload(draft);
        expect(res).toEqual({ ok: false, errors: "empty" });
    });
    it("passes values through", () => {
        const draft: InPredicateDraft = { ...baseDraft, value: ["a", "b"] };

        const res = parseInPayload(draft);
        expect(res).toEqual({ ok: true, result: { operation: "IN", values: ["a", "b"] } });
    });
});

describe("equals", () => {
    const baseDraft = {
        id: "1",
        operator: "EQUALS",
    } as const;

    it("errors out on empty value", () => {
        const draft: EqualsPredicateDraft = { ...baseDraft, value: "" };

        const res = parseEqualsPayload(draft);
        expect(res).toEqual({ ok: false, errors: "required" });
    });
    it("wraps the value in a single-element tuple", () => {
        const draft: EqualsPredicateDraft = { ...baseDraft, value: "UNIT" };

        const res = parseEqualsPayload(draft);
        expect(res).toEqual({ ok: true, result: { operation: "EQUALS", values: ["UNIT"] } });
    });
});

describe("exists", () => {
    it("always parses", () => {
        expect(parseExistsPayload()).toEqual({ ok: true, result: { operation: "EXISTS" } });
    });
});

// Two plain fields, the shape a host's built-in fields have.
const KIND: FilterField = { id: "kind", labelKey: "k", path: "properties.opencop.kind", order: 10 };
const NAME: FilterField = {
    id: "displayName",
    labelKey: "n",
    path: "properties.opencop.displayName",
    order: 20,
};
const FIELDS = [KIND, NAME];

describe("parsePredicate", () => {
    const deps = FIELDS;

    function expectErrors(res: ParsePredicateResult) {
        expect(res.ok).toBe(false);
        return (res as ErrorOf<ParsePredicateResult>).errors;
    }

    it("errors out when the field is missing", () => {
        const res = parsePredicate({ id: "1", operator: "EQUALS", value: "x" }, deps);

        expect(expectErrors(res)).toEqual({ field: "required" });
    });
    it("errors out when the field is unknown", () => {
        const res = parsePredicate(
            { id: "1", field: "nope", operator: "EQUALS", value: "x" },
            deps,
        );

        expect(expectErrors(res)).toEqual({ field: "required" });
    });
    it("errors out when the operator is missing, without a value error", () => {
        const res = parsePredicate({ id: "1", operator: undefined, value: undefined }, deps);

        expect(expectErrors(res)).toEqual({ field: "required", operator: "required" });
    });
    it("combines field and value errors", () => {
        const res = parsePredicate({ id: "1", operator: "EQUALS", value: "" }, deps);

        expect(expectErrors(res)).toEqual({ field: "required", value: "required" });
    });
    it("maps the field to its path and defaults negate to false", () => {
        const res = parsePredicate(
            { id: "1", field: "kind", operator: "EQUALS", value: "UNIT" },
            deps,
        );

        expect(res.ok).toBe(true);
        expect((res as ResultOf<ParsePredicateResult>).result).toEqual({
            path: KIND.path,
            operation: "EQUALS",
            values: ["UNIT"],
            negate: false,
            caseInsensitive: false,
        });
    });
    it("preserves caseInsensitive", () => {
        const res = parsePredicate(
            {
                id: "1",
                field: "kind",
                operator: "EQUALS",
                value: "UNIT",
                caseInsensitive: true,
            },
            deps,
        );

        expect(res.ok).toBe(true);
        expect((res as ResultOf<ParsePredicateResult>).result).toMatchObject({
            caseInsensitive: true,
        });
    });
    it("omits caseInsensitive for EXISTS, which has nothing to compare", () => {
        const res = parsePredicate(
            {
                id: "1",
                field: "kind",
                operator: "EXISTS",
                value: undefined,
                caseInsensitive: true,
            },
            deps,
        );

        expect(res.ok).toBe(true);
        expect((res as ResultOf<ParsePredicateResult>).result).toEqual({
            path: KIND.path,
            operation: "EXISTS",
            negate: false,
        });
    });
    it("preserves negate", () => {
        const res = parsePredicate(
            { id: "1", field: "displayName", operator: "EXISTS", value: undefined, negate: true },
            deps,
        );

        expect(res.ok).toBe(true);
        expect((res as ResultOf<ParsePredicateResult>).result).toMatchObject({
            path: NAME.path,
            negate: true,
        });
    });
});

describe("parseFilter", () => {
    const deps = FIELDS;

    it("parses every predicate and carries the combinator", () => {
        const draft: FilterDraft = {
            combinator: "ANY",
            predicates: [
                { id: "1", field: "kind", operator: "EQUALS", value: "UNIT" },
                { id: "2", field: "displayName", operator: "IN", value: ["a", "b"] },
            ],
        };

        const res = parseFilter(draft, deps);
        expect(res.ok).toBe(true);
        expect((res as ResultOf<ParseFilterResult>).result).toEqual({
            combine: "ANY",
            predicates: [
                {
                    path: KIND.path,
                    operation: "EQUALS",
                    values: ["UNIT"],
                    negate: false,
                    caseInsensitive: false,
                },
                {
                    path: NAME.path,
                    operation: "IN",
                    values: ["a", "b"],
                    negate: false,
                    caseInsensitive: false,
                },
            ],
        });
    });
    it("keys errors by predicate id and only reports failing predicates", () => {
        const draft: FilterDraft = {
            combinator: "ALL",
            predicates: [
                { id: "good", field: "kind", operator: "EQUALS", value: "UNIT" },
                { id: "bad", field: "kind", operator: "EQUALS", value: "" },
            ],
        };

        const res = parseFilter(draft, deps);
        expect(res.ok).toBe(false);
        expect((res as ErrorOf<ParseFilterResult>).errors).toEqual({
            bad: { value: "required" },
        });
    });
    it("parses an empty draft to an empty filter", () => {
        const res = parseFilter({ combinator: "ALL", predicates: [] }, deps);

        expect(res).toEqual({ ok: true, result: { combine: "ALL", predicates: [] } });
    });
});

describe("defaultPredicateDraft", () => {
    it("builds the per-operator empty value shape", () => {
        expect(defaultPredicateDraft({ id: "1", operator: "EQUALS" }).value).toBe("");
        expect(defaultPredicateDraft({ id: "1", operator: "EXISTS" }).value).toBe(undefined);
        expect(defaultPredicateDraft({ id: "1", operator: "IN" }).value).toEqual([]);
        expect(defaultPredicateDraft({ id: "1", operator: "SUBSTRING_IN" }).value).toEqual({
            value: [],
            start: "",
            end: "",
        });
    });
    it("keeps the provided id and field", () => {
        const draft = defaultPredicateDraft({ id: "1", field: "kind", operator: "EQUALS" });

        expect(draft).toMatchObject({ id: "1", field: "kind", operator: "EQUALS" });
    });
    it("carries the modifiers through an operator change", () => {
        const draft = defaultPredicateDraft({
            id: "1",
            field: "kind",
            operator: "IN",
            negate: true,
            caseInsensitive: true,
        });

        expect(draft).toMatchObject({ negate: true, caseInsensitive: true, value: [] });
    });
    it("generates an id when omitted", () => {
        const a = defaultPredicateDraft({ operator: "EQUALS" });
        const b = defaultPredicateDraft({ operator: "EQUALS" });

        expect(a.id).toBeTruthy();
        expect(a.id).not.toBe(b.id);
    });
});

describe("empty-predicate guards", () => {
    const empty: EmptyPredicateDraft = { id: "1", operator: undefined, value: undefined };

    it("treats a draft with no operator and no value as empty", () => {
        expect(isEmptyPredicate(empty)).toBe(true);
        expect(isNonEmptyPredicate(empty)).toBe(false);
    });
    it("treats EXISTS as non-empty despite its undefined value", () => {
        const draft = defaultPredicateDraft({ id: "1", operator: "EXISTS" });

        expect(isEmptyPredicate(draft)).toBe(false);
        expect(isNonEmptyPredicate(draft)).toBe(true);
    });
});

describe("predicateDraftSchema", () => {
    function issues(draft: PredicateDraft) {
        const res = predicateDraftSchema.safeParse(draft);
        expect(res.success).toBe(false);
        return res.error!.issues.map((issue) => ({ path: issue.path, message: issue.message }));
    }

    it("passes a fully valid predicate", () => {
        const draft: PredicateDraft = {
            id: "1",
            field: "kind",
            operator: "EQUALS",
            value: "UNIT",
        };

        expect(predicateDraftSchema.safeParse(draft).success).toBe(true);
    });

    it("flags a missing field and a missing operator independently", () => {
        const draft: PredicateDraft = { id: "1", operator: undefined, value: undefined };

        expect(issues(draft)).toEqual([
            { path: ["field"], message: "required" },
            { path: ["operator"], message: "required" },
        ]);
    });

    it("flags an empty EQUALS value at the value path", () => {
        const draft: PredicateDraft = {
            id: "1",
            field: "kind",
            operator: "EQUALS",
            value: "",
        };

        expect(issues(draft)).toEqual([{ path: ["value"], message: "required" }]);
    });

    it("flags every SUBSTRING_IN sub-error at its own nested path", () => {
        const draft: PredicateDraft = {
            id: "1",
            field: "sidc",
            operator: "SUBSTRING_IN",
            value: { value: [], start: "", end: "" },
        };

        expect(issues(draft)).toEqual([
            { path: ["value", "value"], message: "empty" },
            { path: ["value", "start"], message: "required" },
            { path: ["value", "end"], message: "required" },
        ]);
    });
});

describe("toPredicateError", () => {
    it("returns undefined for no errors", () => {
        expect(toPredicateError(undefined)).toBeUndefined();
    });

    it("converts a flat field error", () => {
        const errors = {
            field: { type: "custom", message: "required" },
        } as unknown as FieldErrors<PredicateDraft>;

        expect(toPredicateError(errors)).toEqual({ field: "required" });
    });

    it("converts a flat value error (EQUALS/IN shape)", () => {
        const errors = {
            value: { type: "custom", message: "empty" },
        } as unknown as FieldErrors<PredicateDraft>;

        expect(toPredicateError(errors)).toEqual({ value: "empty" });
    });

    it("converts a nested SUBSTRING_IN value error", () => {
        const errors = {
            value: {
                start: { type: "custom", message: "required" },
                end: { type: "custom", message: "invalid-range" },
            },
        } as unknown as FieldErrors<PredicateDraft>;

        expect(toPredicateError(errors)).toEqual({
            value: { value: undefined, start: "required", end: "invalid-range" },
        });
    });

    it("discards messages outside the domain error sets", () => {
        const errors = {
            field: { type: "custom", message: "empty" },
            operator: { type: "custom", message: "invalid-range" },
            value: {
                value: { type: "custom", message: "required" },
                start: { type: "custom", message: "empty" },
                end: { type: "custom", message: "unexpected" },
            },
        } as unknown as FieldErrors<PredicateDraft>;

        expect(toPredicateError(errors)).toBeUndefined();
    });
});

describe("draftFromPredicate", () => {
    it("round-trips the modifiers back out of a wire predicate", () => {
        const draft = draftFromPredicate(
            {
                path: KIND.path,
                operation: "EQUALS",
                values: ["UNIT"],
                negate: true,
                caseInsensitive: true,
            },
            "kind",
        );

        expect(draft).toMatchObject({
            field: "kind",
            operator: "EQUALS",
            value: "UNIT",
            negate: true,
            caseInsensitive: true,
        });
    });
});
