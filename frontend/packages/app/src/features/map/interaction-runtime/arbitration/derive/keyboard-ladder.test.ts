// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { ToolDescriptor } from "@opencop/tools";
import { resolveEscape, resolveShortcut, type EscapeSlot } from "./keyboard-ladder";

/**
 * The Escape ladder, pinned as a table — one rung per press, top wins. This is
 * the written priority order that replaces the former 6–8 competing listeners
 * whose winner was registration-order folklore.
 *
 * The ladder ranks the runtime's OWN parties; it does not interpret the
 * session, and it never sees the parties the runtime does not arbitrate — a
 * focused text control, a foreign overlay — which are `../exceptions.ts`'s
 * and are pinned there. What Escape does to a session it reached is
 * `sessionEscape`'s answer, pinned in `../../session/session.test.ts`.
 */

const slots = (active: readonly EscapeSlot[] = []): Record<EscapeSlot, boolean> => ({
    session: active.includes("session"),
    surface: active.includes("surface"),
    "map-selection": active.includes("map-selection"),
    "area-selection": active.includes("area-selection"),
});

describe("resolveEscape — the ladder order", () => {
    it("1. a live session owns Escape — over a surface and over both selections", () => {
        // The ladder knows nothing of the session but that one is live: a
        // waiting tool, an in-flight draw and an edit all rank here, and what
        // Escape does to each is the session's own answer. Lasso a cluster, arm
        // Measure, press Escape: the tool leaves and the lasso's result stands —
        // Escape never clears a selection while a tool is active (Q9). The first
        // ladder cleared the area selection under a waiting tool.
        expect(
            resolveEscape(slots(["session", "surface", "area-selection", "map-selection"])),
        ).toEqual({ rung: "session" });
        expect(resolveEscape(slots(["session"]))).toEqual({ rung: "session" });
    });

    it("2. a map-owned surface closes once no session is live", () => {
        expect(resolveEscape(slots(["surface"]))).toEqual({ rung: "surface" });
    });

    it("2. the surface wins over both selections — the popover is the surface on top", () => {
        expect(resolveEscape(slots(["surface", "area-selection", "map-selection"]))).toEqual({
            rung: "surface",
        });
    });

    it("3. the map selection clears before the area selection beneath it — innermost-first", () => {
        // A point picked from inside a lasso goes back to its list; the working
        // set stands until the next press. Inverted from the first ladder, which
        // cleared the outer selection first and left the detail's breadcrumb
        // pointing at a list that was gone.
        expect(resolveEscape(slots(["area-selection", "map-selection"]))).toEqual({
            rung: "map-selection",
        });
    });

    it("3. with everything else quiet, Escape clears the selection", () => {
        expect(resolveEscape(slots(["map-selection"]))).toEqual({ rung: "map-selection" });
    });

    it("4. the area selection is the last thing Escape touches", () => {
        expect(resolveEscape(slots(["area-selection"]))).toEqual({ rung: "area-selection" });
    });

    it("nothing standing: Escape does nothing", () => {
        expect(resolveEscape(slots())).toEqual({ rung: "none" });
    });
});

describe("resolveShortcut", () => {
    it("finds the descriptor that declared the letter", () => {
        const point: ToolDescriptor = {
            id: "pointTool",
            label: "point",
            icon: () => null,
            shortcut: "p",
        };
        expect(resolveShortcut("P", [point])?.id).toBe("pointTool");
        expect(resolveShortcut("x", [point])).toBeUndefined();
    });
});
