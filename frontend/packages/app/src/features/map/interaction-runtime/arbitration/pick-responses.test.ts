// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it, vi } from "vitest";
import { Point } from "@opencop/elements";
import { pickResponseFor, registerPickResponse, type PickTarget } from "./pick-responses";

/**
 * Ambient's click dispatch: the response registered for the id the nearest
 * hit declared receives a click that has no element beneath — elements first
 * is the arbiter's rule, and empty ground is nobody's.
 */

const target = (overrides: Partial<PickTarget> = {}): PickTarget => ({
    screen: { x: 0, y: 0 },
    hit: null,
    elements: [],
    ...overrides,
});

/** A hit drawn for an object of the store `responseId`, or — for `null` — by a layer outside the scene. */
const hit = (responseId: string | null): PickTarget["hit"] =>
    responseId === null
        ? { responseId: null, ref: null }
        : { responseId, ref: { type: responseId, key: "k" } };

const response = () => ({ onClick: vi.fn() });

const disposers: Array<() => void> = [];
const register = (id: string, r: ReturnType<typeof response>) => {
    const dispose = registerPickResponse(id, r);
    disposers.push(dispose);
    return dispose;
};

afterEach(() => {
    disposers.splice(0).forEach((dispose) => dispose());
});

describe("pickResponseFor", () => {
    it("a click on a hit that declared a registered id, with nothing beneath, is that response's", () => {
        const regions = response();
        register("regions", regions);
        expect(pickResponseFor(target({ hit: hit("regions") }))).toBe(regions);
    });

    it("elements first: an element under the pointer wins over a declared hit drawn above it", () => {
        register("regions", response());
        const t = target({ hit: hit("regions"), elements: [Point.create({ position: [0, 0] })] });
        expect(pickResponseFor(t)).toBeUndefined();
    });

    it("empty ground, a hit that declared no id, and an id nothing registered for are all nobody's", () => {
        register("regions", response());
        expect(pickResponseFor(target())).toBeUndefined();
        expect(pickResponseFor(target({ hit: hit(null) }))).toBeUndefined();
        expect(pickResponseFor(target({ hit: hit("other") }))).toBeUndefined();
    });

    it("a second registration for one id replaces the first — a collision at registration, never a tie at click time", () => {
        const first = response();
        const second = response();
        register("regions", first);
        register("regions", second);
        expect(pickResponseFor(target({ hit: hit("regions") }))).toBe(second);
    });

    it("the disposer withdraws the response; a stale disposer leaves its replacement alone", () => {
        const first = response();
        const second = response();
        const disposeFirst = register("regions", first);
        register("regions", second);
        disposeFirst();
        expect(pickResponseFor(target({ hit: hit("regions") }))).toBe(second);
    });
});
