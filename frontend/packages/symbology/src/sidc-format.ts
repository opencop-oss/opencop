// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * What an SIDC looks like as a string: exactly 20 digits.
 *
 * Its own module so `sidc.ts` and `validation.ts` share one definition without
 * either importing the other — `validation` pulling in `SIDC` would drag the render
 * strategy into its module graph for a regex.
 */
const SIDC_STRING = /^\d{20}$/;

/**
 * Whether a string is shaped like an SIDC at all.
 *
 * A *format* check, not a validity one: `Validator.validateSidc` answers whether a
 * well-formed SIDC is actually drawable. This exists so a consumer holding an
 * element's raw code can ask "is this mine?" without knowing which other schemes
 * share that slot (media-icon URLs, for one).
 */
export function isSidcString(value: string | undefined): boolean {
    return value !== undefined && SIDC_STRING.test(value);
}
