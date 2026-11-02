// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import { Polygon, type DraftObject, type Element } from "@opencop/elements";
import { type CompletionHost, type DrawGrammar, producedElementType } from "@opencop/tools";
import { lassoDescriptor, LASSO_TOOL_ID } from "./lasso.descriptor";
import { LASSO_PRODUCT_TYPE, lassoTrace, type LassoTrace } from "./lasso-trace.model";

/** A ring big enough to survive simplification, as a flat position list. */
const RING = [-104.99, 39.74, -104.98, 39.74, -104.98, 39.75, -104.99, 39.75];

function ring(positions: number[] = RING): LassoTrace {
    const trace = lassoTrace([positions[0], positions[1]]);
    trace.geometry.setAllPos(positions);
    return trace;
}

/** An element the host finds inside the ring. */
function enclosed(): Element {
    return Polygon.create({ positions: RING });
}

type FakeHost = CompletionHost & {
    pickInsidePolygon: ReturnType<typeof vi.fn>;
    selectElements: ReturnType<typeof vi.fn>;
};

function fakeHost(matched: Element[]): FakeHost {
    return {
        pickInsidePolygon: vi.fn(() => matched),
        selectElements: vi.fn(),
    } as FakeHost;
}

describe("the lasso descriptor", () => {
    it("declares only what a trace tool needs — no element type, so no custody", () => {
        expect(lassoDescriptor.id).toBe(LASSO_TOOL_ID);
        // A trace grammar drafting the lasso's own trace, under its product type.
        const grammar = lassoDescriptor.draw?.grammar as DrawGrammar;
        expect(grammar.locksPan).toBe(true);
        expect(grammar.create([0, 0]).type).toBe(LASSO_PRODUCT_TYPE);
        // A product outside the element store is the whole custody rule: the
        // trace produces a throwaway ring the ElementStore never sees.
        expect(producedElementType(lassoDescriptor.produces)).toBeUndefined();
        // No edit model and no escape override: a completed lasso exits rather
        // than becoming an edit session.
        expect(lassoDescriptor.draw?.editModel).toBeUndefined();
        expect(lassoDescriptor.onEscapeInEdit).toBeUndefined();
    });
});

describe("onComplete", () => {
    const complete = (trace: DraftObject, host: CompletionHost) =>
        lassoDescriptor.onComplete!(trace, host);

    it("hands the enclosed elements and the ring to the host, then exits", () => {
        const matched = [enclosed(), enclosed()];
        const host = fakeHost(matched);

        expect(complete(ring(), host)).toBe("exit");

        expect(host.selectElements).toHaveBeenCalledTimes(1);
        const [elements, boundary] = host.selectElements.mock.calls[0];
        expect(elements).toEqual(matched);
        // The boundary is the traced ring, open — four corners, not five.
        expect(boundary).toHaveLength(4);
    });

    it("clears the selection and re-arms when the ring encloses nothing", () => {
        const host = fakeHost([]);

        expect(complete(ring(), host)).toBe("re-arm");

        // An empty hand-over, not silence: a miss must drop the previous ring
        // rather than leaving a stale one on the map.
        expect(host.selectElements).toHaveBeenCalledWith([], []);
    });

    it("re-arms without touching the host when the gesture is too short to be a ring", () => {
        const host = fakeHost([enclosed()]);

        expect(complete(ring([-104.99, 39.74, -104.98, 39.74]), host)).toBe("re-arm");

        expect(host.pickInsidePolygon).not.toHaveBeenCalled();
        expect(host.selectElements).not.toHaveBeenCalled();
    });

    it("picks with the simplified ring when simplification leaves enough vertices", () => {
        const host = fakeHost([enclosed()]);

        complete(ring(), host);

        const picked = host.pickInsidePolygon.mock.calls[0][0];
        expect(picked.length).toBeGreaterThanOrEqual(3);
    });
});
