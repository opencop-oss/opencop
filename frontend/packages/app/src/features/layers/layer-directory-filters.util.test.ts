// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ActiveColumnFilterValue } from "@opencop/ui";
import type { ColumnFiltersState } from "@tanstack/react-table";
import { describe, expect, it } from "vitest";
import {
    toLayerDirectoryFilterParams,
    toLayerDirectorySortField,
} from "./layer-directory-filters.util";

/** Builds the `{ id, value }` shape `ColumnFilterBuilder` writes into `ColumnFiltersState`. */
function chips(id: string, ...values: ActiveColumnFilterValue[]): ColumnFiltersState {
    return [{ id, value: values }];
}

describe("toLayerDirectoryFilterParams", () => {
    it("returns no params for an empty filter state", () => {
        expect(toLayerDirectoryFilterParams([])).toEqual([]);
    });

    it("serializes a name chip as field,operator,value", () => {
        expect(
            toLayerDirectoryFilterParams(chips("name", { operator: "contains", value: "route" })),
        ).toEqual(["name,contains,route"]);
    });

    it("maps the rowType column onto the API's type field", () => {
        expect(
            toLayerDirectoryFilterParams(chips("rowType", { operator: "equals", value: "LAYER" })),
        ).toEqual(["type,equals,LAYER"]);
    });

    it("passes the LAYER_GROUP token through verbatim", () => {
        // Guards against copying the users map's `toWire: toUpperCase`, which yields "LAYER-GROUP".
        expect(
            toLayerDirectoryFilterParams(
                chips("rowType", { operator: "equals", value: "LAYER_GROUP" }),
            ),
        ).toEqual(["type,equals,LAYER_GROUP"]);
    });

    it("serializes accessScope tokens unchanged", () => {
        expect(
            toLayerDirectoryFilterParams(
                chips("accessScope", { operator: "notEquals", value: "GLOBAL" }),
            ),
        ).toEqual(["accessScope,notEquals,GLOBAL"]);
    });

    it("joins a between range with the .. separator", () => {
        expect(
            toLayerDirectoryFilterParams(
                chips("createdAt", {
                    operator: "between",
                    value: "2026-01-01",
                    valueTo: "2026-03-01",
                }),
            ),
        ).toEqual(["createdAt,between,2026-01-01..2026-03-01"]);
    });

    it("drops a between chip missing an endpoint rather than sending a half-range", () => {
        expect(
            toLayerDirectoryFilterParams(
                chips("createdAt", { operator: "between", value: "2026-01-01" }),
            ),
        ).toEqual([]);
    });

    it("drops an operator the column does not declare", () => {
        // `isEmpty` is deliberately absent from the layer name column — both underlying name
        // columns are NOT NULL, and the backend rejects it.
        expect(toLayerDirectoryFilterParams(chips("name", { operator: "isEmpty" }))).toEqual([]);
    });

    it("drops chips for a column with no field mapping", () => {
        expect(
            toLayerDirectoryFilterParams(chips("archived", { operator: "equals", value: "true" })),
        ).toEqual([]);
    });

    it("preserves commas inside a value", () => {
        expect(
            toLayerDirectoryFilterParams(
                chips("name", { operator: "equals", value: "alpha, bravo" }),
            ),
        ).toEqual(["name,equals,alpha, bravo"]);
    });

    it("keeps multiple chips in order so the query key stays stable", () => {
        expect(
            toLayerDirectoryFilterParams([
                { id: "rowType", value: [{ operator: "equals", value: "LAYER" }] },
                { id: "name", value: [{ operator: "contains", value: "a" }] },
            ]),
        ).toEqual(["type,equals,LAYER", "name,contains,a"]);
    });
});

describe("toLayerDirectorySortField", () => {
    it("maps rowType onto the API's type field", () => {
        // Without this the grid's Type sort reaches the server as an unknown field (400).
        expect(toLayerDirectorySortField("rowType")).toBe("type");
    });

    it("passes through ids that already match the API", () => {
        expect(toLayerDirectorySortField("name")).toBe("name");
        expect(toLayerDirectorySortField("accessScope")).toBe("accessScope");
        expect(toLayerDirectorySortField("createdAt")).toBe("createdAt");
    });
});
