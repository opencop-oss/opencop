// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import {
    LEGACY_C_AFFILIATIONS,
    LEGACY_C_BATTLE_DIMENSIONS,
    SIDC_FIELD_RANGES,
    STANDARD_IDENTITIES,
    SYMBOL_SETS,
    standardIdentitiesToLegacyC,
    symbolSetsToLegacyC,
} from "./legacy-c";
import { GENERATED_SIDC_SYMBOL_SETS } from "./sidc.generated";
import { SIDC } from "./sidc";

const slice = (sidc: string, { offset, length }: { offset: number; length: number }) =>
    sidc.slice(offset, offset + length);

describe("SIDC_FIELD_RANGES", () => {
    it("addresses the same fields the SIDC parser does", () => {
        const sidc = new SIDC({ identity: "6", symbolSet: "15" }).toString();

        expect(slice(sidc, SIDC_FIELD_RANGES.d.standardIdentity)).toBe("6");
        expect(slice(sidc, SIDC_FIELD_RANGES.d.symbolSet)).toBe("15");
    });

    it("addresses the affiliation and battle dimension of a 2525C code", () => {
        // SFGPUCI-----USG — Friend, Ground.
        const legacy = "SFGPUCI-----USG";

        expect(slice(legacy, SIDC_FIELD_RANGES.c.affiliation)).toBe("F");
        expect(slice(legacy, SIDC_FIELD_RANGES.c.battleDimension)).toBe("G");
    });
});

describe("STANDARD_IDENTITIES", () => {
    it("covers all seven 2525D identities in code order", () => {
        expect(STANDARD_IDENTITIES.map((identity) => identity.code)).toEqual([
            "0",
            "1",
            "2",
            "3",
            "4",
            "5",
            "6",
        ]);
    });

    it("gives each identity its 2525C letter(s) — two only where D never split them by context", () => {
        expect(STANDARD_IDENTITIES.find((i) => i.code === "0")?.legacyC).toEqual(["P"]);
        expect(STANDARD_IDENTITIES.find((i) => i.code === "1")?.legacyC).toEqual(["U"]);
        expect(STANDARD_IDENTITIES.find((i) => i.code === "2")?.legacyC).toEqual(["A"]);
        expect(STANDARD_IDENTITIES.find((i) => i.code === "3")?.legacyC).toEqual(["F"]);
        expect(STANDARD_IDENTITIES.find((i) => i.code === "4")?.legacyC).toEqual(["N"]);
        expect(STANDARD_IDENTITIES.find((i) => i.code === "5")?.legacyC).toEqual(["S", "J"]);
        expect(STANDARD_IDENTITIES.find((i) => i.code === "6")?.legacyC).toEqual(["H", "K"]);
    });
});

describe("SYMBOL_SETS", () => {
    it("covers every catalog symbol set except the empty one", () => {
        const catalogCodes = Object.keys(GENERATED_SIDC_SYMBOL_SETS)
            .filter((code) => code !== "00")
            .sort();

        expect(SYMBOL_SETS.map((set) => set.code)).toEqual(catalogCodes);
        expect(SYMBOL_SETS).toHaveLength(22);
    });

    it("takes its labels from the generated catalog rather than restating them", () => {
        for (const set of SYMBOL_SETS) {
            const entry = GENERATED_SIDC_SYMBOL_SETS[set.code];
            expect(set.label).toBe(entry.label);
            expect(set.localeKey).toBe(entry.localeKey);
        }
    });
});

describe("standardIdentitiesToLegacyC", () => {
    it("expands an identity 2525D never split by context to its single 2525C letter", () => {
        expect(standardIdentitiesToLegacyC(["3"])).toEqual(["F"]);
    });

    it("expands Hostile/Faker to both of its 2525C letters", () => {
        expect(standardIdentitiesToLegacyC(["6"])).toEqual(["H", "K"]);
    });

    it("de-duplicates across codes and drops unmapped ones", () => {
        expect(standardIdentitiesToLegacyC(["3", "3", "9"])).toEqual(["F"]);
        expect(standardIdentitiesToLegacyC(["nonsense"])).toEqual([]);
    });
});

describe("symbolSetsToLegacyC", () => {
    it("collapses distinct land symbol sets onto one battle dimension", () => {
        // Documents the lossiness: a legacy-C match built from these three is a
        // superset of the D-family match, not a translation of it.
        expect(symbolSetsToLegacyC(["10", "15", "20"])).toEqual(["G"]);
    });

    it("separates the dimensions that do differ", () => {
        expect(symbolSetsToLegacyC(["01"])).toEqual(["A"]);
        expect(symbolSetsToLegacyC(["30"])).toEqual(["S"]);
        expect(symbolSetsToLegacyC(["35"])).toEqual(["U"]);
        expect(symbolSetsToLegacyC(["05"])).toEqual(["P"]);
    });
});

describe("LEGACY_C_AFFILIATIONS", () => {
    it("covers all nine Warfighting affiliation letters that map onto a D identity on their own", () => {
        expect(LEGACY_C_AFFILIATIONS).toHaveLength(9);
        expect(new Set(LEGACY_C_AFFILIATIONS.map((a) => a.letter)).size).toBe(9);
    });

    it("omits the Exercise-context letters", () => {
        const letters = new Set(LEGACY_C_AFFILIATIONS.map((a) => a.letter));
        for (const exerciseLetter of ["G", "W", "M", "D", "L"]) {
            expect(letters.has(exerciseLetter)).toBe(false);
        }
    });

    it("is the source STANDARD_IDENTITIES derives its legacy letters from", () => {
        for (const identity of STANDARD_IDENTITIES) {
            const derived = LEGACY_C_AFFILIATIONS.filter((a) => a.identity === identity.code).map(
                (a) => a.letter,
            );
            expect(identity.legacyC).toEqual(derived);
        }
    });

    it("maps every letter to a real D identity", () => {
        const codes = new Set(STANDARD_IDENTITIES.map((i) => i.code));
        for (const affiliation of LEGACY_C_AFFILIATIONS) {
            expect(codes.has(affiliation.identity)).toBe(true);
        }
    });
});

describe("LEGACY_C_BATTLE_DIMENSIONS", () => {
    it("includes every dimension the symbol-set collapse can produce", () => {
        const produced = new Set(symbolSetsToLegacyC(SYMBOL_SETS.map((s) => s.code)));
        const offered = new Set(LEGACY_C_BATTLE_DIMENSIONS.map((d) => d.letter));
        for (const letter of produced) expect(offered.has(letter)).toBe(true);
    });
});

describe("legacy-C field offsets against real 2525C codes", () => {
    // Codes observed on the wire from the CoT feeder's legacy cluster.
    const SAMPLES = [
        { sidc: "SFAPMFF--------", affiliation: "F", dimension: "A" },
        { sidc: "SAGPUCI--------", affiliation: "A", dimension: "G" },
        { sidc: "SNGPUCI--------", affiliation: "N", dimension: "G" },
        { sidc: "SFUPSCG--------", affiliation: "F", dimension: "U" },
    ];

    it("reads the affiliation and dimension the codes actually carry", () => {
        for (const { sidc, affiliation, dimension } of SAMPLES) {
            expect(slice(sidc, SIDC_FIELD_RANGES.c.affiliation)).toBe(affiliation);
            expect(slice(sidc, SIDC_FIELD_RANGES.c.battleDimension)).toBe(dimension);
        }
    });

    it("offers every value those codes carry as a pickable option", () => {
        const affiliations = new Set(LEGACY_C_AFFILIATIONS.map((a) => a.letter));
        const dimensions = new Set(LEGACY_C_BATTLE_DIMENSIONS.map((d) => d.letter));
        for (const { affiliation, dimension } of SAMPLES) {
            expect(affiliations.has(affiliation)).toBe(true);
            expect(dimensions.has(dimension)).toBe(true);
        }
    });

    it("confirms the D-family offsets cannot match these codes", () => {
        // The reason both pairs of fields exist: a D predicate reads the 2525C
        // status character and function ID, never a valid identity or symbol set.
        const identityCodes = new Set(STANDARD_IDENTITIES.map((i) => i.code));
        const symbolSetCodes = new Set(SYMBOL_SETS.map((s) => s.code));
        for (const { sidc } of SAMPLES) {
            expect(identityCodes.has(slice(sidc, SIDC_FIELD_RANGES.d.standardIdentity))).toBe(
                false,
            );
            expect(symbolSetCodes.has(slice(sidc, SIDC_FIELD_RANGES.d.symbolSet))).toBe(false);
        }
    });
});
