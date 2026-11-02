// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { act, renderHook } from "~/test";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Point, PolylineGeometry } from "@opencop/elements";
import { type Session } from "~/features/map/interaction-runtime/session/interaction.store";
import { bumpRevForTest, resetInteractionForTest, setSessionForTest } from "~/test/interaction";
import { useScopedToolSession, useScopedToolSessionRevision } from "./use-tool-session.hook";

/**
 * The editor contract, pinned. The interaction runtime replaced the lifecycle
 * underneath this hook without changing it, so these characterize what an
 * editor is promised: every member keeps working while the implementation moves.
 * The view is scoped to one tool: another tool's session reads as idle and
 * cannot be changed through it.
 */

const activate = vi.fn();
const draftChanged = vi.fn();
const exit = vi.fn();
const cancel = vi.fn();
const setParams = vi.fn();

vi.mock("~/features/map/interaction-runtime/session/commands", () => ({
    activate: (...args: unknown[]) => activate(...args),
    exit: (...args: unknown[]) => exit(...args),
    cancel: (...args: unknown[]) => cancel(...args),
    draftChanged: () => draftChanged(),
    setParams: (...args: unknown[]) => setParams(...args),
}));

function editSession(custody: boolean): Session {
    return {
        kind: "edit",
        descriptorId: custody ? "pointTool" : "measureTool",
        epoch: 2,
        product: Point.create({ position: [0, 0] }),
        ref: { sourceId: "local", objectId: "x" },
        custody,
        params: {},
        isNew: true,
        sub: { hotspot: null, dragging: null },
    } as Session;
}

function drawSession(overrides: Partial<Extract<Session, { kind: "draw" }>> = {}): Session {
    return {
        kind: "draw",
        descriptorId: "pointTool",
        epoch: 1,
        params: {},
        custody: true,
        locked: 0,
        draft: null,
        ...overrides,
    } as Session;
}

beforeEach(() => {
    vi.clearAllMocks();
    act(() => resetInteractionForTest());
});
afterEach(() => act(() => resetInteractionForTest()));

describe("useScopedToolSession", () => {
    it("maps the session kind onto the phase an editor branches on", () => {
        const { result } = renderHook(() => useScopedToolSession("pointTool"));
        expect(result.current.phase).toBe("idle");

        act(() => setSessionForTest(drawSession()));
        expect(result.current.phase).toBe("draw");

        act(() => setSessionForTest(editSession(true)));
        expect(result.current.phase).toBe("edit");
    });

    it("a custody-less draw reports draw — waiting for its first click or with a draft", () => {
        // A measurement being taken is a draw session like a shape's; its
        // editor tells waiting from mid-draw by `draft` (null until the
        // draft exists), not by a phase of its own.
        const { result } = renderHook(() => useScopedToolSession("measureTool"));
        const waiting = drawSession({ descriptorId: "measureTool", custody: false });
        act(() => setSessionForTest(waiting));
        expect(result.current.phase).toBe("draw");
        expect(result.current.draft).toBeNull();

        const element = Point.create({ position: [0, 0] });
        act(() =>
            setSessionForTest({
                ...waiting,
                draft: { object: element, placed: true },
                locked: 1,
            } as Session),
        );
        expect(result.current.phase).toBe("draw");
        expect(result.current.draft).toBe(element);
    });

    it("a finished custody-less product reports edit — it is an edit session like any shape's", () => {
        const { result } = renderHook(() => useScopedToolSession("measureTool"));
        const session = editSession(false);
        act(() => setSessionForTest(session));
        expect(result.current.phase).toBe("edit");
        expect(result.current.draft).toBe(session.kind === "edit" ? session.product : null);
    });

    it("exposes the draft being drawn and re-renders when it changes", () => {
        const { result } = renderHook(() => useScopedToolSession("pointTool"));
        expect(result.current.draft).toBeNull();

        const element = Point.create({ position: [0, 0] });
        act(() => setSessionForTest(drawSession({ draft: { object: element, placed: false } })));

        expect(result.current.draft).toBe(element);
    });

    it("writes params through the atomic-restart command without subscribing the setter", () => {
        act(() => setSessionForTest(drawSession()));
        const { result } = renderHook(() => useScopedToolSession("pointTool"));
        const stableSetter = result.current.setParams;

        act(() => result.current.setParams({ sidc: "abc" }));

        expect(setParams).toHaveBeenCalledWith({ sidc: "abc" });
        // A stable setter is what lets an editor put it in a dep array without
        // re-creating every callback on each store change.
        act(() => setSessionForTest(drawSession({ params: { sidc: "abc" } })));
        expect(result.current.setParams).toBe(stableSetter);
    });

    it("redraws on invalidate through the session's revision, for an element draft or any other", () => {
        act(() => setSessionForTest(drawSession()));
        const { result } = renderHook(() => useScopedToolSession("pointTool"));
        const draft = { key: "m", type: "m:ruler", geometry: new PolylineGeometry([0, 0], 2) };

        act(() => result.current.invalidate(Point.create({ position: [1, 2] }), "getIcon"));
        act(() => result.current.invalidate(draft, "all"));

        expect(draftChanged).toHaveBeenCalledTimes(2);
    });

    it("arms its own tool, with params when given", () => {
        const { result } = renderHook(() => useScopedToolSession("graphicTool"));

        act(() => result.current.activate());
        expect(activate).toHaveBeenLastCalledWith("graphicTool", {});

        act(() => result.current.activate({ sidc: "abc" }));
        expect(activate).toHaveBeenLastCalledWith("graphicTool", { params: { sidc: "abc" } });
    });

    it("cancels its own edit, and leaves its own draw", () => {
        const { result } = renderHook(() => useScopedToolSession("pointTool"));

        act(() => setSessionForTest(editSession(true)));
        act(() => result.current.cancel());
        expect(cancel).toHaveBeenCalledOnce();

        act(() => setSessionForTest(drawSession()));
        act(() => result.current.cancel());
        expect(exit).toHaveBeenCalledOnce();
    });

    describe("while another tool owns the session", () => {
        const lassoDraw = () =>
            drawSession({
                descriptorId: "lassoTool",
                custody: false,
                params: { mode: "add" },
                draft: { object: Point.create({ position: [0, 0] }), placed: true },
            });

        it("reads as idle, with no draft and no params", () => {
            act(() => setSessionForTest(lassoDraw()));

            const { result } = renderHook(() => useScopedToolSession("measureTool"));

            expect(result.current.phase).toBe("idle");
            expect(result.current.draft).toBeNull();
            expect(result.current.params).toEqual({});
        });

        it("cannot change that session", () => {
            act(() => setSessionForTest(lassoDraw()));
            const { result } = renderHook(() => useScopedToolSession("measureTool"));

            act(() => {
                result.current.setParams({ shape: "circle" });
                result.current.invalidate(Point.create({ position: [0, 0] }), "all");
                result.current.cancel();
            });

            expect(setParams).not.toHaveBeenCalled();
            expect(draftChanged).not.toHaveBeenCalled();
            expect(cancel).not.toHaveBeenCalled();
            expect(exit).not.toHaveBeenCalled();
        });

        it("cannot arm its own tool over that session", () => {
            act(() => setSessionForTest(lassoDraw()));
            const { result } = renderHook(() => useScopedToolSession("measureTool"));

            act(() => result.current.activate({ shape: "circle" }));

            expect(activate).not.toHaveBeenCalled();
        });
    });

    it("re-arms its own tool while it owns the session", () => {
        act(() => setSessionForTest(drawSession({ descriptorId: "graphicTool" })));
        const { result } = renderHook(() => useScopedToolSession("graphicTool"));

        act(() => result.current.activate({ sidc: "abc" }));

        expect(activate).toHaveBeenCalledWith("graphicTool", { params: { sidc: "abc" } });
    });

    it("checks ownership when a command runs, not when it was read", () => {
        act(() => setSessionForTest(drawSession({ descriptorId: "measureTool", custody: false })));
        const { result } = renderHook(() => useScopedToolSession("measureTool"));
        const captured = result.current.setParams;

        // The session ends (the tool was unregistered, or the operator left it).
        act(() => resetInteractionForTest());
        captured({ shape: "circle" });

        expect(setParams).not.toHaveBeenCalled();
    });

    it("checks ownership when activate runs — a kept activate cannot preempt a later session", () => {
        const { result } = renderHook(() => useScopedToolSession("measureTool"));
        const captured = result.current.activate;

        // Another tool arms while the closure is held (a timer, a pending promise).
        act(() => setSessionForTest(drawSession({ descriptorId: "lassoTool", custody: false })));
        captured();

        expect(activate).not.toHaveBeenCalled();
    });
});

describe("useScopedToolSessionRevision", () => {
    it("follows the geometry fence while its tool owns the session", () => {
        act(() => setSessionForTest(drawSession()));
        const { result } = renderHook(() => useScopedToolSessionRevision("pointTool"));
        const before = result.current;

        act(() => bumpRevForTest());

        expect(result.current).toBe(before + 1);
    });

    it("reads 0 while another tool owns the session", () => {
        act(() => setSessionForTest(drawSession({ descriptorId: "lassoTool" })));
        const { result } = renderHook(() => useScopedToolSessionRevision("pointTool"));

        act(() => bumpRevForTest());

        expect(result.current).toBe(0);
    });
});
