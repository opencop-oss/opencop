// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import {
    createOcDateFilter,
    ocBooleanFilter,
    ocDateFilter,
    ocEnumFilter,
    ocNumericFilter,
    ocStringFilter,
} from "./oc-filter-fns";
import type { ActiveColumnFilterValue } from "./column-filter.model";

const COL = "x";
const noMeta = () => {};

function makeRow(value: unknown) {
    return { getValue: () => value } as unknown as Parameters<typeof ocStringFilter>[0];
}

// ─── ocStringFilter ───────────────────────────────────────────────────────────

describe("ocStringFilter", () => {
    it("equals: case-insensitive match", () => {
        const fv: ActiveColumnFilterValue = { operator: "equals", value: "HELLO" };
        expect(ocStringFilter(makeRow("hello"), COL, fv, noMeta)).toBe(true);
        expect(ocStringFilter(makeRow("Hello"), COL, fv, noMeta)).toBe(true);
        expect(ocStringFilter(makeRow("world"), COL, fv, noMeta)).toBe(false);
    });

    it("contains: matches substring case-insensitively", () => {
        const fv: ActiveColumnFilterValue = { operator: "contains", value: "ELL" };
        expect(ocStringFilter(makeRow("hello"), COL, fv, noMeta)).toBe(true);
        expect(ocStringFilter(makeRow("world"), COL, fv, noMeta)).toBe(false);
    });

    it("notContains: excludes substring", () => {
        const fv: ActiveColumnFilterValue = { operator: "notContains", value: "xyz" };
        expect(ocStringFilter(makeRow("hello"), COL, fv, noMeta)).toBe(true);
        expect(ocStringFilter(makeRow("xyz world"), COL, fv, noMeta)).toBe(false);
    });

    it("startsWith: matches prefix case-insensitively", () => {
        const fv: ActiveColumnFilterValue = { operator: "startsWith", value: "HE" };
        expect(ocStringFilter(makeRow("hello"), COL, fv, noMeta)).toBe(true);
        expect(ocStringFilter(makeRow("world"), COL, fv, noMeta)).toBe(false);
    });

    it("isEmpty: matches only empty string", () => {
        expect(ocStringFilter(makeRow(""), COL, { operator: "isEmpty" }, noMeta)).toBe(true);
        expect(ocStringFilter(makeRow("x"), COL, { operator: "isEmpty" }, noMeta)).toBe(false);
    });

    it("isNotEmpty: matches non-empty string", () => {
        expect(ocStringFilter(makeRow("x"), COL, { operator: "isNotEmpty" }, noMeta)).toBe(true);
        expect(ocStringFilter(makeRow(""), COL, { operator: "isNotEmpty" }, noMeta)).toBe(false);
    });

    describe("autoRemove", () => {
        it("removes when value is empty string for value-requiring operators", () => {
            expect(ocStringFilter.autoRemove!({ operator: "contains", value: "" })).toBe(true);
            expect(ocStringFilter.autoRemove!({ operator: "notContains", value: "" })).toBe(true);
            expect(ocStringFilter.autoRemove!({ operator: "startsWith", value: "" })).toBe(true);
            expect(ocStringFilter.autoRemove!({ operator: "equals", value: "" })).toBe(true);
        });

        it("does not remove isEmpty / isNotEmpty (no value needed)", () => {
            expect(ocStringFilter.autoRemove!({ operator: "isEmpty" })).toBe(false);
            expect(ocStringFilter.autoRemove!({ operator: "isNotEmpty" })).toBe(false);
        });

        it("keeps when value is present", () => {
            expect(ocStringFilter.autoRemove!({ operator: "contains", value: "abc" })).toBe(false);
        });

        it("removes when filter array is empty", () => {
            expect(ocStringFilter.autoRemove!([])).toBe(true);
        });
    });
});

// ─── ocEnumFilter ─────────────────────────────────────────────────────────────

describe("ocEnumFilter", () => {
    it("equals: exact-case match", () => {
        const fv: ActiveColumnFilterValue = { operator: "equals", value: "ADMIN" };
        expect(ocEnumFilter(makeRow("ADMIN"), COL, fv, noMeta)).toBe(true);
        expect(ocEnumFilter(makeRow("admin"), COL, fv, noMeta)).toBe(false);
    });

    it("notEquals: excludes the value", () => {
        const fv: ActiveColumnFilterValue = { operator: "notEquals", value: "ADMIN" };
        expect(ocEnumFilter(makeRow("OWNER"), COL, fv, noMeta)).toBe(true);
        expect(ocEnumFilter(makeRow("ADMIN"), COL, fv, noMeta)).toBe(false);
    });

    it("inList: matches any value in the list", () => {
        const fv: ActiveColumnFilterValue = { operator: "inList", values: ["ADMIN", "OWNER"] };
        expect(ocEnumFilter(makeRow("ADMIN"), COL, fv, noMeta)).toBe(true);
        expect(ocEnumFilter(makeRow("OWNER"), COL, fv, noMeta)).toBe(true);
        expect(ocEnumFilter(makeRow("VIEWER"), COL, fv, noMeta)).toBe(false);
    });

    describe("autoRemove", () => {
        it("removes when no value and no values array", () => {
            expect(ocEnumFilter.autoRemove!({ operator: "equals" })).toBe(true);
        });

        it("removes when values array is empty", () => {
            expect(ocEnumFilter.autoRemove!({ operator: "inList", values: [] })).toBe(true);
        });

        it("keeps when value is present", () => {
            expect(ocEnumFilter.autoRemove!({ operator: "equals", value: "ADMIN" })).toBe(false);
        });

        it("keeps when values array is populated", () => {
            expect(ocEnumFilter.autoRemove!({ operator: "inList", values: ["ADMIN"] })).toBe(false);
        });
    });
});

// ─── ocNumericFilter ──────────────────────────────────────────────────────────

describe("ocNumericFilter", () => {
    it("eq / neq", () => {
        expect(ocNumericFilter(makeRow(5), COL, { operator: "eq", value: 5 }, noMeta)).toBe(true);
        expect(ocNumericFilter(makeRow(5), COL, { operator: "eq", value: 6 }, noMeta)).toBe(false);
        expect(ocNumericFilter(makeRow(5), COL, { operator: "neq", value: 6 }, noMeta)).toBe(true);
        expect(ocNumericFilter(makeRow(5), COL, { operator: "neq", value: 5 }, noMeta)).toBe(false);
    });

    it("gt: strictly greater than (boundary excluded)", () => {
        expect(ocNumericFilter(makeRow(6), COL, { operator: "gt", value: 5 }, noMeta)).toBe(true);
        expect(ocNumericFilter(makeRow(5), COL, { operator: "gt", value: 5 }, noMeta)).toBe(false);
        expect(ocNumericFilter(makeRow(4), COL, { operator: "gt", value: 5 }, noMeta)).toBe(false);
    });

    it("gte: boundary included", () => {
        expect(ocNumericFilter(makeRow(5), COL, { operator: "gte", value: 5 }, noMeta)).toBe(true);
        expect(ocNumericFilter(makeRow(4), COL, { operator: "gte", value: 5 }, noMeta)).toBe(false);
    });

    it("lt: strictly less than (boundary excluded)", () => {
        expect(ocNumericFilter(makeRow(4), COL, { operator: "lt", value: 5 }, noMeta)).toBe(true);
        expect(ocNumericFilter(makeRow(5), COL, { operator: "lt", value: 5 }, noMeta)).toBe(false);
        expect(ocNumericFilter(makeRow(6), COL, { operator: "lt", value: 5 }, noMeta)).toBe(false);
    });

    it("lte: boundary included", () => {
        expect(ocNumericFilter(makeRow(5), COL, { operator: "lte", value: 5 }, noMeta)).toBe(true);
        expect(ocNumericFilter(makeRow(6), COL, { operator: "lte", value: 5 }, noMeta)).toBe(false);
    });

    it("NaN cell: fails eq/gt/gte/lt/lte but passes neq", () => {
        expect(ocNumericFilter(makeRow(NaN), COL, { operator: "eq", value: 5 }, noMeta)).toBe(
            false,
        );
        expect(ocNumericFilter(makeRow(NaN), COL, { operator: "gt", value: 5 }, noMeta)).toBe(
            false,
        );
        expect(ocNumericFilter(makeRow(NaN), COL, { operator: "gte", value: 5 }, noMeta)).toBe(
            false,
        );
        expect(ocNumericFilter(makeRow(NaN), COL, { operator: "lt", value: 5 }, noMeta)).toBe(
            false,
        );
        expect(ocNumericFilter(makeRow(NaN), COL, { operator: "lte", value: 5 }, noMeta)).toBe(
            false,
        );
        expect(ocNumericFilter(makeRow(NaN), COL, { operator: "neq", value: 5 }, noMeta)).toBe(
            true,
        );
    });
});

// ─── ocBooleanFilter ──────────────────────────────────────────────────────────

describe("ocBooleanFilter", () => {
    it("isTrue matches truthy cells", () => {
        expect(ocBooleanFilter(makeRow(true), COL, { operator: "isTrue" }, noMeta)).toBe(true);
        expect(ocBooleanFilter(makeRow(1), COL, { operator: "isTrue" }, noMeta)).toBe(true);
        expect(ocBooleanFilter(makeRow("y"), COL, { operator: "isTrue" }, noMeta)).toBe(true);
    });

    it("isFalse matches falsy cells", () => {
        expect(ocBooleanFilter(makeRow(false), COL, { operator: "isFalse" }, noMeta)).toBe(true);
        expect(ocBooleanFilter(makeRow(0), COL, { operator: "isFalse" }, noMeta)).toBe(true);
        expect(ocBooleanFilter(makeRow(""), COL, { operator: "isFalse" }, noMeta)).toBe(true);
    });

    it("isTrue rejects falsy non-null values (0, empty string)", () => {
        expect(ocBooleanFilter(makeRow(0), COL, { operator: "isTrue" }, noMeta)).toBe(false);
        expect(ocBooleanFilter(makeRow(""), COL, { operator: "isTrue" }, noMeta)).toBe(false);
    });

    it("null cell returns false regardless of operator (null guard)", () => {
        expect(ocBooleanFilter(makeRow(null), COL, { operator: "isTrue" }, noMeta)).toBe(false);
        expect(ocBooleanFilter(makeRow(null), COL, { operator: "isFalse" }, noMeta)).toBe(false);
        expect(ocBooleanFilter(makeRow(undefined), COL, { operator: "isTrue" }, noMeta)).toBe(
            false,
        );
    });

    describe("autoRemove", () => {
        it("removes when no operator", () => {
            expect(ocBooleanFilter.autoRemove!({} as ActiveColumnFilterValue)).toBe(true);
            expect(ocBooleanFilter.autoRemove!([])).toBe(true);
        });

        it("keeps when operator is set", () => {
            expect(ocBooleanFilter.autoRemove!({ operator: "isTrue" })).toBe(false);
            expect(ocBooleanFilter.autoRemove!({ operator: "isFalse" })).toBe(false);
        });
    });
});

// ─── ocDateFilter ─────────────────────────────────────────────────────────────

describe("ocDateFilter", () => {
    it("null / invalid cell returns false", () => {
        expect(
            ocDateFilter(makeRow(null), COL, { operator: "before", value: "2099-01-01" }, noMeta),
        ).toBe(false);
        expect(
            ocDateFilter(
                makeRow("not-a-date"),
                COL,
                { operator: "before", value: "2099-01-01" },
                noMeta,
            ),
        ).toBe(false);
    });

    it("before / after", () => {
        const pivot = "2024-06-01";
        expect(
            ocDateFilter(makeRow("2024-05-31"), COL, { operator: "before", value: pivot }, noMeta),
        ).toBe(true);
        expect(
            ocDateFilter(makeRow("2024-06-01"), COL, { operator: "before", value: pivot }, noMeta),
        ).toBe(false);
        expect(
            ocDateFilter(makeRow("2024-06-02"), COL, { operator: "after", value: pivot }, noMeta),
        ).toBe(true);
        expect(
            ocDateFilter(makeRow("2024-06-01"), COL, { operator: "after", value: pivot }, noMeta),
        ).toBe(false);
    });

    it("between: inclusive on both boundaries", () => {
        const fv: ActiveColumnFilterValue = {
            operator: "between",
            value: "2024-01-01",
            valueTo: "2024-01-31",
        };
        expect(ocDateFilter(makeRow("2024-01-01"), COL, fv, noMeta)).toBe(true);
        expect(ocDateFilter(makeRow("2024-01-31"), COL, fv, noMeta)).toBe(true);
        expect(ocDateFilter(makeRow("2024-01-15"), COL, fv, noMeta)).toBe(true);
        expect(ocDateFilter(makeRow("2023-12-31"), COL, fv, noMeta)).toBe(false);
        expect(ocDateFilter(makeRow("2024-02-01"), COL, fv, noMeta)).toBe(false);
    });

    // The bounds operators compare whole UTC calendar days, matching how the server resolves the
    // same operators. Cells carrying a time-of-day are the case that made these disagree: a naive
    // instant comparison lets `after` match the named day and truncates `between`'s end day.
    it("before / after: a timestamped cell is judged on its calendar day", () => {
        const pivot = "2024-06-01";
        expect(
            ocDateFilter(
                makeRow("2024-06-01T14:00:00Z"),
                COL,
                { operator: "after", value: pivot },
                noMeta,
            ),
        ).toBe(false);
        expect(
            ocDateFilter(
                makeRow("2024-06-02T00:30:00Z"),
                COL,
                { operator: "after", value: pivot },
                noMeta,
            ),
        ).toBe(true);
        expect(
            ocDateFilter(
                makeRow("2024-06-01T00:00:01Z"),
                COL,
                { operator: "before", value: pivot },
                noMeta,
            ),
        ).toBe(false);
        expect(
            ocDateFilter(
                makeRow("2024-05-31T23:59:59Z"),
                COL,
                { operator: "before", value: pivot },
                noMeta,
            ),
        ).toBe(true);
    });

    it("resolves the cell's day in the supplied zone, not UTC", () => {
        // 18:00Z on the 1st is already the 2nd in Taipei (UTC+8), so a filter for the 2nd must
        // match the row the grid renders as the 2nd. This is the client-side half of the `tz`
        // contract the server-paged grids send.
        const taipei = createOcDateFilter("Asia/Taipei");
        const cell = "2026-03-01T18:00:00Z";
        const onTheSecond: ActiveColumnFilterValue = {
            operator: "between",
            value: "2026-03-02",
            valueTo: "2026-03-02",
        };

        expect(taipei(makeRow(cell), COL, onTheSecond, noMeta)).toBe(true);
        // The UTC-resolved fallback still places it on the 1st.
        expect(ocDateFilter(makeRow(cell), COL, onTheSecond, noMeta)).toBe(false);
    });

    it("resolves date-only bounds as days in the zone, not as UTC instants", () => {
        // Regression: `new Date("2026-08-13")` is UTC midnight, which is 2026-08-12 in every
        // UTC-negative zone — so the bound landed a day early for all US zones. The earlier zone
        // cases used UTC and Asia/Taipei, both non-negative, where the misparse is invisible.
        const chicago = createOcDateFilter("America/Chicago");
        // Renders as 08/13 in Chicago (13:00 local).
        const cell = "2026-08-13T18:00:00Z";

        expect(
            chicago(
                makeRow(cell),
                COL,
                { operator: "between", value: "2026-08-13", valueTo: "2026-08-13" },
                noMeta,
            ),
        ).toBe(true);
        expect(
            chicago(makeRow(cell), COL, { operator: "before", value: "2026-08-14" }, noMeta),
        ).toBe(true);
        expect(
            chicago(makeRow(cell), COL, { operator: "after", value: "2026-08-13" }, noMeta),
        ).toBe(false);
    });

    it("between: a timestamped cell on the end day is included", () => {
        const fv: ActiveColumnFilterValue = {
            operator: "between",
            value: "2024-01-01",
            valueTo: "2024-01-31",
        };
        expect(ocDateFilter(makeRow("2024-01-31T23:59:00Z"), COL, fv, noMeta)).toBe(true);
        expect(ocDateFilter(makeRow("2024-01-01T00:00:01Z"), COL, fv, noMeta)).toBe(true);
        expect(ocDateFilter(makeRow("2024-02-01T00:00:01Z"), COL, fv, noMeta)).toBe(false);
    });

    it("between: passes through when either bound is missing (treated as no-op)", () => {
        expect(
            ocDateFilter(
                makeRow("2024-01-15"),
                COL,
                { operator: "between", value: "2024-01-01" },
                noMeta,
            ),
        ).toBe(true);
        expect(
            ocDateFilter(
                makeRow("2024-01-15"),
                COL,
                { operator: "between", valueTo: "2024-01-31" },
                noMeta,
            ),
        ).toBe(true);
    });

    it("last7Days: includes a cell from yesterday, excludes 8 days ago", () => {
        const yesterday = new Date(Date.now() - 1 * 24 * 60 * 60 * 1000);
        const eightDaysAgo = new Date(Date.now() - 8 * 24 * 60 * 60 * 1000);
        expect(
            ocDateFilter(makeRow(yesterday.toISOString()), COL, { operator: "last7Days" }, noMeta),
        ).toBe(true);
        expect(
            ocDateFilter(
                makeRow(eightDaysAgo.toISOString()),
                COL,
                { operator: "last7Days" },
                noMeta,
            ),
        ).toBe(false);
    });

    it("last30Days: includes a cell from 20 days ago, excludes 31 days ago", () => {
        const twentyDaysAgo = new Date(Date.now() - 20 * 24 * 60 * 60 * 1000);
        const thirtyOneDaysAgo = new Date(Date.now() - 31 * 24 * 60 * 60 * 1000);
        expect(
            ocDateFilter(
                makeRow(twentyDaysAgo.toISOString()),
                COL,
                { operator: "last30Days" },
                noMeta,
            ),
        ).toBe(true);
        expect(
            ocDateFilter(
                makeRow(thirtyOneDaysAgo.toISOString()),
                COL,
                { operator: "last30Days" },
                noMeta,
            ),
        ).toBe(false);
    });

    describe("autoRemove", () => {
        it("does not remove last7Days / last30Days (no value needed)", () => {
            expect(ocDateFilter.autoRemove!({ operator: "last7Days" })).toBe(false);
            expect(ocDateFilter.autoRemove!({ operator: "last30Days" })).toBe(false);
        });

        it("removes before / after when value is absent", () => {
            expect(ocDateFilter.autoRemove!({ operator: "before" })).toBe(true);
            expect(ocDateFilter.autoRemove!({ operator: "after" })).toBe(true);
        });

        it("keeps before / after when value is present", () => {
            expect(ocDateFilter.autoRemove!({ operator: "before", value: "2024-01-01" })).toBe(
                false,
            );
        });

        it("removes between when either bound is absent", () => {
            expect(ocDateFilter.autoRemove!({ operator: "between", value: "2024-01-01" })).toBe(
                true,
            );
            expect(ocDateFilter.autoRemove!({ operator: "between", valueTo: "2024-01-31" })).toBe(
                true,
            );
        });

        it("keeps between when both bounds are present", () => {
            expect(
                ocDateFilter.autoRemove!({
                    operator: "between",
                    value: "2024-01-01",
                    valueTo: "2024-01-31",
                }),
            ).toBe(false);
        });
    });
});
