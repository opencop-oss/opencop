// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import { Element, TextBox, type TextBoxElement } from "@opencop/elements";
import { FONT_FAMILY } from "@opencop/scene";
import type { DraftRefs, DrawGrammar, DrawInput } from "@opencop/tools";
import { boxFrame } from "./box-geometry.util";
import { wrapToBox } from "./box-text.util";
import { applyAutoTextSize, textBoxGrammar } from "./textbox-tools";
describe("applyAutoTextSize", () => {
    const box = (w: number, h: number) => TextBox.create({ positions: [0, 0, w, 0, w, h, 0, h] });

    it("sizes the text to a fraction of the box", () => {
        const small = box(0.01, 0.005);
        const large = box(0.02, 0.01);
        applyAutoTextSize(small);
        applyAutoTextSize(large);

        const smallSize = small.getStyle().getTextSize();
        expect(smallSize).toBeGreaterThan(0);
        // Twice the box, twice the text — so it looks the same on screen.
        expect(large.getStyle().getTextSize() / smallSize).toBeCloseTo(2, 1);
    });

    it("sizes off the box's own height, not its bounding box", () => {
        // Same box, one upright and one turned 45°. Their bounding boxes differ
        // by ~1.4x; their own heights do not, so the text must not change.
        const upright = TextBox.create({ positions: [0, 0, 0.02, 0, 0.02, 0.01, 0, 0.01] });
        const turned = TextBox.create({
            positions: [0, 0, 0.01414, 0.01414, 0.00707, 0.02121, -0.00707, 0.00707],
        });

        applyAutoTextSize(upright);
        applyAutoTextSize(turned);

        expect(turned.getStyle().getTextSize()).toBeCloseTo(upright.getStyle().getTextSize(), -1);
    });

    it("leaves a degenerate box alone", () => {
        const flat = TextBox.create({ positions: [0, 0, 0, 0, 0, 0, 0, 0] });
        const before = flat.getStyle().getTextSize();
        applyAutoTextSize(flat);
        expect(flat.getStyle().getTextSize()).toBe(before);
    });
});

describe("textBoxGrammar", () => {
    /** Create a draft and narrow it to the textbox this grammar is meant to produce. */
    function created(params: Parameters<typeof textBoxGrammar>[0]): TextBoxElement {
        const draft = textBoxGrammar(params).create([0, 0]);
        if (!(draft instanceof Element) || !TextBox.is(draft)) {
            throw new Error("expected the grammar to create a textbox element");
        }
        return draft;
    }

    it("sizes the text from the box when the operator has not chosen a size", () => {
        const element = created({});
        element.geometry.setAllPos([0, 0, 0.02, 0, 0.02, 0.01, 0, 0.01]);

        applyAutoTextSize(element);

        expect(element.getStyle().getTextSize()).toBeGreaterThan(0);
    });

    it("creates the box with a size chosen before drawing, before the draw replaces it", () => {
        expect(created({ textSize: 1234 }).getStyle().getTextSize()).toBe(1234);
    });

    it("carries pending text and colour onto the new box", () => {
        const element = created({ text: "ALPHA", textColor: "#ff0000ff" });

        expect(element.getStyle().getText()).toBe("ALPHA");
        expect(element.getStyle().getTextColor()).toBe("#ff0000ff");
    });
});

/**
 * The grammar driven the way the host's input drives it, so the last phase's
 * `preview` runs for real. `create()` alone never reaches it, and the auto-size
 * and the grow only happen there.
 *
 * Local fakes rather than `tools`' own: `grammars.test.ts:16-66` keeps its
 * copies unexported, and a plugin reads the host's contract, not its tests.
 */
function drawn(grammar: DrawGrammar) {
    const listeners = new Map<string, Array<(ev: unknown) => void>>();
    const input = {
        onPointer: (event, listener) => {
            listeners.set(event, [
                ...(listeners.get(event) ?? []),
                listener as (e: unknown) => void,
            ]);
            return { event, unsub: vi.fn() };
        },
        onRelease: () => ({ event: "release", unsub: vi.fn() }),
        onEnter: () => ({ event: "enter", unsub: vi.fn() }),
        consumeNextClick: vi.fn(),
    } satisfies DrawInput;

    let element: Element | undefined;
    let locked = 0;
    let placed = false;
    const refs = {
        getDraft: () => element,
        setDraft: (el: Element) => {
            element = el;
        },
        // The phase a parametric draw is in is read back off the locked count,
        // so it has to be real state and not a spy.
        getLocked: () => locked,
        setLocked: (count: number) => {
            locked = count;
        },
        isPlaced: () => placed,
        onPlace: vi.fn(() => {
            placed = true;
        }),
        onComplete: vi.fn(),
        onAbort: vi.fn(),
        onUpdate: vi.fn(),
    } satisfies DraftRefs;

    grammar.bind(input, refs);

    const fire = (event: string, lngLat: [number, number]) =>
        listeners
            .get(event)
            ?.forEach((l) =>
                l({ lngLat, point: { x: 0, y: 0 }, button: 0, preventDefault: vi.fn() }),
            );

    /** Two clicks for the first edge, then a move for the width. */
    const draw = (corners: [[number, number], [number, number], [number, number]]) => {
        fire("mousemove", corners[0]);
        fire("click", corners[0]);
        fire("mousemove", corners[1]);
        fire("click", corners[1]);
        fire("mousemove", corners[2]);
    };

    const draft = (): TextBoxElement => {
        if (!(element instanceof Element) || !TextBox.is(element)) {
            throw new Error("expected the grammar to draft a textbox element");
        }
        return element;
    };

    return { draw, draft, fire, refs };
}

describe("the textbox draw", () => {
    const text = "a sticky note grows as you fill it with more and more content ".repeat(6);

    it("grows the box to hold its text while the last phase is drawn", () => {
        const { draw, draft } = drawn(textBoxGrammar({ text }));

        // The pointer asks for a box far shorter than the text needs.
        draw([
            [0, 0],
            [0.001, 0],
            [0.001, 0.0005],
        ]);

        const box = draft();
        const frame = boxFrame(box.geometry.getAllPos())!;
        const needed = wrapToBox(text, frame.width, box.getStyle().getTextSize(), FONT_FAMILY);
        expect(frame.height).toBeGreaterThanOrEqual(needed.height - 1e-6);
    });

    it("replaces a size staged before the draw with the one the box implies", () => {
        const { draw, draft } = drawn(textBoxGrammar({ text, textSize: 50 }));

        draw([
            [0, 0],
            [0.001, 0],
            [0.001, 0.0005],
        ]);

        // 50 reads as large from a word processor and is invisible on the map,
        // so the draw's own size always wins — see `textBoxGrammar`'s contract.
        expect(draft().getStyle().getTextSize()).not.toBe(50);
    });

    it("finishes a box drawn westward with its first edge running east", () => {
        const { draw, draft, fire, refs } = drawn(textBoxGrammar({ text: "note" }));

        const corners: [[number, number], [number, number], [number, number]] = [
            [0.002, 0],
            [0, 0],
            [0, -0.001],
        ];
        draw(corners);
        fire("click", corners[2]);

        const [x0, , x1] = draft().geometry.getAllPos();
        expect(refs.onComplete).toHaveBeenCalled();
        expect(x1).toBeGreaterThan(x0);
    });
});
