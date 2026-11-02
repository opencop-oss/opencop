// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it } from "vitest";
import {
    escapeIsExcepted,
    everyKeyIsExcepted,
    sampleKeyboardContext,
    type KeyboardContext,
} from "./exceptions";

/**
 * The parties the runtime does not arbitrate — a focused text control and a
 * foreign overlay — and the two rules that keep the keyboard theirs. These
 * cases sat at the top of the Escape ladder's table until the exceptions
 * were named apart from the slots; the ladder now knows only its own parties.
 */

afterEach(() => {
    document.body.replaceChildren();
});

const keys = (overrides: Partial<KeyboardContext> = {}): KeyboardContext => ({
    overlayOpen: false,
    textFocused: false,
    ...overrides,
});

describe("sampleKeyboardContext — what the DOM says", () => {
    it.each(["INPUT", "TEXTAREA", "SELECT"])(
        "recognizes a focused %s as a text control",
        (tagName) => {
            const control = document.createElement(tagName);
            document.body.appendChild(control);
            control.focus();

            expect(sampleKeyboardContext().textFocused).toBe(true);
        },
    );

    it("does not treat a focused button as a text control", () => {
        const button = document.createElement("button");
        document.body.appendChild(button);
        button.focus();

        expect(sampleKeyboardContext().textFocused).toBe(false);
    });

    it.each(["dialog", "menu", "popover", "select", "color-picker"])(
        "recognizes an open %s overlay",
        (scope) => {
            const overlay = document.createElement("div");
            overlay.dataset.scope = scope;
            overlay.dataset.state = "open";
            document.body.appendChild(overlay);

            expect(sampleKeyboardContext().overlayOpen).toBe(true);
        },
    );

    it("ignores closed overlays", () => {
        const overlay = document.createElement("div");
        overlay.dataset.scope = "menu";
        overlay.dataset.state = "closed";
        document.body.appendChild(overlay);

        expect(sampleKeyboardContext().overlayOpen).toBe(false);
    });
});

describe("everyKeyIsExcepted — a focused text control holds every key", () => {
    it("a focused text control takes the keyboard whatever else stands", () => {
        // Escape typed into a tool's own form (the watchbox "Name" field) must
        // never reach the session beneath it — nor may Enter, a chord or a letter.
        expect(everyKeyIsExcepted(keys({ textFocused: true }))).toBe(true);
        expect(everyKeyIsExcepted(keys({ textFocused: true, overlayOpen: true }))).toBe(true);
    });

    it("an open overlay alone does not take every key — only Escape", () => {
        expect(everyKeyIsExcepted(keys({ overlayOpen: true }))).toBe(false);
    });
});

describe("escapeIsExcepted — a FOREIGN overlay holds Escape", () => {
    it("a foreign overlay owns Escape, whatever the map holds beneath it", () => {
        expect(escapeIsExcepted(keys({ overlayOpen: true }), false)).toBe(true);
    });

    it("a map-owned surface is NOT an exception, even though it is also an overlay", () => {
        // Both map menus are Zag menus, so the DOM sniff sees them too. A surface
        // says the runtime knows how to close this one, so it gets its slot and an
        // action rather than a shrug.
        expect(escapeIsExcepted(keys({ overlayOpen: true }), true)).toBe(false);
    });

    it("nothing open: Escape is the ladder's", () => {
        expect(escapeIsExcepted(keys(), false)).toBe(false);
    });
});
