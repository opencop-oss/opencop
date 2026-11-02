// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import { TextBox, type TextBoxElement } from "@opencop/elements";
import type { ToolSession } from "@opencop/tools";
import { idleToolSession, render, screen, userEvent } from "../test";
import { TextBoxToolEditor } from "./TextBoxToolEditor";

/** A drawn box; it grows to hold whatever is typed into it. */
function drawnBox(): TextBoxElement {
    return TextBox.create({ positions: [0, 0, 0.05, 0, 0.05, 0.03, 0, 0.03] });
}

const drawing = (draft: TextBoxElement, overrides: Partial<ToolSession> = {}): ToolSession => ({
    ...idleToolSession,
    draft,
    phase: "draw",
    ...overrides,
});

describe("TextBoxToolEditor", () => {
    it("stages typed content in the session params before a box exists", async () => {
        const setParams = vi.fn();
        render(<TextBoxToolEditor />, { session: { ...idleToolSession, setParams } });

        await userEvent.type(screen.getByLabelText("Text"), "ALPHA");

        // The whole string accumulates: a regression that only shows from the
        // second keystroke on would pass against one character.
        expect(setParams).toHaveBeenLastCalledWith(expect.objectContaining({ text: "ALPHA" }));
    });

    it("writes to the box's own style once one exists, and tells the map", async () => {
        const draft = drawnBox();
        const invalidate = vi.fn();
        render(<TextBoxToolEditor />, { session: drawing(draft, { invalidate }) });

        await userEvent.type(screen.getByLabelText("Text"), "A");

        expect(draft.getStyle().getText()).toBe("A");
        // The exact kind, not either of them: matching both passes with the two
        // inverted, and the map then rebuilds the wrong thing. Text grows the
        // box to hold it, so this is the geometry case.
        expect(invalidate).toHaveBeenLastCalledWith(draft, "geometry");
    });

    it("reports a style change for an edit that cannot move the box", async () => {
        const draft = drawnBox();
        draft.getStyle().setText("KEEP ME");
        const invalidate = vi.fn();
        render(<TextBoxToolEditor />, { session: drawing(draft, { invalidate }) });

        await userEvent.click(screen.getByRole("switch", { name: "Show text" }));

        expect(invalidate).toHaveBeenLastCalledWith(draft, "style");
    });

    it("disables show-text until there is a box to hide", () => {
        render(<TextBoxToolEditor />);
        expect(screen.getByRole("switch", { name: "Show text" })).toBeDisabled();
    });

    it("hides the text without discarding it", async () => {
        const draft = drawnBox();
        draft.getStyle().setText("KEEP ME");
        render(<TextBoxToolEditor />, { session: drawing(draft) });

        await userEvent.click(screen.getByRole("switch", { name: "Show text" }));

        expect(draft.getStyle().isTextVisible()).toBe(false);
        expect(draft.getStyle().getText()).toBe("KEEP ME");
    });

    it("grows the box downwards as content outgrows it", async () => {
        const draft = drawnBox();
        const before = draft.geometry.getAllPos().slice();
        render(<TextBoxToolEditor />, { session: drawing(draft) });

        await userEvent.click(screen.getByLabelText("Text"));
        await userEvent.paste("a sticky note grows as you fill it with more and more content");

        // The whole string reaches the draft, not just the last keystroke.
        expect(draft.getStyle().getText()).toContain("sticky note grows");

        const after = draft.geometry.getAllPos();
        // The first edge is pinned; the far edge moves away from it.
        expect(after.slice(0, 4)).toEqual(before.slice(0, 4));
        expect(after[5]).toBeGreaterThan(before[5]);
        expect(after[7]).toBeGreaterThan(before[7]);
    });

    it("keeps the size the operator picked, however much is typed", async () => {
        const draft = drawnBox();
        draft.getStyle().setTextSize(500);
        render(<TextBoxToolEditor />, { session: drawing(draft) });

        await userEvent.type(screen.getByLabelText("Text"), "plenty of content to overflow with");

        expect(draft.getStyle().getTextSize()).toBe(500);
    });
});
