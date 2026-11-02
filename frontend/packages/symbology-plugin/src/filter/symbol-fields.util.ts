// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { isEqual } from "lodash";
import { v4 as uuid } from "uuid";
import { LegacyC, type SidcFieldRange, type StandardIdentityFrame } from "@opencop/symbology";
import type {
    ContributedFilterField,
    FilterValueEditorProps,
    Predicate,
    PredicateDraft,
    SubstringInPredicate,
    SubstringInPredicateDraft,
} from "@opencop/ui";
import { createElement } from "react";
import { SymbolFieldValueEditor } from "./SymbolFieldValueEditor";

/**
 * The SIDC fields of filter rules: the whole symbol code, and two named slices of it —
 * Standard Identity and Symbol Set — each picked from a closed list.
 *
 * MIL-STD-2525C ⇄ D fan-out and fold-back: saving a slice condition emits a second,
 * 2525C-shaped predicate after the 2525D one ({@link companionsOf}); loading folds the pair
 * back into the single row the operator built ({@link isCompanionOf}).
 *
 * Why this is a runtime fan-out rather than normalizing at ingest:
 *
 * - **The saved form is flat.** A filter is one list of predicates under one combinator,
 *   and `SUBSTRING_IN` carries exactly one `offset`/`length` pair. A nested OR-group under
 *   an outer AND, or one predicate spanning both encodings' offsets, cannot be expressed.
 * - **The two fields aren't symmetric.** Rewriting an incoming 2525C code onto its 2525D
 *   equivalent once would work for Standard Identity: every 2525C affiliation letter maps
 *   onto exactly one 2525D digit (`LegacyC.STANDARD_IDENTITIES`, lossless). It cannot work
 *   for Symbol Set: 2525C records only a battle-dimension letter, a many-to-one coarsening
 *   of the 2525D sets, so there is no canonical 2525D value to normalize *to*.
 */

const OWNER = "opencop.symbology";

/** A field with an id in this plugin's namespace. */
type SymbologyFilterField = ContributedFilterField & { readonly id: `${typeof OWNER}:${string}` };
const SYMBOL_PATH = "properties.opencop.element.symbol";

/** A named slice of the SIDC and its closed list of codes. */
export interface SymbolSlice {
    readonly offset: number;
    readonly length: number;
    /** Allowed codes in display order. `label` is the catalog's English name and backs
     *  `localeKey`, so a newly generated value renders before it's translated. */
    readonly options: readonly SymbolOption[];
    /** Present when the values also exist in 2525C: saving emits a companion predicate at
     *  `range` carrying `toLegacyC(values)`. */
    readonly legacyC?: {
        readonly range: SidcFieldRange;
        readonly toLegacyC: (codes: readonly string[]) => string[];
    };
}

export interface SymbolOption {
    readonly code: string;
    readonly localeKey: string;
    readonly label: string;
    /** Affiliation swatch, where the values have one. Symbol sets don't. */
    readonly color?: string;
    readonly frame?: StandardIdentityFrame;
}

export const STANDARD_IDENTITY: SymbolSlice = {
    ...LegacyC.SIDC_FIELD_RANGES.d.standardIdentity,
    legacyC: {
        range: LegacyC.SIDC_FIELD_RANGES.c.affiliation,
        toLegacyC: LegacyC.standardIdentitiesToLegacyC,
    },
    options: LegacyC.STANDARD_IDENTITIES.map(({ code, localeKey, label, color, frame }) => ({
        code,
        localeKey: `symbology:filter.standardIdentities.${localeKey}`,
        label,
        color,
        frame,
    })),
};

export const SYMBOL_SET: SymbolSlice = {
    ...LegacyC.SIDC_FIELD_RANGES.d.symbolSet,
    // Lossy on purpose — 2525C has only a battle dimension, so the companion matches a
    // superset; the field's notice says so.
    legacyC: {
        range: LegacyC.SIDC_FIELD_RANGES.c.battleDimension,
        toLegacyC: LegacyC.symbolSetsToLegacyC,
    },
    options: LegacyC.SYMBOL_SETS.map(({ code, localeKey, label }) => ({
        code,
        localeKey: `symbology:filter.symbolSets.${localeKey}`,
        label,
    })),
};

const SLICES = [STANDARD_IDENTITY, SYMBOL_SET];

/** A row pinned to a slice. The offsets are the slice's, not the operator's, and
 *  `caseInsensitive` stays unset — these codes are numeric. */
export function sliceDraft(
    field: string,
    slice: SymbolSlice,
    { id, values = [] }: { id?: string; values?: string[] } = {},
): SubstringInPredicateDraft {
    return {
        id: id ?? uuid(),
        field,
        negate: undefined,
        caseInsensitive: undefined,
        operator: "SUBSTRING_IN",
        value: {
            value: values,
            start: String(slice.offset),
            end: String(slice.offset + slice.length),
        },
    };
}

function isSlicePredicate(
    predicate: Predicate | undefined,
    slice: SymbolSlice,
): predicate is SubstringInPredicate {
    return (
        predicate?.operation === "SUBSTRING_IN" &&
        predicate.path === SYMBOL_PATH &&
        predicate.offset === slice.offset &&
        predicate.length === slice.length
    );
}

/**
 * Whether a saved predicate is exactly what the slice's picker produces — one value, from
 * the list, not negated. Anything else (an older hand-built slice) stays on the SIDC field,
 * which can still show its multiple or off-list values; a negated slice too, since
 * claiming it would hide the flag and expand it into a nonsense OR.
 */
function isPickerOutput(predicate: Predicate, slice: SymbolSlice): boolean {
    return (
        isSlicePredicate(predicate, slice) &&
        !predicate.negate &&
        predicate.values.length === 1 &&
        slice.options.some((option) => option.code === predicate.values[0])
    );
}

/** The 2525C half of a slice condition, emitted right after its 2525D predicate — one
 *  `SUBSTRING_IN` can only address one offset, and the two encodings differ. */
function companionsOf(predicate: Predicate, slice: SymbolSlice): Predicate[] {
    if (!slice.legacyC || predicate.operation !== "SUBSTRING_IN") return [];
    const values = slice.legacyC.toLegacyC(predicate.values);
    if (values.length === 0) return [];
    return [{ ...predicate, ...slice.legacyC.range, values }];
}

/** Whether `candidate` is the generated 2525C half of `primary`. Nothing on the saved
 *  form marks the pair, so it's matched exactly — anything less round-trips as its own row. */
function isCompanionOf(candidate: Predicate | undefined, primary: Predicate): boolean {
    if (candidate?.operation !== "SUBSTRING_IN" || primary.operation !== "SUBSTRING_IN")
        return false;
    if (candidate.path !== primary.path || candidate.negate !== primary.negate) return false;
    const slice = SLICES.find((candidateSlice) => isSlicePredicate(primary, candidateSlice));
    if (!slice?.legacyC) return false;
    if (
        candidate.offset !== slice.legacyC.range.offset ||
        candidate.length !== slice.legacyC.range.length
    )
        return false;
    return isEqual(candidate.values, slice.legacyC.toLegacyC(primary.values));
}

/** A slice's value editor, as the field contract takes it: one component per slice. */
function sliceValueEditor(slice: SymbolSlice) {
    const SliceValueEditor = (props: FilterValueEditorProps) =>
        createElement(SymbolFieldValueEditor, { ...props, slice });
    return SliceValueEditor;
}

function sliceField(
    name: string,
    order: number,
    slice: SymbolSlice,
    noticeKey?: string,
): SymbologyFilterField {
    const id = `${OWNER}:${name}` as const;
    return {
        id,
        labelKey: `symbology:filter.fields.${name}`,
        path: SYMBOL_PATH,
        order,
        // The field implies the operator, so there is nothing to choose.
        operators: ["SUBSTRING_IN"],
        // Withheld: a slice row saves an OR of the 2525D and 2525C predicates, and negating
        // that matches almost everything; and the codes are digits, with no case to ignore.
        toggles: { negate: false, caseInsensitive: false },
        newDraft: (rowId): PredicateDraft => sliceDraft(id, slice, { id: rowId }),
        ValueEditor: sliceValueEditor(slice),
        formatValue: (predicate, t) => {
            if (!isSlicePredicate(predicate, slice)) return undefined;
            const code = predicate.values[0];
            const option = slice.options.find((candidate) => candidate.code === code);
            return option ? t(option.localeKey as never, { defaultValue: option.label }) : code;
        },
        companions: (predicate) => companionsOf(predicate, slice),
        claims: (predicate, next) =>
            isPickerOutput(predicate, slice)
                ? { folds: isCompanionOf(next, predicate) ? 1 : 0 }
                : null,
        noticeKey,
    };
}

/** The whole symbol code: any operator, and any saved predicate on it no slice claims. */
const SIDC_FIELD: SymbologyFilterField = {
    id: `${OWNER}:sidc`,
    labelKey: "symbology:filter.fields.sidc",
    path: SYMBOL_PATH,
    order: 30,
    claims: (predicate, next) => {
        if (predicate.path !== SYMBOL_PATH) return null;
        if (SLICES.some((slice) => isPickerOutput(predicate, slice))) return null;
        // Folds a companion it does not emit again on save, so a hand-built slice with its
        // companion loses the companion when re-saved. Kept: reading it back as two rows
        // instead would change what saved filters show.
        return { folds: isCompanionOf(next, predicate) ? 1 : 0 };
    },
};

/** The SIDC fields, as the plugin registers them. */
export const SYMBOL_FILTER_FIELDS: readonly SymbologyFilterField[] = [
    SIDC_FIELD,
    sliceField("standardIdentity", 40, STANDARD_IDENTITY),
    sliceField("symbolSet", 50, SYMBOL_SET, "symbology:filter.symbolSetLegacyIsBroad"),
];
