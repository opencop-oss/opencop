// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { beforeEach, describe, expect, it, vi } from "vitest";
import { createSourceControllerStore } from "./source-controller.store";
import { Source } from "./sources/Source";

// ---------------------------------------------------------------------------
// Test helpers
// ---------------------------------------------------------------------------

class FakeSource extends Source {
    startCalls = 0;
    stopCalls = 0;

    protected onEnable(): void {
        this.startCalls++;
        this.setState("enabled");
    }

    protected onDisable(): void {
        this.stopCalls++;
    }
}

/** Shorthand to access Zustand store state + actions. */
const ctrl = (purgeSource = vi.fn()) => ({
    controller: createSourceControllerStore({ purgeSource }),
    purgeSource,
});

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("SourceController", () => {
    beforeEach(() => {
        localStorage.clear();
    });

    describe("reconcile (single slice)", () => {
        it("admits new sources; enable starts them", () => {
            const { controller } = ctrl();
            const s1 = new FakeSource("s1");
            const s2 = new FakeSource("s2");

            controller.getState().reconcile("live", [s1, s2]);
            [s1, s2].forEach((source) => controller.getState().enable(source.id));

            expect(s1.startCalls).toBe(1);
            expect(s2.startCalls).toBe(1);
            expect(controller.getState().sourceStates).toEqual({ s1: "enabled", s2: "enabled" });
        });

        it("evicts sources dropped from the slice", () => {
            const { controller } = ctrl();
            const s1 = new FakeSource("s1");
            const s2 = new FakeSource("s2");

            controller.getState().reconcile("live", [s1, s2]);
            [s1, s2].forEach((source) => controller.getState().enable(source.id));
            controller.getState().reconcile("live", [s1]);

            expect(s2.stopCalls).toBe(1);
            expect(controller.getState().sourceStates["s2"]).toBe(undefined);
        });

        it("keeps an unchanged source untouched (not re-admitted, not evicted)", () => {
            const { controller } = ctrl();
            const s1 = new FakeSource("s1");

            controller.getState().reconcile("live", [s1]);
            controller.getState().enable("s1");
            controller.getState().reconcile("live", [s1]);

            expect(s1.startCalls).toBe(1);
            expect(s1.stopCalls).toBe(0);
        });

        it("keeps the existing instance on an id match, dropping the incoming one", () => {
            const { controller } = ctrl();
            const first = new FakeSource("s1");
            const second = new FakeSource("s1"); // same id, different instance

            controller.getState().reconcile("live", [first]);
            controller.getState().enable("s1");
            controller.getState().reconcile("live", [second]);

            // The original instance is retained; the incoming duplicate never starts.
            expect(first.startCalls).toBe(1);
            expect(first.stopCalls).toBe(0);
            expect(second.startCalls).toBe(0);
        });

        it("evicts every source when reconciled to an empty slice", () => {
            const { controller, purgeSource } = ctrl();
            const s1 = new FakeSource("layer-1");
            const s2 = new FakeSource("local");

            controller.getState().reconcile("live", [s1, s2]);
            [s1, s2].forEach((source) => controller.getState().enable(source.id));
            controller.getState().reconcile("live", []);

            expect(s1.stopCalls).toBe(1);
            expect(s2.stopCalls).toBe(1);
            expect(purgeSource).toHaveBeenCalledWith("layer-1");
            expect(purgeSource).toHaveBeenCalledWith("local");
        });

        it("purges only the dropped source when the slice shrinks", () => {
            const { controller, purgeSource } = ctrl();
            const s1 = new FakeSource("layer-1");
            const s2 = new FakeSource("layer-2");

            controller.getState().reconcile("live", [s1, s2]);
            controller.getState().reconcile("live", [s1]);

            expect(purgeSource).toHaveBeenCalledTimes(1);
            expect(purgeSource).toHaveBeenCalledWith("layer-2");
        });

        it("does not purge when a slice first grows from empty", () => {
            const { controller, purgeSource } = ctrl();
            controller.getState().reconcile("live", [new FakeSource("layer-1")]);
            expect(purgeSource).not.toHaveBeenCalled();
        });

        // A source's elements are not always stored under its own id — a historical
        // source is keyed by session while its elements carry the subject id they
        // came from. Purging must follow `ownedSourceIds`.
        it("purges every id a source declares it owns, not just its own id", () => {
            const { controller, purgeSource } = ctrl();

            class MultiSubjectSource extends FakeSource {
                override get ownedSourceIds(): string[] {
                    return ["subject-a", "subject-b"];
                }
            }

            controller.getState().reconcile("historical", [new MultiSubjectSource("history:s1")]);
            controller.getState().reconcile("historical", []);

            expect(purgeSource).toHaveBeenCalledWith("subject-a");
            expect(purgeSource).toHaveBeenCalledWith("subject-b");
            expect(purgeSource).not.toHaveBeenCalledWith("history:s1");
        });
    });

    // The point of named slices: features contribute independently and neither can
    // evict the other. This is what lets the live sources (near the router) and the
    // historical session (inside the map view) share one controller.
    describe("reconcile (multiple slices)", () => {
        it("unions slices; a slice only evicts its own sources", () => {
            const { controller } = ctrl();
            const live = new FakeSource("live-1");
            const hist = new FakeSource("hist-1");

            controller.getState().reconcile("live", [live]);
            controller.getState().reconcile("historical", [hist]);
            controller.getState().enable("live-1");
            controller.getState().enable("hist-1");

            // Withdraw only the historical slice.
            controller.getState().reconcile("historical", []);

            expect(hist.stopCalls).toBe(1);
            expect(controller.getState().sourceStates["hist-1"]).toBe(undefined);
            // The live slice is untouched.
            expect(live.stopCalls).toBe(0);
            expect(controller.getState().sourceStates["live-1"]).toBe("enabled");
        });

        it("re-reconciling one slice does not evict another slice's sources", () => {
            const { controller } = ctrl();
            const live = new FakeSource("live-1");
            const hist = new FakeSource("hist-1");

            controller.getState().reconcile("live", [live]);
            controller.getState().reconcile("historical", [hist]);

            // A no-op-ish reconcile of the live slice must leave historical alone.
            controller.getState().reconcile("live", [live]);

            expect(hist.stopCalls).toBe(0);
            expect(controller.getState().sourceStates["hist-1"]).toBe("disabled");
        });

        it("keeps the first instance and warns when two slices contribute the same id", () => {
            const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
            const { controller } = ctrl();
            const a = new FakeSource("dup");
            const b = new FakeSource("dup");

            controller.getState().reconcile("live", [a]);
            controller.getState().enable("dup");
            controller.getState().reconcile("historical", [b]);

            // `a` (first-seen) is retained; `b` never starts.
            expect(a.startCalls).toBe(1);
            expect(b.startCalls).toBe(0);
            expect(b.stopCalls).toBe(0);
            warn.mockRestore();
        });
    });

    describe("enable / disable", () => {
        it("enable starts an admitted source; disable stops and purges it", () => {
            const { controller, purgeSource } = ctrl();
            const source = new FakeSource("s1");

            controller.getState().reconcile("live", [source]);
            controller.getState().enable("s1");
            expect(source.state).toBe("enabled");

            controller.getState().disable("s1");
            expect(source.state).toBe("disabled");
            expect(source.stopCalls).toBe(1);
            expect(purgeSource).toHaveBeenCalledWith("s1");
        });

        it("re-enable after disable starts it again", () => {
            const { controller } = ctrl();
            const source = new FakeSource("s1");

            controller.getState().reconcile("live", [source]);
            controller.getState().disable("s1");
            controller.getState().enable("s1");

            expect(controller.getState().sourceStates.s1).toBe("enabled");
        });

        it("enable / disable are no-ops for unknown ids", () => {
            const { controller } = ctrl();
            expect(() => controller.getState().enable("nope")).not.toThrow();
            expect(() => controller.getState().disable("nope")).not.toThrow();
        });
    });

    describe("initial state", () => {
        it("starts with empty states", () => {
            expect(ctrl().controller.getState().sourceStates).toEqual({});
        });

        it("an admitted-but-not-enabled source reads as disabled", () => {
            const { controller } = ctrl();
            controller.getState().reconcile("live", [new FakeSource("s1")]);
            expect(controller.getState().sourceStates).toEqual({ s1: "disabled" });
        });
    });
});
