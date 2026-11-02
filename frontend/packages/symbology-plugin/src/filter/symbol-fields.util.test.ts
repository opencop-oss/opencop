// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import {
    draftFromFilter,
    isInertUnderAll,
    parseFilter,
    retargetDraftToField,
    type FilterField,
    type Predicate,
    type PredicateDraft,
} from "@opencop/ui";
import {
    STANDARD_IDENTITY,
    SYMBOL_FILTER_FIELDS,
    SYMBOL_SET,
    sliceDraft,
} from "./symbol-fields.util";

const SIDC = "opencop.symbology:sidc";
const IDENTITY = "opencop.symbology:standardIdentity";
const SET = "opencop.symbology:symbolSet";
const PATH = "properties.opencop.element.symbol";

/** The SIDC fields beside a plain field, as a host would list them. */
const NAME: FilterField = {
    id: "displayName",
    labelKey: "n",
    path: "properties.opencop.displayName",
    order: 20,
};
const FIELDS: readonly FilterField[] = [NAME, ...SYMBOL_FILTER_FIELDS];

const identityRow = (code: string) =>
    sliceDraft(IDENTITY, STANDARD_IDENTITY, { id: "p1", values: [code] });

const sub = (values: string[], offset: number, length: number, negate = false): Predicate => ({
    path: PATH,
    operation: "SUBSTRING_IN",
    values,
    offset,
    length,
    negate,
    caseInsensitive: false,
});

function saved(draft: PredicateDraft[]) {
    const res = parseFilter({ combinator: "ANY", predicates: draft }, FIELDS);
    if (!res.ok) throw new Error(JSON.stringify(res.errors));
    return res.result;
}

const fieldOf = (predicate: Predicate) =>
    draftFromFilter({ combine: "ALL", predicates: [predicate] }, FIELDS).predicates[0]?.field;

describe("the SIDC slices", () => {
    it("pin each field to the slice it names", () => {
        expect(STANDARD_IDENTITY).toMatchObject({ offset: 3, length: 1 });
        expect(SYMBOL_SET).toMatchObject({ offset: 4, length: 2 });
    });

    it("offer every catalog value as a choice", () => {
        expect(STANDARD_IDENTITY.options).toHaveLength(7);
        expect(SYMBOL_SET.options).toHaveLength(22);
    });

    it("pin the slice when a row is retargeted onto one, dropping Negate", () => {
        const before: PredicateDraft = {
            id: "1",
            field: "displayName",
            operator: "EQUALS",
            value: "Alpha",
            negate: true,
        };

        const after = retargetDraftToField(before, SET, FIELDS);

        expect(after).toMatchObject({
            id: "1",
            field: SET,
            operator: "SUBSTRING_IN",
            value: { value: [], start: "4", end: "6" },
        });
        expect(after.negate).toBeUndefined();
    });

    it("drop the pinned slice when a row is retargeted away", () => {
        const after = retargetDraftToField(
            sliceDraft(SET, SYMBOL_SET, { id: "1", values: ["10"] }),
            "displayName",
            FIELDS,
        );

        expect(after).toMatchObject({
            id: "1",
            field: "displayName",
            operator: "EQUALS",
            value: "",
        });
    });
});

describe("2525C companion expansion", () => {
    it("emits the 2525D predicate followed by its 2525C companion", () => {
        expect(saved([identityRow("6")]).predicates).toEqual([
            sub(["6"], 3, 1),
            sub(["H", "K"], 1, 1),
        ]);
    });

    it("expands a symbol set to its battle dimension", () => {
        const predicates = saved([
            sliceDraft(SET, SYMBOL_SET, { id: "p1", values: ["01"] }),
        ]).predicates;

        expect(predicates).toHaveLength(2);
        expect(predicates[1]).toMatchObject({ values: ["A"], offset: 2, length: 1 });
    });

    it("carries the row's negate flag onto the companion", () => {
        const predicates = saved([{ ...identityRow("3"), negate: true }]).predicates;

        expect(predicates.every((p) => p.negate)).toBe(true);
    });

    it("leaves other conditions alone", () => {
        expect(
            saved([{ id: "p1", field: "displayName", operator: "EQUALS", value: "Alpha" }])
                .predicates,
        ).toHaveLength(1);
    });

    it("folds the companion back into one row when reopened", () => {
        const reopened = draftFromFilter(saved([identityRow("6")]), FIELDS);

        expect(reopened.predicates).toHaveLength(1);
        expect(reopened.predicates[0]).toMatchObject({
            field: IDENTITY,
            operator: "SUBSTRING_IN",
            value: { value: ["6"], start: "3", end: "4" },
        });
    });

    it("round-trips a whole filter back to the conditions the user built", () => {
        const filter = saved([
            identityRow("6"),
            sliceDraft(SET, SYMBOL_SET, { id: "p2", values: ["01"] }),
        ]);

        expect(filter.predicates).toHaveLength(4);
        expect(draftFromFilter(filter, FIELDS).predicates.map((p) => p.field)).toEqual([
            IDENTITY,
            SET,
        ]);
    });

    it("does not swallow a hand-built predicate that merely sits next to one", () => {
        // Companion-shaped, but with values the conversion would never produce —
        // it's the operator's own rule and must survive.
        const filter = {
            combine: "ANY" as const,
            predicates: [sub(["6"], 3, 1), sub(["Z"], 1, 1)],
        };

        expect(draftFromFilter(filter, FIELDS).predicates).toHaveLength(2);
    });

    it("makes a slice condition need the Any combinator", () => {
        expect(isInertUnderAll({ combinator: "ALL", predicates: [identityRow("6")] }, FIELDS)).toBe(
            true,
        );
        expect(
            isInertUnderAll(
                {
                    combinator: "ALL",
                    predicates: [
                        { id: "p1", field: "displayName", operator: "EQUALS", value: "Alpha" },
                    ],
                },
                FIELDS,
            ),
        ).toBe(false);
    });
});

describe("pre-existing SIDC filters", () => {
    it("adopt one that looks exactly like the picker's output", () => {
        expect(fieldOf(sub(["6"], 3, 1))).toBe(IDENTITY);
        expect(fieldOf(sub(["01"], 4, 2))).toBe(SET);
    });

    it("leave anything the picker could not have produced on the SIDC row", () => {
        // A single-select picklist cannot hold two values or an off-list code, so
        // adopting these would lose data on the next save.
        expect(fieldOf(sub(["3", "6"], 3, 1))).toBe(SIDC);
        expect(fieldOf(sub(["9"], 3, 1))).toBe(SIDC);
        expect(fieldOf(sub(["6"], 3, 1, true))).toBe(SIDC);
        expect(fieldOf(sub(["10"], 0, 2))).toBe(SIDC);
    });

    it("do not add a 2525C companion to one left on the SIDC row", () => {
        const reopened = draftFromFilter(
            { combine: "ALL", predicates: [sub(["3", "6"], 3, 1)] },
            FIELDS,
        );
        const res = parseFilter(reopened, FIELDS);

        expect(res.ok && res.result.predicates).toHaveLength(1);
    });
});

describe("summaries", () => {
    const identity = SYMBOL_FILTER_FIELDS.find((field) => field.id === IDENTITY);
    const t = ((key: string, options?: { defaultValue?: string }) =>
        options?.defaultValue ?? key) as never;

    it("name a picked value instead of its code", () => {
        expect(identity?.formatValue?.(sub(["6"], 3, 1), t)).toBe("Hostile/Faker");
    });

    it("fall back to the raw code for a value the catalog no longer has", () => {
        expect(identity?.formatValue?.(sub(["9"], 3, 1), t)).toBe("9");
    });
});
