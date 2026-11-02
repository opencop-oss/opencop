// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/** The commands: canActivate, setParams, complete, restart. See `~/test/interaction-runtime-harness` for the fixtures. */
import { afterEach, describe, expect, it } from "vitest";
import { noop } from "lodash";
import { Line, Point } from "@opencop/elements";
import { ELEMENT_STORE_ID, ToolError, type ToolDescriptor } from "@opencop/tools";
import { registerTool } from "~/features/contributions/tool-registry";
import {
    attachStackedObjectSelectionSurface,
    useStackedObjectSelectionStore,
} from "~/features/map/stacked-object-selection.store";
import { useAreaSelectionStore } from "~/features/selection/area-selection.store";
import { getInteractionState, isInFlight } from "./interaction.store";
import {
    activate,
    beginDraft,
    canActivate,
    cancel,
    complete,
    draftCreated,
    isDraftPlaced,
    restart,
    save,
    setEditSub,
    setParams,
    startEdit,
} from "./commands";
import { currentSurface } from "../arbitration/surface";
import { attachHost } from "../host";
import {
    attachRuntime,
    fakeDeps,
    finishMeasurement,
    lineFixture,
    measureFixture,
    traceFixture,
    installInteractionRuntimeHarness,
} from "~/test/interaction-runtime-harness";

installInteractionRuntimeHarness();

describe("canActivate — one rule for every draw", () => {
    it("preempts a tool that is only waiting, refuses one with something in flight", () => {
        const deps = fakeDeps();
        const unconfigure = attachHost(deps.host);
        registerTool(lineFixture);
        registerTool(traceFixture(() => "exit"));

        // Waiting: nothing on the map, switching is free (as from the toolbar).
        expect(activate("fixtureLine")).toEqual({ ok: true });
        expect(canActivate("fixtureTrace")).toBe(true);
        expect(canActivate("fixtureLine")).toBe(true);

        // In flight: a placed vertex. Switching tools is not a way to abandon
        // it — Escape is — so every activation refuses, the same tool's too.
        const session = getInteractionState().session;
        const epoch = session.kind === "draw" ? session.epoch : -1;
        const element = Line.create({ positions: [0, 0] });
        draftCreated(element, epoch);
        beginDraft(element, epoch);
        expect(isInFlight(getInteractionState().session)).toBe(true);
        expect(canActivate("fixtureTrace")).toBe(false);
        expect(canActivate("fixtureLine")).toBe(false);
        expect(activate("fixtureTrace")).toMatchObject({ ok: false });
        // The refused activation left the in-flight draw exactly as it was.
        expect(getInteractionState().session).toMatchObject({
            kind: "draw",
            descriptorId: "fixtureLine",
            epoch,
        });
        expect(deps.held()).toBe(element);

        unconfigure();
    });
});

describe("setParams — atomic destroy-and-restart", () => {
    it("releases the draft, re-arms, and bumps the epoch in one command", () => {
        const deps = fakeDeps();
        const unconfigure = attachHost(deps.host);
        registerTool(lineFixture);

        activate("fixtureLine");
        const before = getInteractionState().session;
        const epoch = before.kind === "draw" ? before.epoch : -1;

        const element = Line.create({ positions: [0, 0] });
        draftCreated(element, epoch);
        beginDraft(element, epoch);
        expect(deps.held()).toBe(element);

        expect(setParams({ anything: 1 })).toEqual({ ok: true });

        const after = getInteractionState().session;
        expect(after.kind).toBe("draw");
        if (after.kind === "draw") {
            // One atomic step: draft gone, params applied, epoch bumped, no
            // dangling element left in custody or the store.
            expect(after.draft).toBeNull();
            expect(after.params).toEqual({ anything: 1 });
            expect(after.epoch).toBeGreaterThan(epoch);
            expect(after.locked).toBe(0);
        }
        expect(deps.held()).toBeUndefined();
        expect(deps.store.has(element.key)).toBe(false);

        unconfigure();
    });

    it("stale writes from a superseded epoch are refused", () => {
        const deps = fakeDeps();
        const unconfigure = attachHost(deps.host);
        registerTool(lineFixture);

        activate("fixtureLine");
        const first = getInteractionState().session;
        const staleEpoch = first.kind === "draw" ? first.epoch : -1;
        setParams({ restarted: true });

        const stale = Point.create({ position: [1, 1] });
        expect(draftCreated(stale, staleEpoch)).toMatchObject({ ok: false });
        const session = getInteractionState().session;
        if (session.kind === "draw") expect(session.draft).toBeNull();

        unconfigure();
    });
});

describe("complete — draw hands the element to an edit session under the same custody", () => {
    it("moves to EDIT with custody, holding the draft it placed", () => {
        const deps = fakeDeps();
        const unconfigure = attachHost(deps.host);
        registerTool(lineFixture);

        activate("fixtureLine");
        const session = getInteractionState().session;
        const epoch = session.kind === "draw" ? session.epoch : -1;
        const element = Line.create({ positions: [0, 0] });
        draftCreated(element, epoch);
        beginDraft(element, epoch);

        expect(complete()).toEqual({ ok: true });

        const after = getInteractionState().session;
        expect(after).toMatchObject({ kind: "edit", custody: true, isNew: true });
        // The session holds the element, not a key: it is the very object in
        // custody, which reaches the store only when saved.
        if (after.kind === "edit") expect(after.product).toBe(deps.held());
        expect(deps.mapManager.selectElement).toHaveBeenCalledWith(element);
        expect(deps.store.has(element.key)).toBe(false);

        unconfigure();
    });
});

describe("complete — a measure-class product is an edit session WITHOUT custody", () => {
    it("holds the finished draft as its element; the store, pipeline and selection are untouched", () => {
        const deps = fakeDeps();
        const unconfigure = attachHost(deps.host);
        registerTool(measureFixture);

        const draft = finishMeasurement(deps);

        const after = getInteractionState().session;
        expect(after).toMatchObject({
            kind: "edit",
            descriptorId: "fixtureMeasure",
            custody: false,
            isNew: true,
            params: { shape: "line" },
        });
        if (after.kind === "edit") expect(after.product).toBe(draft);
        expect(deps.store.size).toBe(0);
        expect(deps.held()).toBeUndefined();
        expect(deps.mapManager.selectElement).not.toHaveBeenCalled();
        // Nothing is placed any more: a later re-arm starts clean.
        expect(isDraftPlaced()).toBe(false);

        unconfigure();
    });

    it("restart drops the product and re-arms the same descriptor with the same params", () => {
        const deps = fakeDeps();
        const unconfigure = attachHost(deps.host);
        registerTool(measureFixture);
        finishMeasurement(deps);
        const before = getInteractionState().session;
        const epoch = before.kind === "edit" ? before.epoch : -1;

        expect(restart()).toEqual({ ok: true });

        const after = getInteractionState().session;
        expect(after).toMatchObject({
            kind: "draw",
            descriptorId: "fixtureMeasure",
            params: { shape: "line" },
            draft: null,
            locked: 0,
        });
        if (after.kind === "draw") expect(after.epoch).toBeGreaterThan(epoch);

        unconfigure();
    });

    it("setParams re-arms with the new params (measure's shape toggle over a finished measurement)", () => {
        const deps = fakeDeps();
        const unconfigure = attachHost(deps.host);
        registerTool(measureFixture);
        finishMeasurement(deps);

        expect(setParams({ shape: "circle" })).toEqual({ ok: true });

        expect(getInteractionState().session).toMatchObject({
            kind: "draw",
            params: { shape: "circle" },
            draft: null,
        });

        unconfigure();
    });

    it("cancel drops the product and leaves, writing nothing to the store", () => {
        const deps = fakeDeps();
        const unconfigure = attachHost(deps.host);
        registerTool(measureFixture);
        finishMeasurement(deps);

        expect(cancel()).toEqual({ ok: true });
        expect(getInteractionState().session.kind).toBe("idle");
        // No custody ⇒ nothing to unwind: no selection cleared, no store write.
        expect(deps.mapManager.handleEmptyClick).not.toHaveBeenCalled();
        expect(deps.store.size).toBe(0);

        unconfigure();
    });

    it("save over the product ends the session without committing anything", () => {
        const deps = fakeDeps();
        const unconfigure = attachHost(deps.host);
        registerTool(measureFixture);
        finishMeasurement(deps);

        // `save` means "the product is kept", not "the runtime persisted it".
        // With no custody there is nothing to commit, so the paired assertion
        // is the one that matters: the session ends and the store stays empty.
        expect(save()).toEqual({ ok: true });
        expect(getInteractionState().session.kind).toBe("idle");
        expect(deps.store.size).toBe(0);

        unconfigure();
    });

    it("activation is refused over the finished product, as over any edit", () => {
        const deps = fakeDeps();
        const unconfigure = attachHost(deps.host);
        registerTool(measureFixture);
        registerTool(lineFixture);
        finishMeasurement(deps);

        expect(canActivate("fixtureLine")).toBe(false);
        expect(canActivate("fixtureMeasure")).toBe(false);

        unconfigure();
    });

    it("a custody edit refuses restart and setParams — Save/Cancel resolve it", () => {
        const deps = fakeDeps();
        const unconfigure = attachHost(deps.host);
        registerTool(lineFixture);
        activate("fixtureLine");
        const session = getInteractionState().session;
        const epoch = session.kind === "draw" ? session.epoch : -1;
        const element = Line.create({ positions: [0, 0] });
        draftCreated(element, epoch);
        beginDraft(element, epoch);
        complete();

        expect(restart()).toMatchObject({ ok: false });
        expect(setParams({ x: 1 })).toMatchObject({ ok: false });
        expect(getInteractionState().session).toMatchObject({
            kind: "edit",
            custody: true,
        });

        unconfigure();
    });
});

describe("restart — Escape's in-session step", () => {
    it("aborts the draft but stays in the session", () => {
        const deps = fakeDeps();
        const unconfigure = attachHost(deps.host);
        registerTool(lineFixture);

        activate("fixtureLine");
        const session = getInteractionState().session;
        const epoch = session.kind === "draw" ? session.epoch : -1;
        const element = Line.create({ positions: [0, 0] });
        draftCreated(element, epoch);
        beginDraft(element, epoch);

        expect(restart()).toEqual({ ok: true });

        const after = getInteractionState().session;
        expect(after.kind).toBe("draw");
        if (after.kind === "draw") expect(after.draft).toBeNull();
        expect(deps.held()).toBeUndefined();

        unconfigure();
    });
});

describe("entering a session dismisses the click-disambiguation popover — that surface only", () => {
    const picked = () => Line.create({ positions: [0, 0, 1, 1] });
    const popover = () => useStackedObjectSelectionStore.getState();
    const lasso = () => useAreaSelectionStore.getState();

    // Dismissal is produced exactly as the app wires it: the popover declares
    // itself the open surface while its store holds elements, and the runtime's
    // composition point closes the open surface when a session starts — so the
    // REAL runtime is attached here, not the host alone.
    let deps: ReturnType<typeof fakeDeps>;
    let detach: () => void = noop;
    let unconfigure: () => void = noop;
    beforeEach(() => {
        deps = fakeDeps();
        unconfigure = attachRuntime(deps);
        detach = attachStackedObjectSelectionSurface();
    });
    afterEach(() => {
        detach();
        unconfigure();
        popover().dismiss();
        lasso().clear();
    });

    it("activate dismisses the popover and leaves a lasso selection standing", () => {
        registerTool(lineFixture);

        popover().open([picked()], { x: 10, y: 10 }, false);
        expect(activate("fixtureLine")).toEqual({ ok: true });
        expect(popover().elements).toBeNull();

        // The lasso selection is a group in the events panel whose members are
        // edited one at a time and returned to, so arming a tool over it must
        // leave it standing. They are separate stores, so this is structural.
        lasso().select([picked()], []);
        expect(activate("fixtureLine")).toEqual({ ok: true });
        expect(lasso().elements).toHaveLength(1);
    });

    it("startEdit dismisses the popover and leaves a lasso selection standing", () => {
        popover().open([picked()], { x: 10, y: 10 }, false);
        expect(startEdit(picked(), false)).toEqual({ ok: true });
        expect(popover().elements).toBeNull();

        lasso().select([picked()], []);
        expect(startEdit(picked(), false)).toEqual({ ok: true });
        expect(lasso().elements).toHaveLength(1);
    });

    it("a refused activation leaves the popover standing", () => {
        registerTool({
            id: "fixtureRefusing",
            label: "fixtureRefusing",
            icon: () => null,
            produces: { store: ELEMENT_STORE_ID, type: "point" },
            draw: {
                grammar: () => {
                    throw new ToolError("no drawable tool for these params");
                },
            },
        } satisfies ToolDescriptor);

        popover().open([picked()], { x: 10, y: 10 }, false);
        expect(activate("fixtureRefusing")).toMatchObject({ ok: false });

        // No session began, so nothing closed the surface — the popover
        // outlives a refusal the same way an in-flight draw does. `activate`
        // closes the open surface only after every refusal has passed.
        expect(popover().elements).toHaveLength(1);
    });

    it("closing the popover releases the hover suppression it imposed — through the runtime's hover policy, not its own code", () => {
        popover().open([picked()], { x: 10, y: 10 }, false);
        expect(deps.mapManager.suppressHoverClear).toHaveBeenLastCalledWith(true);
        expect(deps.mapManager.setHover).not.toHaveBeenCalled();

        // Whoever closed it — a row click, the ladder's rung, a pan — the slot
        // empties, ownership returns to ambient, and the applier releases the
        // suppression; the manager applies the clear the popover left standing.
        popover().dismiss();

        expect(deps.mapManager.suppressHoverClear).toHaveBeenLastCalledWith(false);
        expect(currentSurface()).toBeNull();
    });
});

describe("setEditSub — the hover hotspot", () => {
    it("keeps the session object when the hotspot and drag do not change", () => {
        const deps = fakeDeps();
        const unconfigure = attachHost(deps.host);
        registerTool(measureFixture);
        finishMeasurement(deps);

        setEditSub({ hotspot: "handle", dragging: null });
        const hovering = getInteractionState().session;
        // Hovering calls this on every mouse move: an unchanged sub is not a change.
        setEditSub({ hotspot: "handle", dragging: null });
        expect(getInteractionState().session).toBe(hovering);

        setEditSub({ hotspot: null, dragging: null });
        expect(getInteractionState().session).not.toBe(hovering);
        expect(getInteractionState().session).toMatchObject({
            kind: "edit",
            sub: { hotspot: null, dragging: null },
        });
        unconfigure();
    });
});
