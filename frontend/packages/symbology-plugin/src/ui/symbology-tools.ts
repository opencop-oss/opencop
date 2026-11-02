// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Symbol } from "@opencop/elements";
import { SIDC } from "@opencop/symbology";
import { createPointGrammar, type DrawGrammar, type ToolParams } from "@opencop/tools";

/**
 * The shape symbology stores in the session's opaque {@link ToolParams} bag. The
 * bag is deliberately untyped at the runtime layer (each subject owns its own params), so
 * this is the ONE place that names symbology's keys and narrows them — every
 * reader goes through {@link readSymbologyParams} rather than casting inline.
 */
export interface SymbologyToolParams {
    sidc?: SIDC;
    modifiers?: Record<string, string>;
}

/** Read symbology's typed params out of the opaque tool-params bag. */
export function readSymbologyParams(params: ToolParams): SymbologyToolParams {
    return {
        sidc: params.sidc as SIDC | undefined,
        modifiers: params.modifiers as Record<string, string> | undefined,
    };
}

/**
 * The single-point symbol tool: places a MIL-STD symbol at the cursor.
 *
 * Built from the tool's current SIDC rather than a constant, so the element is
 * created with its final code. `@opencop/elements` has no default symbol code —
 * it does not interpret codes, so it cannot pick a sensible one — which makes
 * supplying it symbology's job.
 */
export function symbolGrammarFromSIDC(sidc: SIDC): DrawGrammar {
    return createPointGrammar({
        create: (cursor) => Symbol.create({ position: cursor, sidc: sidc.toString() }),
    });
}
