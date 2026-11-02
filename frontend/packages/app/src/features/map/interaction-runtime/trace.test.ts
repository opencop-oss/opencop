// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/** A trace tool on its draw grammar: registry data, press → drag → release, restart mid-trace. See `~/test/interaction-runtime-harness` for the fixtures. */
import { describe, expect, it, vi } from "vitest";
import { Polygon } from "@opencop/elements";
import { ELEMENT_STORE_ID, type ToolDescriptor } from "@opencop/tools";
import { registerTool } from "~/features/contributions/tool-registry";
import { getInteractionState, isInFlight } from "./session/interaction.store";
import {
    activate,
    beginDraft,
    canActivate,
    draftCreated,
    isDraftPlaced,
    restart,
    setLocked,
} from "./session/commands";
import { attachHost } from "./host";
import {
    fakeDeps,
    mapMouse,
    traceFixture,
    installInteractionRuntimeHarness,
    attachPointerArbitration,
} from "~/test/interaction-runtime-harness";

installInteractionRuntimeHarness();

describe("a lasso-class tool is registry data", () => {
    it("a fixture trace descriptor registered by name activates into the same draw session a shape gets", () => {
        const deps = fakeDeps();
        const unconfigure = attachHost(deps.host);

        registerTool(traceFixture(() => "exit"));

        expect(canActivate("fixtureTrace")).toBe(true);
        expect(activate("fixtureTrace")).toEqual({ ok: true });

        const session = getInteractionState().session;
        expect(session).toMatchObject({
            kind: "draw",
            descriptorId: "fixtureTrace",
            custody: false,
            draft: null,
        });
        // No custody, so arming it leaves the current selection alone — the
        // pick the last lasso made survives until a new ring replaces it.
        expect(deps.mapManager.handleEmptyClick).not.toHaveBeenCalled();

        unconfigure();
    });
});

describe("restart — a trace in flight", () => {
    it("aborts the trace's polygon draft and re-arms, like any placed draw", () => {
        const deps = fakeDeps();
        const unconfigure = attachHost(deps.host);
        registerTool(traceFixture(() => "exit"));

        activate("fixtureTrace");
        const armed = getInteractionState().session;
        const epoch = armed.kind === "draw" ? armed.epoch : -1;
        // The press: create + place at once, first vertex locked.
        const ring = Polygon.create({ positions: [0, 0] });
        draftCreated(ring, epoch);
        beginDraft(ring, epoch);
        setLocked(1, epoch);
        expect(getInteractionState().session).toMatchObject({ kind: "draw", locked: 1 });
        expect(isInFlight(getInteractionState().session)).toBe(true);

        // A trace's captured vertices ARE its draft, so the generic rule
        // applies — no descriptor-owned state, no special case to refuse on.
        expect(restart()).toEqual({ ok: true });
        expect(getInteractionState().session).toMatchObject({
            kind: "draw",
            draft: null,
            locked: 0,
        });
        expect(isDraftPlaced()).toBe(false);

        unconfigure();
    });
});

describe("a trace runs on its draw grammar — press, drag, release", () => {
    it("completes through onComplete with the captured ring, and the host drops the click the release may synthesize", () => {
        const deps = fakeDeps();
        const unconfigure = attachHost(deps.host);
        const onComplete = vi.fn<NonNullable<ToolDescriptor["onComplete"]>>(() => "exit");
        registerTool(traceFixture(onComplete));
        const dispose = attachPointerArbitration(deps);

        expect(activate("fixtureTrace")).toEqual({ ok: true });

        deps.fire("mousedown", mapMouse([0, 0], { x: 0, y: 0 }));
        const engaged = getInteractionState().session;
        expect(engaged).toMatchObject({ kind: "draw", locked: 1 });
        const ring = engaged.kind === "draw" ? (engaged.draft?.object ?? null) : null;
        expect(ring).not.toBeNull();
        expect(isDraftPlaced()).toBe(true);

        // Under the throttle: no vertex. Past it: one vertex per move.
        deps.fire("mousemove", mapMouse([0.1, 0], { x: 4, y: 0 }));
        deps.fire("mousemove", mapMouse([1, 0], { x: 40, y: 0 }));
        deps.fire("mousemove", mapMouse([1, 1], { x: 40, y: 40 }));
        expect(getInteractionState().session).toMatchObject({ locked: 3 });
        expect(ring!.geometry.getAllPos()).toEqual([0, 0, 1, 0, 1, 1]);

        // The release is heard at document level (it may land off-canvas).
        document.dispatchEvent(new MouseEvent("mouseup", { button: 0 }));

        expect(onComplete).toHaveBeenCalledTimes(1);
        expect(onComplete.mock.calls[0][0]).toBe(ring);
        expect(onComplete.mock.calls[0][1]).toMatchObject({
            pickInsidePolygon: expect.any(Function),
            selectElements: expect.any(Function),
        });
        expect(deps.mapManager.consumeNextDeckClick).toHaveBeenCalledTimes(1);
        expect(getInteractionState().session.kind).toBe("idle");

        dispose();
        unconfigure();
    });

    it("a throwing onComplete is the tool's defect: the session ends as exited and nothing stays stored", () => {
        // `onComplete` is contributed code, a plugin's included — contained like
        // `resolveBehavior`, never allowed to take the runtime down.
        const deps = fakeDeps();
        const unconfigure = attachHost(deps.host);
        const onComplete = vi.fn<NonNullable<ToolDescriptor["onComplete"]>>(() => {
            throw new Error("boom");
        });
        registerTool({
            ...traceFixture(onComplete),
            produces: { store: ELEMENT_STORE_ID, type: "polygon" },
        });
        const dispose = attachPointerArbitration(deps);

        activate("fixtureTrace");
        deps.fire("mousedown", mapMouse([0, 0], { x: 0, y: 0 }));
        expect(deps.host.custody.isLocked()).toBe(true);

        expect(() =>
            document.dispatchEvent(new MouseEvent("mouseup", { button: 0 })),
        ).not.toThrow();

        expect(onComplete).toHaveBeenCalledTimes(1);
        expect(getInteractionState().session.kind).toBe("idle");
        expect(deps.store.size).toBe(0);
        expect(deps.host.custody.isLocked()).toBe(false);

        dispose();
        unconfigure();
    });

    it("re-arms with nothing placed when onComplete asks for another attempt", () => {
        const deps = fakeDeps();
        const unconfigure = attachHost(deps.host);
        registerTool(traceFixture(() => "re-arm"));
        const dispose = attachPointerArbitration(deps);

        activate("fixtureTrace");
        const before = getInteractionState().session;
        const epoch = before.kind === "draw" ? before.epoch : -1;
        deps.fire("mousedown", mapMouse([0, 0], { x: 0, y: 0 }));
        document.dispatchEvent(new MouseEvent("mouseup", { button: 0 }));

        const after = getInteractionState().session;
        expect(after).toMatchObject({ kind: "draw", draft: null, locked: 0 });
        if (after.kind === "draw") expect(after.epoch).toBeGreaterThan(epoch);
        // The next press must be able to place again.
        expect(isDraftPlaced()).toBe(false);

        dispose();
        unconfigure();
    });

    it("a right-click mid-press abandons the trace and re-arms; the release that follows completes nothing", () => {
        const deps = fakeDeps();
        const unconfigure = attachHost(deps.host);
        const onComplete = vi.fn<NonNullable<ToolDescriptor["onComplete"]>>(() => "exit");
        registerTool(traceFixture(onComplete));
        const dispose = attachPointerArbitration(deps);

        activate("fixtureTrace");
        deps.fire("mousedown", mapMouse([0, 0], { x: 0, y: 0 }));
        deps.fire("mousemove", mapMouse([1, 0], { x: 40, y: 0 }));
        expect(getInteractionState().session).toMatchObject({ kind: "draw", locked: 2 });

        const menu = mapMouse([1, 0], { x: 40, y: 0 }, 2);
        deps.fire("contextmenu", menu);
        expect(menu.preventDefault).toHaveBeenCalled();
        expect(getInteractionState().session).toMatchObject({
            kind: "draw",
            draft: null,
            locked: 0,
        });

        document.dispatchEvent(new MouseEvent("mouseup", { button: 0 }));
        expect(onComplete).not.toHaveBeenCalled();
        expect(getInteractionState().session).toMatchObject({ kind: "draw", draft: null });

        dispose();
        unconfigure();
    });

    it("releases a draft the hook consumed — under custody too, so nothing stays stored and locked", () => {
        // `onComplete` is where a product of this kind is consumed, not kept:
        // a typed trace would otherwise leave its ring in the store, locked,
        // with no session left to release it.
        const deps = fakeDeps();
        const unconfigure = attachHost(deps.host);
        registerTool({
            ...traceFixture(() => "exit"),
            produces: { store: ELEMENT_STORE_ID, type: "polygon" },
        });
        const dispose = attachPointerArbitration(deps);

        activate("fixtureTrace");
        deps.fire("mousedown", mapMouse([0, 0], { x: 0, y: 0 }));
        expect(deps.host.custody.isLocked()).toBe(true);

        document.dispatchEvent(new MouseEvent("mouseup", { button: 0 }));

        expect(getInteractionState().session.kind).toBe("idle");
        expect(deps.store.size).toBe(0);
        expect(deps.host.custody.isLocked()).toBe(false);

        dispose();
        unconfigure();
    });
});
