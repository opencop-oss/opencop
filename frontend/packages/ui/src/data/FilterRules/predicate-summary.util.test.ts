// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { i18n } from "~/i18n/config";
import { summarizePredicate } from "./predicate-summary.util";
import type { FilterField } from "./filter-field.model";

const t = i18n.t.bind(i18n);

const field = (id: string, labelKey: string): FilterField => ({
    id,
    labelKey,
    path: `properties.opencop.${id}`,
    order: 0,
});
const DISPLAY_NAME = field("displayName", "filter.fields.displayName");
const KIND = field("kind", "filter.fields.kind");
const CODE: FilterField = {
    id: "sidc",
    labelText: "SIDC",
    path: "properties.opencop.code",
    order: 0,
};
const byId: Record<string, FilterField> = { displayName: DISPLAY_NAME, kind: KIND, sidc: CODE };

describe("summarizePredicate", () => {
    it("describes a negated EQUALS predicate grammatically", () => {
        const summary = summarizePredicate(
            {
                id: "1",
                field: "displayName",
                operator: "EQUALS",
                value: "Test Name",
                negate: true,
            },
            byId["displayName"],
            t,
        );

        expect(summary).toEqual({
            key: "filter.summary.equalsNegated",
            values: { field: "Display Name", value: "Test Name" },
        });
    });

    it("describes an affirmative EQUALS predicate", () => {
        const summary = summarizePredicate(
            { id: "1", field: "displayName", operator: "EQUALS", value: "Test Name" },
            byId["displayName"],
            t,
        );

        expect(summary).toEqual({
            key: "filter.summary.equals",
            values: { field: "Display Name", value: "Test Name" },
        });
    });

    it("describes EXISTS and its negation", () => {
        expect(
            summarizePredicate(
                { id: "1", field: "sidc", operator: "EXISTS", value: undefined },
                byId["sidc"],
                t,
            ),
        ).toEqual({ key: "filter.summary.exists", values: { field: "SIDC" } });

        expect(
            summarizePredicate(
                { id: "1", field: "sidc", operator: "EXISTS", value: undefined, negate: true },
                byId["sidc"],
                t,
            ),
        ).toEqual({ key: "filter.summary.existsNegated", values: { field: "SIDC" } });
    });

    it("joins values for IN", () => {
        const summary = summarizePredicate(
            { id: "1", field: "kind", operator: "IN", value: ["Aircraft", "Ship"] },
            byId["kind"],
            t,
        );

        expect(summary).toEqual({
            key: "filter.summary.in",
            values: { field: "Kind", values: "Aircraft, Ship" },
        });
    });

    it("keeps every value needed by SUBSTRING_IN", () => {
        const summary = summarizePredicate(
            {
                id: "1",
                field: "sidc",
                operator: "SUBSTRING_IN",
                value: { value: ["AIR", "SEA"], start: "3", end: "7" },
            },
            byId["sidc"],
            t,
        );

        expect(summary).toEqual({
            key: "filter.summary.substringIn",
            values: { field: "SIDC", values: "AIR, SEA", start: "3", end: "7" },
        });
    });

    it("reads a fallback row by its path, marked unavailable", () => {
        expect(
            summarizePredicate(
                {
                    id: "1",
                    raw: {
                        path: "properties.opencop.element.symbol",
                        operation: "SUBSTRING_IN",
                        values: ["6"],
                        offset: 3,
                        length: 1,
                    },
                },
                undefined,
                t,
            ),
        ).toEqual({
            key: "filter.summary.substringIn",
            values: {
                field: "properties.opencop.element.symbol",
                values: "6",
                start: "3",
                end: "4",
            },
            unavailable: true,
        });
    });

    it("lets a field name its value", () => {
        const named: FilterField = { ...CODE, formatValue: () => "Hostile" };

        expect(
            summarizePredicate(
                {
                    id: "1",
                    field: "sidc",
                    operator: "SUBSTRING_IN",
                    value: { value: ["6"], start: "3", end: "4" },
                },
                named,
                t,
            ),
        ).toEqual({ key: "filter.summary.equals", values: { field: "SIDC", value: "Hostile" } });
    });

    it("returns undefined for an incomplete predicate so callers keep it expanded", () => {
        expect(
            summarizePredicate(
                { id: "1", field: "displayName", operator: undefined, value: undefined },
                byId["displayName"],
                t,
            ),
        ).toBeUndefined();

        expect(
            summarizePredicate(
                { id: "1", field: "displayName", operator: "EQUALS", value: "" },
                byId["displayName"],
                t,
            ),
        ).toBeUndefined();

        expect(
            summarizePredicate(
                { id: "1", field: undefined, operator: "EXISTS", value: undefined },
                undefined,
                t,
            ),
        ).toBeUndefined();
    });
});
