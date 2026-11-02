// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * A tool's own draw grammar: a grammar the tool implements itself — not one of
 * the built-ins — drives a draw end to end (activation, the pointer, placement,
 * completion into edit) through the same runtime every built-in grammar uses,
 * and the host contains it. See `~/test/interaction-runtime-harness` for the
 * fixtures.
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import Logger from "js-logger";
import { Line } from "@opencop/elements";
import { ELEMENT_STORE_ID, type DrawGrammar, type ToolDescriptor } from "@opencop/tools";
import { getTool, registerTool } from "~/features/contributions/tool-registry";
import { __resetContributionFailureLog } from "~/features/contributions/contribution-guard";
import { sessionDrawHints } from "./session/derive/draw-hints";
import { getInteractionState } from "./session/interaction.store";
import { activate } from "./session/commands";
import { deliverSessionEnter } from "./session/session-enter";
import { attachHost } from "./host";
import {
    attachPointerArbitration,
    fakeDeps,
    installInteractionRuntimeHarness,
    mapMouse,
} from "~/test/interaction-runtime-harness";

installInteractionRuntimeHarness();

/** A test-only grammar: two clicks, start and end — no preview, no double-click. */
const segmentGrammar: DrawGrammar = {
    create: (cursor) => Line.create({ positions: cursor }),
    bind: (input, draft) => [
        input.onPointer("click", (ev) => {
            const existing = draft.getDraft();
            if (!existing) {
                const element = segmentGrammar.create(ev.lngLat);
                draft.setDraft(element);
                draft.onPlace(element);
                draft.setLocked(1);
                return;
            }
            existing.geometry.setPos(ev.lngLat, 1);
            draft.setLocked(2);
            draft.onComplete(existing);
        }),
    ],
    hints: (state) => [{ key: state.phase === "armed" ? "test.start" : "test.end" }],
};

function segmentTool(grammar: DrawGrammar = segmentGrammar): ToolDescriptor {
    return {
        id: "fixtureSegment",
        label: "segment",
        icon: () => null,
        produces: { store: ELEMENT_STORE_ID, type: "line" },
        draw: { grammar },
    };
}

const disposers: Array<() => void> = [];
afterEach(() => {
    for (const dispose of disposers.splice(0)) dispose();
});

describe("a tool's own draw grammar", () => {
    it("draws end to end: armed, placed, completed into a custody edit", () => {
        const deps = fakeDeps();
        disposers.push(attachHost(deps.host));
        registerTool(segmentTool());
        disposers.push(attachPointerArbitration(deps));

        expect(activate("fixtureSegment")).toEqual({ ok: true });
        expect(sessionDrawHints(getInteractionState().session, getTool)).toEqual([
            { key: "test.start" },
        ]);

        deps.fire("click", mapMouse([0, 0], { x: 0, y: 0 }));
        expect(getInteractionState().session).toMatchObject({ kind: "draw", locked: 1 });
        expect(sessionDrawHints(getInteractionState().session, getTool)).toEqual([
            { key: "test.end" },
        ]);

        deps.fire("click", mapMouse([2, 3], { x: 20, y: 30 }));
        const session = getInteractionState().session;
        expect(session).toMatchObject({
            kind: "edit",
            custody: true,
            descriptorId: "fixtureSegment",
        });
        expect(session.kind === "edit" && session.product.geometry.getAllPos()).toEqual([
            0, 0, 2, 3,
        ]);
        expect(session.kind === "edit" && session.product).toBe(deps.held());
    });

    it("places its product whole on immediate placement, through its own `create`", () => {
        const deps = fakeDeps();
        disposers.push(attachHost(deps.host));
        registerTool(segmentTool());

        expect(activate("fixtureSegment", { placeAt: [4, 5] })).toEqual({ ok: true });

        const session = getInteractionState().session;
        expect(session.kind === "edit" && session.product.geometry.getAllPos()).toEqual([4, 5]);
    });
});

describe("a tool's own draw grammar is contained", () => {
    function withGrammar(grammar: DrawGrammar) {
        __resetContributionFailureLog();
        const errors: string[] = [];
        vi.spyOn(Logger.get("plugins"), "error").mockImplementation((...args: unknown[]) => {
            errors.push(args.map(String).join(" "));
        });
        const deps = fakeDeps();
        disposers.push(attachHost(deps.host));
        registerTool(segmentTool(grammar));
        disposers.push(attachPointerArbitration(deps));
        disposers.push(() => vi.restoreAllMocks());
        return { deps, errors };
    }

    it("a bind that throws is reported and releases what it bound; the session binds nothing", () => {
        const unsub = vi.fn();
        const { deps, errors } = withGrammar({
            ...segmentGrammar,
            bind: (input) => {
                const bound = input.onPointer("click", () => undefined);
                (bound as { unsub(): void }).unsub = unsub;
                throw new Error("bind exploded");
            },
        });

        expect(() => activate("fixtureSegment")).not.toThrow();
        expect(errors.some((e) => e.includes("fixtureSegment"))).toBe(true);
        expect(unsub).toHaveBeenCalled();

        deps.fire("click", mapMouse([0, 0], { x: 0, y: 0 }));
        expect(getInteractionState().session).toMatchObject({ kind: "draw", locked: 0 });
    });

    it("a listener that throws is reported and ends the session; the map survives", () => {
        const { deps, errors } = withGrammar({
            ...segmentGrammar,
            bind: (input) => [
                input.onPointer("click", () => {
                    throw new Error("listener exploded");
                }),
            ],
        });
        activate("fixtureSegment");

        expect(() => deps.fire("click", mapMouse([0, 0], { x: 0, y: 0 }))).not.toThrow();
        expect(errors.some((e) => e.includes("fixtureSegment"))).toBe(true);
        expect(getInteractionState().session.kind).toBe("idle");
    });

    it("an Enter listener that throws is reported and ends the session, and is released with it", () => {
        const onEnter = vi.fn(() => {
            throw new Error("enter exploded");
        });
        const { errors } = withGrammar({
            ...segmentGrammar,
            bind: (input, draft) => [...segmentGrammar.bind(input, draft), input.onEnter(onEnter)],
        });
        activate("fixtureSegment");

        expect(() => deliverSessionEnter()).not.toThrow();
        expect(errors.some((e) => e.includes("fixtureSegment"))).toBe(true);
        expect(getInteractionState().session.kind).toBe("idle");

        deliverSessionEnter();
        expect(onEnter).toHaveBeenCalledOnce();
    });
});
