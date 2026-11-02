// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { QueryClient } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";
import { groupMutations, groupQueries } from "./groups.queries";
import type { Group } from "./groups.queries";

const GROUP: Group = {
    id: "g1",
    name: "Recon Team",
    description: null,
    createdAt: "2026-01-01T00:00:00Z",
    source: "OPENCOP",
};

const CONTEXT = { meta: undefined };

/**
 * The assign pickers read `searches()`, a sibling of `lists()`. Any mutation that changes a group's
 * identity has to invalidate both. Invalidating `lists()` alone leaves an open picker showing a
 * stale name, or a deleted group, until `staleTime` expires.
 */
describe("groupMutations: assign-picker pool invalidation", () => {
    function spyOnClient() {
        const queryClient = new QueryClient({
            defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
        });
        return { queryClient, spy: vi.spyOn(queryClient, "invalidateQueries") };
    }

    it("invalidates the picker pool when a group is created", () => {
        const { queryClient, spy } = spyOnClient();

        groupMutations.create(queryClient).onSuccess?.(GROUP, { name: GROUP.name }, undefined, {
            ...CONTEXT,
            client: queryClient,
        });

        expect(spy).toHaveBeenCalledWith({ queryKey: groupQueries.searches() });
    });

    it("invalidates the picker pool when a group is renamed", () => {
        const { queryClient, spy } = spyOnClient();

        // Name is the only field the picker renders, so a rename is the visible case.
        groupMutations
            .patch(queryClient)
            .onSuccess?.(GROUP, { groupId: GROUP.id, name: "Renamed" }, undefined, {
                ...CONTEXT,
                client: queryClient,
            });

        expect(spy).toHaveBeenCalledWith({ queryKey: groupQueries.searches() });
    });

    it("invalidates the picker pool when a group is deleted", () => {
        const { queryClient, spy } = spyOnClient();

        groupMutations.delete(queryClient).onSuccess?.(undefined, GROUP.id, undefined, {
            ...CONTEXT,
            client: queryClient,
        });

        expect(spy).toHaveBeenCalledWith({ queryKey: groupQueries.searches() });
    });

    it("leaves the pool alone when only membership changes", () => {
        const { queryClient, spy } = spyOnClient();

        // Membership doesn't change a group's id or name, so the pool is unaffected.
        groupMutations
            .patchMembers(queryClient)
            .onSuccess?.(undefined, { groupId: GROUP.id, add: ["u1"] }, undefined, {
                ...CONTEXT,
                client: queryClient,
            });

        expect(spy).not.toHaveBeenCalledWith({ queryKey: groupQueries.searches() });
    });
});
