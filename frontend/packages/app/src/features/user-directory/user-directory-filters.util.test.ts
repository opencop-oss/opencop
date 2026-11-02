// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { MAX_FILTER_VALUE_LENGTH } from "@opencop/api";
import type { ActiveColumnFilterValue } from "@opencop/ui";
import type { ColumnFiltersState } from "@tanstack/react-table";
import { describe, expect, it } from "vitest";
import {
    toUserDirectoryFilterParams,
    toUserDirectorySortField,
} from "./user-directory-filters.util";

/** Builds the `{ id, value }` shape `ColumnFilterBuilder` writes into `ColumnFiltersState`. */
function chips(id: string, ...values: ActiveColumnFilterValue[]): ColumnFiltersState {
    return [{ id, value: values }];
}

describe("toUserDirectoryFilterParams", () => {
    it("returns no params for an empty filter state", () => {
        expect(toUserDirectoryFilterParams([])).toEqual([]);
    });

    it("serializes a string chip as field,operator,value", () => {
        expect(
            toUserDirectoryFilterParams(chips("email", { operator: "contains", value: "jo" })),
        ).toEqual(["email,contains,jo"]);
    });

    it("maps column ids to their backend field names", () => {
        // The grid calls these rowType/isAdmin; the API calls them type/admin.
        expect(
            toUserDirectoryFilterParams(chips("rowType", { operator: "equals", value: "user" })),
        ).toEqual(["type,equals,USER"]);
        expect(
            toUserDirectoryFilterParams(chips("isAdmin", { operator: "equals", value: "true" })),
        ).toEqual(["admin,equals,true"]);
    });

    it("upper-cases the entry type, which the grid stores lower-cased", () => {
        expect(
            toUserDirectoryFilterParams(chips("rowType", { operator: "equals", value: "group" })),
        ).toEqual(["type,equals,GROUP"]);
    });

    it("passes boolean-valued enum options through unchanged", () => {
        expect(
            toUserDirectoryFilterParams(chips("active", { operator: "equals", value: "false" })),
        ).toEqual(["active,equals,false"]);
    });

    it("emits the uniform three-token form for no-value operators", () => {
        expect(toUserDirectoryFilterParams(chips("email", { operator: "isEmpty" }))).toEqual([
            "email,isEmpty,",
        ]);
        expect(toUserDirectoryFilterParams(chips("email", { operator: "isNotEmpty" }))).toEqual([
            "email,isNotEmpty,",
        ]);
    });

    it("joins a between range with a double dot", () => {
        expect(
            toUserDirectoryFilterParams(
                chips("createdAt", {
                    operator: "between",
                    value: "2026-01-01",
                    valueTo: "2026-03-01",
                }),
            ),
        ).toEqual(["createdAt,between,2026-01-01..2026-03-01"]);
    });

    it("skips a between chip that is missing an endpoint", () => {
        expect(
            toUserDirectoryFilterParams(
                chips("createdAt", { operator: "between", value: "2026-01-01" }),
            ),
        ).toEqual([]);
    });

    it("preserves commas inside a value", () => {
        // The backend splits on the first two commas only, so the value survives intact.
        expect(
            toUserDirectoryFilterParams(chips("name", { operator: "equals", value: "Doe, John" })),
        ).toEqual(["name,equals,Doe, John"]);
    });

    it("emits one param per chip, in order, for two chips on the same column", () => {
        expect(
            toUserDirectoryFilterParams(
                chips(
                    "name",
                    { operator: "contains", value: "a" },
                    { operator: "notEquals", value: "admin" },
                ),
            ),
        ).toEqual(["name,contains,a", "name,notEquals,admin"]);
    });

    it("emits params across columns in filter-state order", () => {
        expect(
            toUserDirectoryFilterParams([
                { id: "rowType", value: [{ operator: "equals", value: "user" }] },
                { id: "email", value: [{ operator: "contains", value: "jo" }] },
            ]),
        ).toEqual(["type,equals,USER", "email,contains,jo"]);
    });

    it("skips a chip on a column the backend cannot filter", () => {
        expect(
            toUserDirectoryFilterParams(
                chips("somethingRemoved", { operator: "contains", value: "x" }),
            ),
        ).toEqual([]);
    });

    it("skips an operator outside the column's whitelist", () => {
        // `contains` is legal on email but not on the entry type; never send it.
        expect(
            toUserDirectoryFilterParams(chips("rowType", { operator: "contains", value: "us" })),
        ).toEqual([]);
    });

    it("skips a value that is empty after trimming", () => {
        expect(
            toUserDirectoryFilterParams(chips("email", { operator: "contains", value: "   " })),
        ).toEqual([]);
    });

    it("trims surrounding whitespace from a value", () => {
        expect(
            toUserDirectoryFilterParams(chips("email", { operator: "contains", value: "  jo  " })),
        ).toEqual(["email,contains,jo"]);
    });

    it("clips an overlong value to the server cap rather than failing the request", () => {
        const long = "x".repeat(MAX_FILTER_VALUE_LENGTH + 50);

        expect(
            toUserDirectoryFilterParams(chips("email", { operator: "contains", value: long })),
        ).toEqual([`email,contains,${"x".repeat(MAX_FILTER_VALUE_LENGTH)}`]);
    });
});

describe("toUserDirectorySortField", () => {
    it("maps grid column ids to the backend attribute the server orders by", () => {
        // The grid names these after its row model. Mapping them here means a legitimate sort
        // never reaches the server as an unknown field; the 400 it now answers with is a backstop.
        expect(toUserDirectorySortField("rowType")).toBe("type");
        expect(toUserDirectorySortField("isAdmin")).toBe("admin");
    });

    it("passes through ids that already match the backend name", () => {
        expect(toUserDirectorySortField("name")).toBe("name");
        expect(toUserDirectorySortField("email")).toBe("email");
        expect(toUserDirectorySortField("firstName")).toBe("firstName");
        expect(toUserDirectorySortField("lastName")).toBe("lastName");
        expect(toUserDirectorySortField("active")).toBe("active");
        expect(toUserDirectorySortField("createdAt")).toBe("createdAt");
    });

    it("falls back to the id for a column outside the map", () => {
        expect(toUserDirectorySortField("somethingElse")).toBe("somethingElse");
    });
});
