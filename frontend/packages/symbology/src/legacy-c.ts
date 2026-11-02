// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { flatMap, uniq } from "lodash";
import { GENERATED_SIDC_SYMBOL_SETS } from "./sidc.generated";
import type { SIDCSymbolSet } from "./sidc";

/**
 * MIL-STD-2525D ⇄ 2525C mappings for the only two fields with a field-independent
 * legacy equivalent: Standard Identity and Symbol Set.
 *
 * The D offsets also cover 2525E and APP-6D — same wire structure, different version digits.
 */

/** A fixed-width slice of a SIDC string: a start `offset` and a character `length` —
 *  the same shape as a `SubstringInPredicate`'s wire fields, not `.slice()` bounds. */
export interface SidcFieldRange {
    offset: number;
    length: number;
}

/** Grouped by standard, not by field — D's Standard Identity is C's Affiliation,
 *  and Symbol Set only maps onto C's coarser Battle Dimension. */
export const SIDC_FIELD_RANGES = {
    d: {
        /** D-family position 4 — the affiliation digit. */
        standardIdentity: { offset: 3, length: 1 },
        /** D-family positions 5-6. */
        symbolSet: { offset: 4, length: 2 },
    },
    c: {
        /** 2525C position 2 — the combined context+affiliation letter. */
        affiliation: { offset: 1, length: 1 },
        /** 2525C position 3 — battle dimension. */
        battleDimension: { offset: 2, length: 1 },
    },
} as const satisfies Record<"d" | "c", Record<string, SidcFieldRange>>;

/** Copied from the plugin's Identity picker so both dropdowns agree. Dashed frames, not
 *  colour, separate Assumed Friend from Friend and Suspect from Hostile. */
const UNKNOWN = "#ffff00ff";
const PENDING = UNKNOWN;
const FRIEND = "#00b8fbff";
const NEUTRAL = "#00ff04ff";
const HOSTILE = "#ff3d3dff";

/** A shape name, not markup — this package stays React-free, so the UI draws the SVG. */
export type StandardIdentityFrame =
    | "circle"
    | "circle-dashed"
    | "rect"
    | "rect-dashed"
    | "square"
    | "diamond"
    | "diamond-dashed";

export interface StandardIdentityOption {
    /** D-family affiliation digit. */
    code: string;
    label: string;
    localeKey: string;
    /** Affiliation colour, matching the symbology plugin's picker. */
    color: string;
    frame: StandardIdentityFrame;
    /** This identity's 2525C letter(s) — two only for Suspect/Hostile, where 2525D never
     *  split the Joker/Faker variant out by context. See {@link LEGACY_C_AFFILIATIONS}. */
    legacyC: readonly string[];
}

export interface LegacyCAffiliationOption {
    /** 2525C affiliation letter, at {@link SIDC_FIELD_RANGES}.standardIdentity.legacyC. */
    letter: string;
    label: string;
    localeKey: string;
    /** The D-family identity digit this letter denotes. */
    identity: string;
}

/**
 * The nine Warfighting affiliation letters that map onto a D identity on their own.
 *
 * 2525C also has five "Exercise <identity>" letters (Pending, Unknown, Assumed Friend,
 * Friend, Neutral) that fold a separate Context field into the affiliation character.
 * 2525D keeps Context as its own field, so one of these letters isn't really "the same
 * identity" — it's that identity *and* Context=Exercise, which we can't represent without
 * a Context predicate of our own (and combining two predicates into one legacy match is
 * more machinery than it's worth for now). Left out until we add one.
 *
 * Suspect/Joker and Hostile/Faker are different: 2525D never split them out by context —
 * `STANDARD_IDENTITIES` already names both codes 5 and 6 with the combined label — so both
 * letters belong to the same identity with nothing missing.
 *
 * Source of truth for the D⇄C identity link — {@link STANDARD_IDENTITIES} derives from it.
 */
export const LEGACY_C_AFFILIATIONS: readonly LegacyCAffiliationOption[] = [
    { letter: "P", label: "Pending", localeKey: "pending", identity: "0" },
    { letter: "U", label: "Unknown", localeKey: "unknown", identity: "1" },
    { letter: "A", label: "Assumed Friend", localeKey: "assumedFriend", identity: "2" },
    { letter: "F", label: "Friend", localeKey: "friend", identity: "3" },
    { letter: "N", label: "Neutral", localeKey: "neutral", identity: "4" },
    { letter: "S", label: "Suspect", localeKey: "suspect", identity: "5" },
    { letter: "J", label: "Joker", localeKey: "joker", identity: "5" },
    { letter: "H", label: "Hostile", localeKey: "hostile", identity: "6" },
    { letter: "K", label: "Faker", localeKey: "faker", identity: "6" },
];

export interface LegacyCBattleDimensionOption {
    /** 2525C battle-dimension letter, at {@link SIDC_FIELD_RANGES}.symbolSet.legacyC. */
    letter: string;
    label: string;
    localeKey: string;
}

/** The nearest legacy equivalent of a symbol set, and far coarser — every land set becomes `G`.
 *  Not public API; kept as the reference list the conversion is checked against. */
export const LEGACY_C_BATTLE_DIMENSIONS: readonly LegacyCBattleDimensionOption[] = [
    { letter: "P", label: "Space", localeKey: "space" },
    { letter: "A", label: "Air", localeKey: "air" },
    { letter: "G", label: "Ground", localeKey: "ground" },
    { letter: "S", label: "Sea Surface", localeKey: "seaSurface" },
    { letter: "U", label: "Subsurface", localeKey: "subsurface" },
    { letter: "F", label: "SOF", localeKey: "sof" },
    { letter: "X", label: "Other", localeKey: "other" },
    { letter: "Z", label: "Unknown", localeKey: "unknownDimension" },
];

/** 2525C letter → D identity digit — the direction that's actually one-to-one.
 *  {@link STANDARD_IDENTITIES} groups this backward to fill each option's legacyC list. */
const AFFILIATION_TO_STANDARD_IDENTITY: Readonly<Record<string, string>> = Object.fromEntries(
    LEGACY_C_AFFILIATIONS.map(({ letter, identity }) => [letter, identity]),
);

/** The seven 2525D standard identities, in code order. */
export const STANDARD_IDENTITIES: readonly StandardIdentityOption[] = (
    [
        // The plugin's picker omits Pending, so this colour/frame isn't inherited —
        // 2525D draws it as the dashed counterpart of Unknown.
        {
            code: "0",
            label: "Pending",
            localeKey: "pending",
            color: PENDING,
            frame: "circle-dashed",
        },
        { code: "1", label: "Unknown", localeKey: "unknown", color: UNKNOWN, frame: "circle" },
        {
            code: "2",
            label: "Assumed Friend",
            localeKey: "assumedFriend",
            color: FRIEND,
            frame: "rect-dashed",
        },
        { code: "3", label: "Friend", localeKey: "friend", color: FRIEND, frame: "rect" },
        { code: "4", label: "Neutral", localeKey: "neutral", color: NEUTRAL, frame: "square" },
        {
            code: "5",
            label: "Suspect/Joker",
            localeKey: "suspect",
            color: HOSTILE,
            frame: "diamond-dashed",
        },
        {
            code: "6",
            label: "Hostile/Faker",
            localeKey: "hostile",
            color: HOSTILE,
            frame: "diamond",
        },
    ] satisfies Omit<StandardIdentityOption, "legacyC">[]
).map((identity) => ({
    ...identity,
    legacyC: Object.entries(AFFILIATION_TO_STANDARD_IDENTITY)
        .filter(([, code]) => code === identity.code)
        .map(([letter]) => letter),
}));

/**
 * Symbol set → 2525C battle dimension, mirroring `SidcMappingPolicy` in the Java SDK.
 * Lossy: every land set becomes `G`, so a legacy match is a superset, not a translation.
 */
const SYMBOL_SET_TO_BATTLE_DIMENSION: Readonly<Record<string, string>> = {
    "01": "A",
    "02": "A",
    "05": "P",
    "06": "P",
    "10": "G",
    "11": "G",
    "15": "G",
    "20": "G",
    "25": "G",
    "30": "S",
    "35": "U",
    "36": "U",
    "40": "G",
    "45": "A",
    "46": "S",
    "47": "P",
    "50": "P",
    "51": "A",
    "52": "G",
    "53": "S",
    "54": "U",
    "60": "P",
};

export interface SymbolSetOption {
    /** D-family two-digit symbol set code. */
    code: string;
    label: string;
    localeKey: string;
    /** 2525C battle-dimension letter. See {@link SYMBOL_SET_TO_BATTLE_DIMENSION} on lossiness. */
    legacyC: readonly string[];
}

const symbolSetLabel = (code: string): SIDCSymbolSet | undefined =>
    GENERATED_SIDC_SYMBOL_SETS[code];

/**
 * The 22 real symbol sets, in code order, labelled from the generated catalog.
 * `00` is excluded — it means "no symbol set", not something to filter on.
 */
export const SYMBOL_SETS: readonly SymbolSetOption[] = Object.keys(SYMBOL_SET_TO_BATTLE_DIMENSION)
    .sort()
    .flatMap((code) => {
        const entry = symbolSetLabel(code);
        if (!entry) return [];
        return [
            {
                code,
                label: entry.label,
                localeKey: entry.localeKey,
                legacyC: [SYMBOL_SET_TO_BATTLE_DIMENSION[code]],
            },
        ];
    });

/** Identity digits → the 2525C letters meaning the same thing. Unknown codes are dropped,
 *  since passing one through would build a predicate that matches nothing. */
export function standardIdentitiesToLegacyC(codes: readonly string[]): string[] {
    return toLegacyC(codes, STANDARD_IDENTITIES);
}

/** Symbol-set codes → battle-dimension letters. Lossy by construction; see the table above. */
export function symbolSetsToLegacyC(codes: readonly string[]): string[] {
    return toLegacyC(codes, SYMBOL_SETS);
}

function toLegacyC(
    codes: readonly string[],
    options: readonly { code: string; legacyC: readonly string[] }[],
): string[] {
    return uniq(
        flatMap(
            codes,
            (code) => options.find((candidate) => candidate.code === code)?.legacyC ?? [],
        ),
    );
}
