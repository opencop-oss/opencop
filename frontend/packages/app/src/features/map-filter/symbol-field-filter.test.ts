// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Element, IconStyle, PointGeometry } from "@opencop/elements";
import { parseFilter, type FilterCombinator, type PredicateDraft } from "@opencop/ui";
import { getRegisteredFilterFields } from "~/features/contributions/filter-field-registry";
import { withPlugins } from "~/test/plugins";
import { compile } from "./compiled-filter.model";
import type { MapFilter } from "./map-filter.store";

/**
 * Drives the symbology plugin's SIDC fields end to end — a picked value through
 * `parseFilter` into the compiled expression the map evaluates — against SIDCs captured
 * from a live CoT feed.
 */
withPlugins(["opencop.symbology"]);

type Field = "standardIdentity" | "symbolSet";
const D_HOSTILE_AIR = "10060100001100000000";
const D_FRIEND_LAND = "10031000001100000000";
const C_HOSTILE_AIR = "SHAPMFF--------";
const C_NEUTRAL_GROUND = "SNGPUCI--------";

function elementWithCode(sidc: string): Element {
    const element = new Element({
        type: "symbol",
        displayName: sidc,
        geometry: new PointGeometry([0, 0]),
        style: new IconStyle({ size: 1, angle: 0 }),
    });
    element.setCode(sidc);
    return element;
}

function buildFilter(
    conditions: [Field, string][],
    combinator: FilterCombinator,
    negate = false,
): MapFilter {
    const fields = getRegisteredFilterFields();
    const predicates = conditions.map(([name, value], index): PredicateDraft => {
        const field = fields.find((candidate) => candidate.id === `opencop.symbology:${name}`);
        const draft = field?.newDraft?.(`p${index}`);
        if (draft?.operator !== "SUBSTRING_IN")
            throw new Error(`${name} is not a SIDC slice field`);
        return { ...draft, negate, value: { ...draft.value, value: [value] } };
    });

    const parsed = parseFilter({ combinator, predicates }, fields);
    if (!parsed.ok) throw new Error(`filter did not parse: ${JSON.stringify(parsed.errors)}`);
    return { name: "test", ...parsed.result };
}

const matches = (
    conditions: [Field, string][],
    sidc: string,
    combinator: FilterCombinator = "ANY",
) => compile(buildFilter(conditions, combinator)).execute({ element: elementWithCode(sidc) });

describe("symbol-backed field filters", () => {
    it("matches both encodings from a single condition under Any", () => {
        const hostile: [Field, string][] = [["standardIdentity", "6"]];

        expect(matches(hostile, D_HOSTILE_AIR)).toBe(true);
        expect(matches(hostile, C_HOSTILE_AIR)).toBe(true);
        expect(matches(hostile, D_FRIEND_LAND)).toBe(false);
        expect(matches(hostile, C_NEUTRAL_GROUND)).toBe(false);
    });

    it("matches both encodings for a symbol set too", () => {
        const air: [Field, string][] = [["symbolSet", "01"]];

        expect(matches(air, D_HOSTILE_AIR)).toBe(true);
        expect(matches(air, C_HOSTILE_AIR)).toBe(true);
        expect(matches(air, D_FRIEND_LAND)).toBe(false);
        expect(matches(air, C_NEUTRAL_GROUND)).toBe(false);
    });

    it("matches a superset for symbol sets, because 2525C has no symbol-set field", () => {
        // Land Units expands to dimension G, so a 2525C ground *equipment* track matches too.
        expect(matches([["symbolSet", "10"]], C_NEUTRAL_GROUND)).toBe(true);
        expect(matches([["symbolSet", "10"]], D_FRIEND_LAND)).toBe(true);
        expect(matches([["symbolSet", "10"]], D_HOSTILE_AIR)).toBe(false);
    });

    it("matches nothing under All, which is why the builder warns", () => {
        // No element is both encodings, so the two halves can't both hold.
        expect(matches([["standardIdentity", "6"]], D_HOSTILE_AIR, "ALL")).toBe(false);
        expect(matches([["standardIdentity", "6"]], C_HOSTILE_AIR, "ALL")).toBe(false);
    });

    it("would be wrong if a symbol row could be negated, which is why the UI withholds it", () => {
        // A guard, not an endorsement: `NOT D OR NOT C` is true for nearly everything.
        // This is what ships if Negate is ever re-enabled without nesting.
        const compiled = compile(buildFilter([["standardIdentity", "6"]], "ANY", true));

        expect(compiled.execute({ element: elementWithCode(D_HOSTILE_AIR) })).toBe(true);
    });
});
