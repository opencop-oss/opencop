// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ElementStore } from "@opencop/ingest";
import { Point } from "@opencop/elements";
import type { Element } from "@opencop/elements";

function makePoint(
    objectId: string,
    stale: number = Date.now() + 60_000,
    source: string = "local",
) {
    return Point.create({
        position: [0, 0],
        meta: { objectId, stale, source },
    });
}

describe("ElementStore", () => {
    let store: ElementStore;

    beforeEach(() => {
        vi.useFakeTimers();
        store = new ElementStore();
    });

    afterEach(() => {
        store.dispose();
        vi.useRealTimers();
    });

    describe("add / get / getAll", () => {
        it("should add an element and retrieve it by id", () => {
            const el = makePoint("e1");
            store.add(el);

            expect(store.get(el.key)).toBe(el);
        });

        it("should replace an element with the same objectId", () => {
            const el1 = makePoint("e1");
            const el2 = makePoint("e1");
            store.add(el1);
            store.add(el2);

            expect(store.get(el2.key)).toBe(el2);
            expect([...store.getByType("point")!]).toHaveLength(1);
        });

        it("should announce a replaced element as an update, not an add", () => {
            const add = vi.fn();
            const update = vi.fn();
            store.on("add", add);
            store.on("update", update);
            const el1 = makePoint("e1");
            const el2 = makePoint("e1");

            store.add(el1);
            store.add(el2);

            expect(add).toHaveBeenCalledTimes(1);
            expect(add).toHaveBeenCalledWith([el1]);
            expect(update).toHaveBeenCalledWith([el2]);
        });

        it("should retire the previous draft when another is set", () => {
            const stored = makePoint("e1");
            store.add(stored);
            const remove = vi.fn();
            const update = vi.fn();
            store.on("remove", remove);
            store.on("update", update);
            const fresh = makePoint("d1");

            store.setDraft(fresh);
            store.setDraft(makePoint("e1"));
            expect(remove).toHaveBeenCalledWith([fresh]);

            store.setDraft(makePoint("d2"));
            expect(update).toHaveBeenLastCalledWith([stored]);
        });

        it("should keep elements with the same objectId from different sources", () => {
            const el1 = makePoint("e1", Date.now() + 60_000, "layer-a");
            const el2 = makePoint("e1", Date.now() + 60_000, "layer-b");
            store.add(el1);
            store.add(el2);

            expect([...store.getByType("point")!]).toHaveLength(2);
            expect([...(store.getBySource("layer-a") ?? [])]).toEqual([el1]);
            expect([...(store.getBySource("layer-b") ?? [])]).toEqual([el2]);
        });

        it("should hold back updates to a drafted element and announce the latest when the draft clears", () => {
            const el = makePoint("e1");
            store.add(el);
            store.setDraft(el);
            const added = vi.fn();
            const updated = vi.fn();
            store.on("add", added);
            store.on("update", updated);

            store.add(makePoint("e1", Date.now() + 100_000));
            const newestEl = makePoint("e1", Date.now() + 200_000);
            store.add(newestEl);
            expect(added).not.toHaveBeenCalled();
            expect(store.getDraft()).toBe(el);

            store.clearDraft();
            expect(updated).toHaveBeenLastCalledWith([newestEl]);
        });

        it("should keep announcing a drafted element's style changes after its style is replaced", () => {
            const draft = makePoint("e1");
            store.setDraft(draft);
            const style = draft.getStyle().clone();
            draft.setStyle(style);
            const updated = vi.fn();
            store.on("update", updated);

            style.setColor("#ff0000ff");

            expect(updated).toHaveBeenCalledWith([draft]);
        });

        it("should stop announcing a drafted element's changes once the draft clears", () => {
            const el = makePoint("e1");
            store.add(el);
            store.setDraft(el);
            store.clearDraft();
            const updated = vi.fn();
            store.on("update", updated);

            el.geometry.setPosition([1, 1]);
            expect(updated).not.toHaveBeenCalled();
        });
    });

    describe("stale filtering", () => {
        it("should reject elements that are already stale", () => {
            const el = makePoint("e1", Date.now() - 1);
            store.add(el);
            vi.advanceTimersByTime(0);

            expect(store.get(el.key)).toBeUndefined();
        });

        it("should hold back the removal of a drafted element until the draft clears", () => {
            const el = makePoint("e1");
            store.add(el);
            store.setDraft(el);
            const removed = vi.fn();
            store.on("remove", removed);

            store.add(makePoint("e1", Date.now() - 100_000));
            expect(removed).not.toHaveBeenCalled();
            expect(store.getDraft()).toBe(el);

            store.clearDraft();
            expect(removed).toHaveBeenCalledWith([el]);
            expect(store.get(el.key)).toBeUndefined();
        });

        it("should expire a drafted element only once the draft clears", () => {
            const el = makePoint("e1", Date.now() + 1_000);
            store.add(el);
            store.setDraft(el);
            const removed = vi.fn();
            const expired = vi.fn();
            store.on("remove", removed);
            store.on("expire", expired);

            vi.advanceTimersByTime(2_000);
            expect(store.get(el.key)).toBe(el);
            expect(expired).not.toHaveBeenCalled();

            store.clearDraft();
            expect(removed).toHaveBeenCalledWith([el]);
            expect(expired).toHaveBeenCalledWith([el]);
            expect(store.get(el.key)).toBeUndefined();
        });

        it("should remove a drafted element that is stale when committed", () => {
            const el = makePoint("e1", Date.now() + 1_000);
            store.add(el);
            store.setDraft(el);
            const updated = vi.fn();
            const removed = vi.fn();
            store.on("update", updated);
            store.on("remove", removed);

            vi.advanceTimersByTime(2_000);
            store.commitDraft();

            expect(updated).not.toHaveBeenCalled();
            expect(removed).toHaveBeenCalledExactlyOnceWith([el]);
            expect(store.get(el.key)).toBeUndefined();
        });

        it("should reject elements with stale exactly equal to Date.now()", () => {
            const el = makePoint("e1", Date.now());
            store.add(el);
            vi.advanceTimersByTime(0);

            expect(store.get(el.key)).toBeUndefined();
        });

        it("should remove an existing element when re-added with an already-stale timestamp", () => {
            const el = makePoint("e1", Date.now() + 60_000);
            store.add(el);
            expect(store.get(el.key)).toBe(el);

            // Simulate a delete broadcast: same objectId, stale set to now
            const staleEl = makePoint("e1", Date.now());
            store.add(staleEl);

            expect(store.get(el.key)).toBeUndefined();
            expect([...(store.getByType("point") ?? [])]).toHaveLength(0);
        });

        it("should fire 'remove' listener when existing element is replaced by a stale one", () => {
            const listener = vi.fn();
            store.on("remove", listener);

            const el = makePoint("e1", Date.now() + 60_000);
            store.add(el);

            const staleEl = makePoint("e1", Date.now());
            store.add(staleEl);

            expect(listener).toHaveBeenCalledWith([el]);
        });
    });

    describe("stale timeout", () => {
        it("should auto-delete an element when its stale time elapses", () => {
            const el = makePoint("e1", Date.now() + 5_000);
            store.add(el);

            expect(store.get(el.key)).toBe(el);

            vi.advanceTimersByTime(5_000);

            expect(store.get(el.key)).toBeUndefined();
        });

        it("fires 'expire' (not just 'remove') when the sweep evicts a stale element, so owners can prune", () => {
            const onExpire = vi.fn();
            store.on("expire", onExpire);

            const el = makePoint("e1", Date.now() + 5_000);
            store.add(el);
            vi.advanceTimersByTime(5_000);

            expect(onExpire).toHaveBeenCalledTimes(1);
            expect(onExpire.mock.calls[0][0]).toContainEqual(el);
        });

        it("does NOT fire 'expire' when a source is evicted via deleteBySource", () => {
            const onExpire = vi.fn();
            store.on("expire", onExpire);

            const el = makePoint("e1", Date.now() + 60_000);
            store.add(el);
            store.deleteBySource(el.source);

            expect(store.get(el.key)).toBeUndefined();
            expect(onExpire).not.toHaveBeenCalled();
        });

        it("should reset the timeout when the same element is re-added", () => {
            const el1 = makePoint("e1", Date.now() + 5_000);
            store.add(el1);

            vi.advanceTimersByTime(3_000);

            // Re-add with a fresh stale time
            const el2 = makePoint("e1", Date.now() + 10_000);
            store.add(el2);

            // Original timeout (5s) would fire here, but it was cleared
            vi.advanceTimersByTime(3_000);
            expect(store.get(el2.key)).toBe(el2);

            // New timeout (10s from re-add) fires
            vi.advanceTimersByTime(7_001);
            expect(store.get(el2.key)).toBeUndefined();
        });

        it("should clamp timeout to MAX_INT32", () => {
            const farFuture = Date.now() + 0x7fffffff + 100_000;
            const el = makePoint("e1", farFuture);
            store.add(el);

            // Should not have been deleted yet (clamped at MAX_INT32)
            vi.advanceTimersByTime(0x7fffffff - 1);
            expect(store.get(el.key)).toBe(el);
        });
    });

    // A single store must hold live and historical elements at once: live rows
    // expire on their stale time, historical snapshots never do (every chunk's
    // version coexists so the playhead can scrub across them).
    describe("per-element stale exemption (elementClass)", () => {
        function makeHistoricalPoint(id: string, stale: number, source = "layer-a") {
            return Point.create({
                position: [0, 0],
                meta: {
                    objectId: `obj-${id}`,
                    // Historical ids carry a ":<chunkIndex>" suffix, keeping their
                    // keys in a namespace live objectIds never occupy.
                    id: `${id}:0`,
                    stale,
                    source,
                    elementClass: "historical",
                },
            });
        }

        it("should sweep a live element whose stale time has passed", () => {
            const el = makePoint("live-1", Date.now() - 1);
            store.add(el);
            vi.advanceTimersByTime(0);

            expect(store.get(el.key)).toBeUndefined();
        });

        it("should retain a historical element whose stale time has passed", () => {
            const el = makeHistoricalPoint("hist-1", Date.now() - 1);
            store.add(el);
            vi.advanceTimersByTime(0);

            expect(store.get(el.key)).toBe(el);
        });

        it("should not evict historical elements when the sweep runs for live ones", () => {
            const live = makePoint("live-2", Date.now() + 5_000);
            const historical = makeHistoricalPoint("hist-2", Date.now() - 1);
            store.add([live, historical]);

            // Advance past the live element's stale time — its sweep must not
            // take the historical element with it.
            vi.advanceTimersByTime(5_000);

            expect(store.get(live.key)).toBeUndefined();
            expect(store.get(historical.key)).toBe(historical);
        });

        it("should hold live and historical versions of the same entity under distinct keys", () => {
            const live = makePoint("shared", Date.now() + 60_000, "layer-a");
            const historical = makeHistoricalPoint("shared", Date.now() - 1, "layer-a");
            store.add([live, historical]);

            expect(live.key).not.toBe(historical.key);
            expect(store.get(live.key)).toBe(live);
            expect(store.get(historical.key)).toBe(historical);
            expect([...store.getByType("point")]).toHaveLength(2);
        });
    });

    describe("delete", () => {
        it("should remove an element by id", () => {
            const el = makePoint("e1");
            store.add(el);
            store.delete(el.key);

            expect(store.get(el.key)).toBeUndefined();
            expect([...(store.getByType("point") ?? [])]).toHaveLength(0);
        });

        it("should clear the stale timeout on delete", () => {
            const el = makePoint("e1", Date.now() + 5_000);
            store.add(el);
            store.delete(el.key);

            // Advancing past stale time should not cause errors
            vi.advanceTimersByTime(10_000);
            expect(store.get(el.key)).toBeUndefined();
        });
    });

    describe("element type change", () => {
        it("should move element from old type array to new type array when type changes", () => {
            const el1 = {
                type: "typeA",
                key: "src e1",
                objectId: "e1",
                source: "src",
                stale: Date.now() + 60_000,
            } as unknown as Element;

            const el2 = {
                type: "typeB",
                key: "src e1",
                objectId: "e1",
                source: "src",
                stale: Date.now() + 60_000,
            } as unknown as Element;

            store.add([el1]);
            expect([...store.getByType("typeA")!]).toHaveLength(1);

            store.add([el2]);
            // Old type array should be empty, new type array should have the element
            expect([...(store.getByType("typeA") ?? [])]).toHaveLength(0);
            expect([...store.getByType("typeB")!]).toHaveLength(1);
        });
    });

    describe("event listeners", () => {
        it("should fire 'add' listener when an element is added", () => {
            const listener = vi.fn();
            store.on("add", listener);

            const el = makePoint("e1");
            store.add(el);

            expect(listener).toHaveBeenCalledWith([el]);
        });

        it("should fire 'remove' listener when an element is deleted", () => {
            const listener = vi.fn();
            store.on("remove", listener);

            const el = makePoint("e1");
            store.add(el);
            store.delete(el.key);

            expect(listener).toHaveBeenCalledWith([el]);
        });

        it("should not fire 'remove' for unknown ids", () => {
            const listener = vi.fn();
            store.on("remove", listener);

            store.delete("nonexistent");

            expect(listener).not.toHaveBeenCalled();
        });

        it("should unsubscribe with off()", () => {
            const listener = vi.fn();
            store.on("add", listener);

            store.off("add", listener);

            store.add(makePoint("e1"));
            expect(listener).not.toHaveBeenCalled();
        });

        it("should only unsubscribe the specific listener", () => {
            const listener1 = vi.fn();
            const listener2 = vi.fn();
            store.on("add", listener1);
            store.on("add", listener2);

            store.off("add", listener1);

            store.add(makePoint("e1"));
            expect(listener1).not.toHaveBeenCalled();
            expect(listener2).toHaveBeenCalled();
        });
    });

    describe("dispose", () => {
        it("should clear all pending timeouts", () => {
            store.add(makePoint("e1", Date.now() + 5_000));
            store.add(makePoint("e2", Date.now() + 5_000));

            store.dispose();

            // Timeouts should not fire after dispose
            vi.advanceTimersByTime(10_000);
            // dispose clears all store state alongside timers.
            expect(store.get("e1")).toBeUndefined();
            expect(store.get("e2")).toBeUndefined();
        });
    });
});
