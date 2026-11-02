// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import { getElementKey, type Element } from "@opencop/elements";
import { searchElements } from "./event-search.util";

function makeElement(overrides: {
    id: string;
    objectId: string;
    name: string;
    modifiers?: Record<string, string>;
}): Element {
    return {
        key: getElementKey({ source: "source", objectId: overrides.objectId }),
        id: overrides.id,
        objectId: overrides.objectId,
        source: "source",
        type: "point",
        getDisplayName: () => overrides.name,
        getCode: () => undefined,
        getModifiers: () => overrides.modifiers ?? {},
        getAttributes: () => ({}),
    } as unknown as Element;
}

describe("searchElements", () => {
    const alpha = makeElement({
        id: "version-alpha",
        objectId: "object-alpha",
        name: "Alpha",
        modifiers: { affiliation: "friendly" },
    });
    const bravo = makeElement({
        id: "version-bravo",
        objectId: "object-bravo",
        name: "Bravo",
    });

    it.each(["object-alpha", "VERSION-ALPHA", "alp", "friendly"])(
        "matches %s across identity, name, and modifiers",
        (query) => {
            expect(searchElements([alpha, bravo], query).map((r) => r.element)).toEqual([alpha]);
        },
    );

    it("ranks an exact UUID match before other partial matches", () => {
        const partial = makeElement({
            id: "version-partial",
            objectId: "prefix-object-alpha-suffix",
            name: "Aardvark",
        });

        expect(searchElements([partial, alpha], "object-alpha").map((r) => r.element)).toEqual([
            alpha,
            partial,
        ]);
    });

    it("returns no results for a blank query", () => {
        expect(searchElements([alpha], "   ")).toEqual([]);
    });

    it("does not search attributes that are unavailable on live WebSocket elements", () => {
        const elementWithAttributes = {
            ...alpha,
            getAttributes: () => ({ callsign: "Raven" }),
        } as unknown as Element;

        expect(searchElements([elementWithAttributes], "Raven")).toEqual([]);
    });

    it("populates matchedFields for non-obvious matches", () => {
        const results = searchElements([alpha, bravo], "friendly");
        expect(results).toHaveLength(1);
        expect(results[0].matchedFields).toContainEqual({
            label: "affiliation",
            value: "friendly",
        });
    });

    it("collects matched fields once per element", () => {
        const getModifiers = vi.fn(() => ({ callsign: "Raven" }));
        const modifierMatch = { ...bravo, getModifiers } as unknown as Element;

        searchElements([modifierMatch], "Raven");

        expect(getModifiers).toHaveBeenCalledTimes(1);
    });

    it("identifies the object ID as the reason for a match", () => {
        const results = searchElements([alpha, bravo], "object-alpha");

        expect(results[0].matchedFields).toContainEqual({
            label: "objectId",
            value: "object-alpha",
        });
    });

    it("identifies the per-version UUID as the reason for a match", () => {
        const results = searchElements([alpha, bravo], "version-alpha");

        expect(results[0].matchedFields).toContainEqual({
            label: "id",
            value: "version-alpha",
        });
    });
});
