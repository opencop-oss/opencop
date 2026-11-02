// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Public API of `@opencop/symbology`, organized as capability namespaces.
 *
 * Six primary capability exports cover the symbology API:
 *   - `SIDC`       — class for SIDC parsing / manipulation / comparison
 *   - `Validator`  — point-symbol SIDC validation
 *   - `Renderer`   — point-symbol icon rendering
 *   - `Catalog`    — point-symbol display tables
 *   - `LegacyC`    — MIL-STD-2525D ⇄ 2525C field mappings and SIDC field offsets
 *   - `Defaults`   — default SIDC / entity / symbol-set values
 *
 * The barrel also exports the general-purpose `LRU` utility.
 *
 * Renderer implementation modules (`milstd/`, `milsymbol/`, `strategy.ts`)
 * are deliberately not re-exported. Consumers reach the renderer libraries
 * only through the capability namespaces above.
 */

// ─── SIDC class (existing) ────────────────────────────────────────────
export { SIDC } from "./sidc.ts";
export type {
    SIDCData,
    SIDCFields,
    SIDCLabel,
    SIDCMap,
    SIDCEntityType,
    SIDCEntity,
    SIDCSymbolSet,
    SIDCAmplifier,
} from "./sidc.ts";

// ─── Validator namespace ──────────────────────────────────────────────
import { validateSidc } from "./validation.ts";

export const Validator = {
    validateSidc,
} as const;
export type { ValidationResult } from "./validation.ts";

// ─── Renderer namespace ───────────────────────────────────────────────
import { render, renderPreview, MISSING_ICON_FALLBACK } from "./render.ts";

export const Renderer = {
    render,
    renderPreview,
    MISSING_ICON_FALLBACK,
} as const;
export type { RenderResult } from "./render.ts";

import { AMPLIFIERS, POINT_SYMBOL_SETS } from "./sidc.ts";
export const Catalog = { AMPLIFIERS, POINT_SYMBOL_SETS } as const;

// ─── LegacyC namespace ────────────────────────────────────────────────
import {
    LEGACY_C_AFFILIATIONS,
    SIDC_FIELD_RANGES,
    STANDARD_IDENTITIES,
    SYMBOL_SETS,
    standardIdentitiesToLegacyC,
    symbolSetsToLegacyC,
} from "./legacy-c.ts";

export const LegacyC = {
    LEGACY_C_AFFILIATIONS,
    SIDC_FIELD_RANGES,
    STANDARD_IDENTITIES,
    SYMBOL_SETS,
    standardIdentitiesToLegacyC,
    symbolSetsToLegacyC,
} as const;
export type {
    LegacyCAffiliationOption,
    SidcFieldRange,
    StandardIdentityFrame,
    StandardIdentityOption,
    SymbolSetOption,
} from "./legacy-c.ts";

// ─── Defaults namespace ───────────────────────────────────────────────
import { defaultSidc } from "./defaults.ts";

export const Defaults = {
    defaultSidc,
} as const;

// ─── General TypeScript utilities ─────────────────────────────────────
// Generic helpers that happen to live in this package. They are not SIDC-
// specific; promote out if a third consumer appears. (`assertNever` was
// promoted out — use the `assert-never` package instead.)
export { LRU } from "./lru.ts";
