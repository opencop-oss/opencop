// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { appSourceFiles, sourceWithoutComments } from "~/test/source-census";

/**
 * The pointer census, as an assertion. A contended pointer event — a press,
 * a move, a release, a click, a double-click, a right-click, or any deck
 * pick but hover — belongs to whichever slot owns the pointer, and the
 * runner binds that owner's set through the sources the runtime was handed.
 * A subscription to one of those events anywhere else is a party the runner
 * cannot arbitrate: it stays bound under a live session and gates itself per
 * event, which is the pattern the context menu used to be and the rules say
 * does not exist.
 *
 * So the next such subscription fails here. Every `onMap(<contended>)` and
 * every `onDeck(<pick>)` under `packages/app/src`, outside the runtime, must
 * be in the allowlist below with its reason. Camera events (`move`,
 * `movestart`, `moveend`, `zoomend`) and `onDeck("onHover")` are uncontended
 * and free.
 */

const RUNTIME = "features/map/interaction-runtime";

/**
 * Files that may subscribe to a contended pointer event directly, and why.
 * Empty: the last one, the paste drop point, reads `MapManager.pointerLngLat()`
 * — the pointer's position is an observational read, like hover, not a
 * subscription.
 */
const POINTER_SUBSCRIPTIONS_ALLOWED: readonly string[] = [];

const CONTENDED_MAP_EVENT =
    /\.onMap\(\s*["'](mousedown|mousemove|mouseup|click|dblclick|contextmenu)["']/;
const CONTENDED_DECK_EVENT = /\.onDeck\(\s*["'](onClick|onDragStart|onDrag|onDragEnd)["']/;
const DOCUMENT_BINDING = /\.onDocument\(\s*["']/;

describe("the pointer census — the runner binds every contended pointer handler", () => {
    const files = appSourceFiles([RUNTIME]);

    it("finds the app source it audits", () => {
        expect(files.length).toBeGreaterThan(100);
    });

    it("no contended onMap / onDeck / onDocument subscription exists outside the runtime and the allowlist", () => {
        const offenders = files
            .filter((file) => {
                const source = sourceWithoutComments(file);
                return (
                    CONTENDED_MAP_EVENT.test(source) ||
                    CONTENDED_DECK_EVENT.test(source) ||
                    DOCUMENT_BINDING.test(source)
                );
            })
            .filter((file) => !POINTER_SUBSCRIPTIONS_ALLOWED.includes(file));
        expect(offenders).toEqual([]);
    });

    it("the allowlist names only files that still exist", () => {
        const known = new Set(files);
        for (const file of POINTER_SUBSCRIPTIONS_ALLOWED) {
            expect(known.has(file), `${file} is allowlisted but gone — drop the entry`).toBe(true);
        }
    });
});
