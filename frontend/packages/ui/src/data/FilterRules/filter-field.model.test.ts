// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { FilterDraft, Predicate, PredicateDraft } from "./predicate.model";
import {
    activeNotices,
    draftFromFilter,
    hasFallbackRow,
    isInertUnderAll,
    parseFilter,
    resolveFilter,
    retargetDraftToField,
    type FilterField,
} from "./filter-field.model";

const PATH = "properties.opencop.code";

const PLAIN: FilterField = {
    id: "plain",
    labelKey: "plain",
    path: "properties.opencop.name",
    order: 10,
};

/** Claims a one-value slice at 0..1 and folds an "echo" companion after it. */
const SLICE: FilterField = {
    id: "acme:slice",
    labelKey: "slice",
    path: PATH,
    order: 30,
    operators: ["SUBSTRING_IN"],
    newDraft: (id) => ({
        id,
        field: "acme:slice",
        operator: "SUBSTRING_IN",
        value: { value: [], start: "0", end: "1" },
    }),
    companions: (predicate) => [{ ...predicate, offset: 5, length: 1 } as Predicate],
    claims: (predicate, next) =>
        predicate.path === PATH &&
        predicate.operation === "SUBSTRING_IN" &&
        predicate.offset === 0 &&
        predicate.length === 1
            ? { folds: next?.operation === "SUBSTRING_IN" && next.offset === 5 ? 1 : 0 }
            : null,
    noticeKey: "slice.notice",
};

const FIELDS = [PLAIN, SLICE];

const slice = (values: string[]): Predicate => ({
    path: PATH,
    operation: "SUBSTRING_IN",
    values,
    offset: 0,
    length: 1,
    negate: false,
    caseInsensitive: false,
});
const echo = (values: string[]): Predicate => ({ ...slice(values), offset: 5 }) as Predicate;
const plain = (value: string): Predicate => ({
    path: PLAIN.path,
    operation: "EQUALS",
    values: [value],
    negate: false,
    caseInsensitive: false,
});

describe("resolveFilter", () => {
    it("lets a claiming field take a predicate and fold its companion into the same row", () => {
        const { rows } = resolveFilter(
            { combine: "ANY", predicates: [slice(["a"]), echo(["a"]), plain("x")] },
            FIELDS,
        );

        expect(rows.map((row) => row.field?.id)).toEqual(["acme:slice", "plain"]);
    });

    it("matches a field without claims by its path", () => {
        const { rows } = resolveFilter({ combine: "ALL", predicates: [plain("x")] }, FIELDS);

        expect(rows[0]?.draft).toMatchObject({ field: "plain", operator: "EQUALS", value: "x" });
    });

    it("asks the provider's fallback for a predicate no listed field holds", () => {
        const attribute: FilterField = {
            id: "attribute:callsign",
            labelText: "callsign",
            path: "properties.opencop.attributes.callsign",
            order: 1000,
        };
        const saved: Predicate = { ...plain("x"), path: attribute.path };

        const { rows } = resolveFilter(
            { combine: "ALL", predicates: [saved] },
            FIELDS,
            () => attribute,
        );

        expect(rows[0]?.field).toBe(attribute);
    });

    it("keeps a predicate nobody holds verbatim, as a fallback row", () => {
        const orphan: Predicate = { ...slice(["a"]), path: "properties.opencop.elsewhere" };

        const { rows } = resolveFilter({ combine: "ANY", predicates: [orphan] }, FIELDS);

        expect(rows[0]?.field).toBeUndefined();
        expect(rows[0]?.draft).toMatchObject({ raw: orphan });
    });

    it("reads a pair as two fallback rows once the field that wrote it is gone", () => {
        const draft = draftFromFilter({ combine: "ANY", predicates: [slice(["a"]), echo(["a"])] }, [
            PLAIN,
        ]);

        expect(draft.predicates).toHaveLength(2);
        expect(hasFallbackRow(draft)).toBe(true);
    });
});

describe("parseFilter", () => {
    it("saves a row followed by its field's companions", () => {
        const draft: FilterDraft = {
            combinator: "ANY",
            predicates: [
                {
                    id: "1",
                    field: "acme:slice",
                    operator: "SUBSTRING_IN",
                    value: { value: ["a"], start: "0", end: "1" },
                },
            ],
        };

        const res = parseFilter(draft, FIELDS);

        expect(res).toEqual({
            ok: true,
            result: { combine: "ANY", predicates: [slice(["a"]), echo(["a"])] },
        });
    });

    it("saves a fallback row back verbatim and in place", () => {
        const orphan: Predicate = {
            ...plain("y"),
            path: "properties.opencop.elsewhere",
            negate: true,
        };
        const saved = { combine: "ALL" as const, predicates: [plain("x"), orphan, plain("z")] };

        const res = parseFilter(draftFromFilter(saved, FIELDS), FIELDS);

        expect(res).toEqual({ ok: true, result: saved });
    });

    it("refuses a row whose field cannot produce its companions now", () => {
        const broken: FilterField = { ...SLICE, companions: () => undefined };
        const draft: FilterDraft = {
            combinator: "ANY",
            predicates: [
                {
                    id: "1",
                    field: "acme:slice",
                    operator: "SUBSTRING_IN",
                    value: { value: ["a"], start: "0", end: "1" },
                },
            ],
        };

        const res = parseFilter(draft, [PLAIN, broken]);

        expect(res).toEqual({ ok: false, errors: { "1": { field: "unavailable" } } });
    });
});

describe("retargetDraftToField", () => {
    const row: PredicateDraft = {
        id: "r",
        field: "plain",
        operator: "EQUALS",
        value: "x",
        negate: true,
    };

    it("starts a row from the field's newDraft", () => {
        expect(retargetDraftToField(row, "acme:slice", FIELDS)).toMatchObject({
            id: "r",
            field: "acme:slice",
            operator: "SUBSTRING_IN",
            value: { start: "0", end: "1" },
        });
    });

    it("leaves the row unchanged when newDraft has nothing (or threw, contained)", () => {
        const refusing: FilterField = { ...SLICE, newDraft: () => undefined };

        expect(retargetDraftToField(row, "acme:slice", [PLAIN, refusing])).toBe(row);
    });

    it("drops a pinned shape when the row leaves that field", () => {
        const pinned = retargetDraftToField(row, "acme:slice", FIELDS);

        expect(retargetDraftToField(pinned, "plain", FIELDS)).toMatchObject({
            field: "plain",
            operator: "EQUALS",
            value: "",
        });
    });

    it("replaces an operator the new field does not offer", () => {
        const only: FilterField = { ...PLAIN, id: "only", operators: ["IN"] };

        expect(retargetDraftToField(row, "only", [PLAIN, only])).toMatchObject({
            field: "only",
            operator: "IN",
            value: [],
            negate: true,
        });
    });
});

describe("the ANY rule and notices", () => {
    const sliceRow: PredicateDraft = {
        id: "1",
        field: "acme:slice",
        operator: "SUBSTRING_IN",
        value: { value: ["a"], start: "0", end: "1" },
    };

    it("calls a row whose field has companions inert under ALL", () => {
        expect(isInertUnderAll({ combinator: "ALL", predicates: [sliceRow] }, FIELDS)).toBe(true);
        expect(isInertUnderAll({ combinator: "ANY", predicates: [sliceRow] }, FIELDS)).toBe(false);
    });

    it("lists each used field's notice once", () => {
        expect(
            activeNotices(
                { combinator: "ANY", predicates: [sliceRow, { ...sliceRow, id: "2" }] },
                FIELDS,
            ),
        ).toEqual(["slice.notice"]);
    });
});

describe("a claim or companions that returns the wrong thing", () => {
    const three = { combine: "ANY" as const, predicates: [plain("a"), plain("b"), plain("c")] };
    const greedy = (claim: unknown): FilterField => ({
        id: "acme:greedy",
        labelKey: "greedy",
        path: PATH,
        order: 1,
        claims: () => claim as never,
    });

    it("cannot take a predicate on another field's path", () => {
        const { rows } = resolveFilter(three, [PLAIN, greedy({ folds: 0 })]);

        expect(rows.map((row) => row.field?.id)).toEqual(["plain", "plain", "plain"]);
    });

    it.each([[{ folds: 2 }], [{ folds: -1 }], [{ folds: Number.NaN }], [true], [{}]])(
        "cannot fold away predicates with %j",
        (claim) => {
            const own = {
                combine: "ANY" as const,
                predicates: [slice(["a"]), slice(["b"]), slice(["c"])],
            };

            const { rows } = resolveFilter(own, [{ ...greedy(claim) }]);

            expect(rows).toHaveLength(3);
        },
    );

    it.each([
        ["not a list", () => 42],
        // An inherited key is not an operator the predicate language knows.
        [
            "a predicate with a prototype key as its operation",
            () => [{ path: SLICE.path, operation: "toString", values: ["a"] }],
        ],
    ])("refuses the save when companions returns %s", (_case, companions) => {
        const broken: FilterField = { ...SLICE, companions: companions as never };
        const draft: FilterDraft = {
            combinator: "ANY",
            predicates: [
                {
                    id: "1",
                    field: "acme:slice",
                    operator: "SUBSTRING_IN",
                    value: { value: ["a"], start: "0", end: "1" },
                },
            ],
        };

        const res = parseFilter(draft, [PLAIN, broken]);

        expect(res.ok).toBe(false);
    });
});
