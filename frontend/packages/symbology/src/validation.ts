// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { isSidcString } from "./sidc-format";
import { chooseEditor, chooseRenderStrategy } from "./strategy";

export type ValidationResult = { ok: true } | { ok: false; kind: "invalid" | "unsupported" };

/** Validate a point symbol. Operational Graphics are unavailable in this release. */
export function validateSidc(sidc: string): ValidationResult {
    if (!isSidcString(sidc)) return { ok: false, kind: "invalid" };
    if (
        chooseEditor(sidc) !== "symbol-editor" ||
        chooseRenderStrategy(sidc, "icon").library === "none"
    ) {
        return { ok: false, kind: "unsupported" };
    }
    return { ok: true };
}
