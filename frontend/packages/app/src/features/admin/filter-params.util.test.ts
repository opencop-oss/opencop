// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { MAX_FILTER_VALUE_LENGTH } from "@opencop/api";
import type { ActiveColumnFilterValue } from "@opencop/ui";
import type { ColumnFiltersState } from "@tanstack/react-table";
import { afterEach, describe, expect, it, vi } from "vitest";
import { createFilterParamSerializer, type FilterFieldDef } from "./filter-params.util";

/**
 * A synthetic two-field map, so these cover the shared grammar itself rather than any one grid's
 * column list. Each grid's own map is covered by its own test.
 */
const FIELDS = {
    title: {
        field: "name",
        operators: ["contains", "equals", "isEmpty"],
    },
    kind: { field: "type", operators: ["equals"], toWire: (v) => String(v).toUpperCase() },
    when: { field: "createdAt", operators: ["between"] },
    sortOnly: { field: "position" },
} satisfies Record<string, FilterFieldDef>;

const serializer = createFilterParamSerializer(FIELDS, "test-filters");

/** Builds the `{ id, value }` shape `ColumnFilterBuilder` writes into `ColumnFiltersState`. */
function chips(id: string, ...values: ActiveColumnFilterValue[]): ColumnFiltersState {
    return [{ id, value: values }];
}

afterEach(() => {
    vi.restoreAllMocks();
});

describe("createFilterParamSerializer — toFilterParams", () => {
    it("returns no params for an empty filter state", () => {
        expect(serializer.toFilterParams([])).toEqual([]);
    });

    it("maps the column id onto its backend field name", () => {
        expect(
            serializer.toFilterParams(chips("title", { operator: "contains", value: "jo" })),
        ).toEqual(["name,contains,jo"]);
    });

    it("applies the column's toWire normalizer", () => {
        expect(
            serializer.toFilterParams(chips("kind", { operator: "equals", value: "user" })),
        ).toEqual(["type,equals,USER"]);
    });

    it("emits the uniform three-token form for a no-value operator", () => {
        expect(serializer.toFilterParams(chips("title", { operator: "isEmpty" }))).toEqual([
            "name,isEmpty,",
        ]);
    });

    it("joins a between range with the .. separator", () => {
        expect(
            serializer.toFilterParams(
                chips("when", { operator: "between", value: "2026-01-01", valueTo: "2026-03-01" }),
            ),
        ).toEqual(["createdAt,between,2026-01-01..2026-03-01"]);
    });

    it("drops a between chip missing either endpoint", () => {
        expect(
            serializer.toFilterParams(chips("when", { operator: "between", value: "2026-01-01" })),
        ).toEqual([]);
        expect(
            serializer.toFilterParams(
                chips("when", { operator: "between", valueTo: "2026-03-01" }),
            ),
        ).toEqual([]);
    });

    it("drops an operator the column does not declare", () => {
        expect(
            serializer.toFilterParams(chips("title", { operator: "startsWith", value: "jo" })),
        ).toEqual([]);
    });

    it("drops a column that declares no operators at all", () => {
        // `sortOnly` exists to supply a sort-field mapping; it is not filterable.
        expect(
            serializer.toFilterParams(chips("sortOnly", { operator: "equals", value: "1" })),
        ).toEqual([]);
    });

    it("trims a value and drops it when nothing remains", () => {
        expect(
            serializer.toFilterParams(chips("title", { operator: "contains", value: "  jo  " })),
        ).toEqual(["name,contains,jo"]);
        expect(
            serializer.toFilterParams(chips("title", { operator: "contains", value: "   " })),
        ).toEqual([]);
    });

    it("clips a value to the server-enforced cap rather than letting it 400", () => {
        const tooLong = "x".repeat(MAX_FILTER_VALUE_LENGTH + 10);

        const [param] = serializer.toFilterParams(
            chips("title", { operator: "contains", value: tooLong }),
        );

        expect(param).toBe(`name,contains,${"x".repeat(MAX_FILTER_VALUE_LENGTH)}`);
    });

    it("preserves commas inside a value, which the backend's limit-3 split keeps whole", () => {
        expect(
            serializer.toFilterParams(chips("title", { operator: "equals", value: "Doe, John" })),
        ).toEqual(["name,equals,Doe, John"]);
    });

    it("keeps chips in order so the query key stays stable across renders", () => {
        expect(
            serializer.toFilterParams([
                { id: "kind", value: [{ operator: "equals", value: "user" }] },
                { id: "title", value: [{ operator: "contains", value: "a" }] },
            ]),
        ).toEqual(["type,equals,USER", "name,contains,a"]);
    });

    it("serializes several chips on one column, which AND on the backend", () => {
        expect(
            serializer.toFilterParams(
                chips(
                    "title",
                    { operator: "contains", value: "a" },
                    { operator: "contains", value: "b" },
                ),
            ),
        ).toEqual(["name,contains,a", "name,contains,b"]);
    });

    it("drops an unmapped column and says so, since that is a wiring mistake", () => {
        const error = vi.spyOn(console, "error").mockImplementation(() => {});

        expect(
            serializer.toFilterParams(chips("nope", { operator: "equals", value: "x" })),
        ).toEqual([]);
        expect(error).toHaveBeenCalledWith(expect.stringContaining("test-filters"));
    });
});

describe("createFilterParamSerializer — toSortField", () => {
    it("maps a column id onto its backend field", () => {
        expect(serializer.toSortField("kind")).toBe("type");
    });

    it("maps a sort-only column, which has no operators", () => {
        expect(serializer.toSortField("sortOnly")).toBe("position");
    });

    it("falls back to the id when the names already match", () => {
        expect(serializer.toSortField("unknown")).toBe("unknown");
    });
});
