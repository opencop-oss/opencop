// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Element, IconStyle, PointGeometry, PointStyle } from "@opencop/elements";
import {
    compile,
    EqualsExpression,
    ExistsExpression,
    InExpression,
    SubstringInExpression,
} from "./compiled-filter.model";
import type { MapFilter } from "./map-filter.store";

describe("compiled-filters", () => {
    const element = new Element({
        type: "point",
        displayName: "null",
        geometry: new PointGeometry([0, 0]),
        style: new PointStyle({ color: "", radius: 0 }),
    });

    const baseFilter = {
        name: "null",
        combine: "ALL",
    } as const;

    it("compiles equals correctly", () => {
        const filter: MapFilter = {
            ...baseFilter,
            predicates: [
                {
                    operation: "EQUALS",
                    path: "properties.opencop.displayName",
                    values: ["value"],
                },
            ],
        };

        const compiled = compile(filter);
        expect(compiled.predicates[0]).toBeInstanceOf(EqualsExpression);
        const equals = compiled.predicates[0] as EqualsExpression;
        expect(equals.value).toBe("value");
        expect(compiled.execute({ element })).toBe(false);
    });
    it("compiles exists correctly", () => {
        const filter: MapFilter = {
            ...baseFilter,
            predicates: [
                {
                    operation: "EXISTS",
                    path: "properties.opencop.displayName",
                },
            ],
        };

        const compiled = compile(filter);
        expect(compiled.predicates[0]).toBeInstanceOf(ExistsExpression);
        expect(compiled.execute({ element })).toBe(true);
    });
    it("compiles in correctly", () => {
        const filter: MapFilter = {
            ...baseFilter,
            predicates: [
                {
                    operation: "IN",
                    path: "properties.opencop.displayName",
                    values: ["null", "something", "else"],
                },
            ],
        };

        const compiled = compile(filter);
        expect(compiled.predicates[0]).toBeInstanceOf(InExpression);
        expect(compiled.execute({ element })).toBe(true);
    });
    it("compiles substring in correctly", () => {
        const filter: MapFilter = {
            ...baseFilter,
            predicates: [
                {
                    operation: "SUBSTRING_IN",
                    path: "properties.opencop.displayName",
                    values: ["null", "something", "else"],
                    offset: 0,
                    length: 4,
                },
            ],
        };

        const compiled = compile(filter);
        expect(compiled.predicates[0]).toBeInstanceOf(SubstringInExpression);
        const substringIn = compiled.predicates[0] as SubstringInExpression;
        expect(substringIn.start).toBe(0);
        expect(substringIn.end).toBe(4);
        expect(substringIn.values).toEqual(["null", "something", "else"]);
        expect(compiled.execute({ element })).toBe(true);
    });
    it("routes a missing field through substring in's negation, matching backend behavior", () => {
        // The symbol path is the only one that can be absent: `getCode()` returns
        // `string | undefined`, where `getDisplayName()` falls back to "New <type>".
        // Sole predicate on purpose — a sibling under ALL would short-circuit and mask this.
        const substringIn = (negate: boolean): MapFilter => ({
            ...baseFilter,
            predicates: [
                {
                    operation: "SUBSTRING_IN",
                    path: "properties.opencop.element.symbol",
                    values: ["10"],
                    offset: 0,
                    length: 2,
                    negate,
                },
            ],
        });

        // `element` is a point with no code, so the accessor returns undefined.
        expect(compile(substringIn(false)).execute({ element })).toBe(false);
        // The absent field is not a hard `false` — negation flips it, as it does for the
        // other operations. Restoring that `return false` fails this assertion alone.
        expect(compile(substringIn(true)).execute({ element })).toBe(true);
    });
    it("compiles negated predicate correctly", () => {
        const filter: MapFilter = {
            ...baseFilter,
            predicates: [
                {
                    operation: "EXISTS",
                    path: "properties.opencop.displayName",
                    negate: true,
                },
            ],
        };

        const compiled = compile(filter);
        expect(compiled.predicates[0]).toBeInstanceOf(ExistsExpression);
        expect(compiled.execute({ element })).toBe(false);
    });
    it("compiles caseInsensitive predicate correctly", () => {
        const filter: MapFilter = {
            ...baseFilter,
            predicates: [
                {
                    operation: "EQUALS",
                    path: "properties.opencop.displayName",
                    values: ["NULL"],
                    caseInsensitive: true,
                },
            ],
        };

        const compiled = compile(filter);
        expect(compiled.predicates[0]).toBeInstanceOf(EqualsExpression);
        expect(compiled.execute({ element })).toBe(true);
    });
    it("compiles a complex filter", () => {
        const filter: MapFilter = {
            ...baseFilter,
            predicates: [
                {
                    operation: "SUBSTRING_IN",
                    path: "properties.opencop.displayName",
                    values: ["null"],
                    offset: 0,
                    length: 4,
                },
                {
                    operation: "IN",
                    path: "properties.opencop.displayName",
                    values: ["null"],
                },
                {
                    operation: "EXISTS",
                    path: "properties.opencop.displayName",
                },
                {
                    operation: "EQUALS",
                    path: "properties.opencop.displayName",
                    values: ["null"],
                },
                {
                    operation: "EXISTS",
                    path: "properties.opencop.element.symbol",
                },
                {
                    operation: "SUBSTRING_IN",
                    path: "properties.opencop.element.symbol",
                    values: ["10", "00", "01"],
                    offset: 0,
                    length: 2,
                },
                {
                    operation: "IN",
                    path: "properties.opencop.element.symbol",
                    values: ["10031000001211000000"],
                },
            ],
        };

        const compiled = compile(filter);
        const predicates = compiled.predicates;

        expect(predicates[0]).toBeInstanceOf(SubstringInExpression);
        expect(predicates[1]).toBeInstanceOf(InExpression);
        expect(predicates[2]).toBeInstanceOf(ExistsExpression);
        expect(predicates[3]).toBeInstanceOf(EqualsExpression);
        expect(predicates[4]).toBeInstanceOf(ExistsExpression);
        expect(predicates[5]).toBeInstanceOf(SubstringInExpression);
        expect(predicates[6]).toBeInstanceOf(InExpression);

        const first = new Element({
            type: "symbol",
            displayName: "null",
            code: "10031000001211000000",
            geometry: new PointGeometry([0, 0]),
            style: new IconStyle({}),
        });

        expect(compiled.execute({ element: first })).toBe(true);

        const second = new Element({
            type: "symbol",
            displayName: "null",
            code: "11031000001211000000",
            geometry: new PointGeometry([0, 0]),
            style: new IconStyle({}),
        });

        expect(compiled.execute({ element: second })).toBe(false);

        const third = new Element({
            type: "symbol",
            code: "00031000001211000000",
            geometry: new PointGeometry([0, 0]),
            style: new IconStyle({}),
        });

        expect(compiled.execute({ element: third })).toBe(false);
    });
    it("matches when any predicate passes under ANY", () => {
        const filter: MapFilter = {
            ...baseFilter,
            combine: "ANY",
            predicates: [
                { operation: "EQUALS", path: "properties.opencop.displayName", values: ["nope"] },
                { operation: "EXISTS", path: "properties.opencop.displayName" },
            ],
        };
        expect(compile(filter).execute({ element })).toBe(true);
    });

    it("treats an empty ANY filter as matching everything", () => {
        expect(
            compile({ ...baseFilter, combine: "ANY", predicates: [] }).execute({ element }),
        ).toBe(true);
    });
});
