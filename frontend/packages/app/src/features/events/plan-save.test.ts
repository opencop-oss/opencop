// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, test } from "vitest";
import { localEventSource } from "@opencop/elements";
import { planSave, type SaveAction } from "./plan-save";
import type { EventMutationTarget } from "~/features/map/editability";

const layerTarget: EventMutationTarget = { kind: "layer", id: "layer-1" };
const sourceTarget: EventMutationTarget = { kind: "source", id: "ds-1" };

describe("planSave", () => {
    test.each<{
        name: string;
        originalSource: string;
        objectId: string | undefined;
        broadcastTarget: string | undefined;
        mutationTarget: EventMutationTarget | undefined;
        expected: SaveAction;
    }>([
        {
            name: "no broadcast target → local",
            originalSource: localEventSource,
            objectId: "client-uuid-1",
            broadcastTarget: undefined,
            mutationTarget: undefined,
            expected: { kind: "local" },
        },
        {
            name: "broadcast target is the local source → local",
            originalSource: localEventSource,
            objectId: "client-uuid-1",
            broadcastTarget: localEventSource,
            mutationTarget: undefined,
            expected: { kind: "local" },
        },
        {
            name: "local origin broadcast to a layer → create (regression case)",
            originalSource: localEventSource,
            objectId: "client-uuid-1",
            broadcastTarget: "layer-1",
            mutationTarget: undefined,
            expected: { kind: "create", targetLayer: "layer-1" },
        },
        {
            name: "no object id yet → create, never patch (defensive)",
            originalSource: "layer-1",
            objectId: undefined,
            broadcastTarget: "layer-1",
            mutationTarget: layerTarget,
            expected: { kind: "create", targetLayer: "layer-1" },
        },
        {
            name: "existing layer event → patch (layer)",
            originalSource: "layer-1",
            objectId: "obj-1",
            broadcastTarget: "layer-1",
            mutationTarget: layerTarget,
            expected: { kind: "patch", target: layerTarget, objectId: "obj-1" },
        },
        {
            name: "existing data-source event → patch (source)",
            originalSource: "ds-1",
            objectId: "obj-1",
            broadcastTarget: "ds-1",
            mutationTarget: sourceTarget,
            expected: { kind: "patch", target: sourceTarget, objectId: "obj-1" },
        },
    ])("$name", ({ originalSource, objectId, broadcastTarget, mutationTarget, expected }) => {
        expect(planSave(originalSource, objectId, broadcastTarget, mutationTarget)).toEqual(
            expected,
        );
    });

    describe("move", () => {
        test("an existing layer event retargeted with move → move", () => {
            expect(planSave("layer-1", "obj-1", "layer-2", layerTarget, true)).toEqual({
                kind: "move",
                fromLayer: "layer-1",
                objectId: "obj-1",
                targetLayer: "layer-2",
            });
        });

        test("a data-source event can never move — its identity belongs to the feed", () => {
            // The UI gates moves on canBroadcast, which data-source events never
            // hold; the decision throws rather than forking the event while the
            // upstream feed re-populates the deleted original.
            expect(() => planSave("ds-1", "obj-1", "layer-2", sourceTarget, true)).toThrow(
                "Data-source events cannot be moved",
            );
        });

        test("moving to the source it already lives on is a patch, not a delete+create", () => {
            expect(planSave("layer-1", "obj-1", "layer-1", layerTarget, true)).toEqual({
                kind: "patch",
                target: layerTarget,
                objectId: "obj-1",
            });
        });

        test("retargeting without the move opt-in still patches in place", () => {
            // The form locks the field unless move is ticked; this pins that a
            // stale target can never silently relocate the event.
            expect(planSave("layer-1", "obj-1", "layer-2", layerTarget, false)).toEqual({
                kind: "patch",
                target: layerTarget,
                objectId: "obj-1",
            });
        });

        test("a local element with move set still creates — there is nothing to move from", () => {
            expect(planSave(localEventSource, "client-uuid-3", "layer-2", undefined, true)).toEqual(
                {
                    kind: "create",
                    targetLayer: "layer-2",
                },
            );
        });

        test("a move with no mutation target throws instead of orphaning the old copy", () => {
            expect(() => planSave("layer-1", "obj-1", "layer-2", undefined, true)).toThrow(
                "Missing mutation target for remote event",
            );
        });
    });

    test("a remote event with an empty target throws instead of silently going local", () => {
        // A cleared combobox emits "" — resolving that to a local save would
        // re-key the event on this client while the server copy lives on.
        expect(() => planSave("layer-1", "obj-1", "", layerTarget)).toThrow(
            "Remote event has no broadcast target",
        );
    });

    test("a remote element that resolves to patch but has no mutation target throws", () => {
        expect(() => planSave("layer-1", "obj-1", "layer-1", undefined)).toThrow(
            "Missing mutation target for remote event",
        );
    });

    test("keys create-vs-patch off the original source, not the chosen target", () => {
        // Origin is local and the user picked an existing layer as the target, with no
        // mutationTarget (editability returns none for a local element). Keying the
        // decision off the chosen target would mis-route this to a patch and throw;
        // keying off the origin correctly creates.
        expect(planSave(localEventSource, "client-uuid-2", "layer-9", undefined)).toEqual({
            kind: "create",
            targetLayer: "layer-9",
        });
    });
});
