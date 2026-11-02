// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { appSourceFiles, sourceWithoutComments } from "~/test/source-census";

/**
 * The listener census, as an assertion. Every party that wants Escape
 * registers as a claimant into a slot of the Escape channel; the ONE
 * `document` keydown listener that executes the winner lives in this
 * folder. A second listener, or a React handler that decides what Escape
 * means for map state, is exactly what the ladder cannot rank — the
 * events panel kept one for a lasso result, the router's rung silently
 * preempted it, and only a human pressing the key found out.
 *
 * So the next unregistered party fails here instead. Every `"Escape"`
 * literal and every `document`/`window` keydown listener under
 * `packages/app/src`, outside `arbitration/`, must be in the allowlist
 * below — and an allowlist entry needs a reason, which is the review.
 *
 * What this does NOT police, on purpose: a tool's `onEscapeInEdit` field.
 * That is a tool telling the session what its own `dismiss()` should do —
 * lifecycle, not a second party arbitrating the key — and the ladder never
 * sees it. The regex matches the string literal `"Escape"` only, which is
 * the shape a key comparison takes; an identifier that merely contains the
 * word is not a listener.
 */

const ARBITRATION = "features/map/interaction-runtime/arbitration";

/**
 * Files that may keep a `document`/`window` keydown listener, and why. Empty:
 * the events clipboard, the last one, registers its chords as key bindings.
 */
const KEYDOWN_LISTENERS_ALLOWED: readonly string[] = [];

/** Files that may name Escape outside the registry, and why. */
const ESCAPE_HANDLERS_ALLOWED: readonly string[] = [];

const KEYDOWN_LISTENER = /(document|window)\.addEventListener\(\s*["']keydown["']/;
const ESCAPE_LITERAL = /["']Escape["']/;

describe("the Escape census — one listener, registered claimants, nothing else", () => {
    const files = appSourceFiles([ARBITRATION]);

    it("finds the app source it audits", () => {
        expect(files.length).toBeGreaterThan(100);
    });

    it("no document or window keydown listener exists outside arbitration/ and the allowlist", () => {
        const offenders = files
            .filter((file) => KEYDOWN_LISTENER.test(sourceWithoutComments(file)))
            .filter((file) => !KEYDOWN_LISTENERS_ALLOWED.includes(file));
        expect(offenders).toEqual([]);
    });

    it("no code outside arbitration/ compares against the Escape key — a party that wants it registers a claimant", () => {
        const offenders = files
            .filter((file) => ESCAPE_LITERAL.test(sourceWithoutComments(file)))
            .filter((file) => !ESCAPE_HANDLERS_ALLOWED.includes(file));
        expect(offenders).toEqual([]);
    });

    it("the allowlist names only files that still exist", () => {
        const known = new Set(files);
        for (const file of [...KEYDOWN_LISTENERS_ALLOWED, ...ESCAPE_HANDLERS_ALLOWED]) {
            expect(known.has(file), `${file} is allowlisted but gone — drop the entry`).toBe(true);
        }
    });
});
