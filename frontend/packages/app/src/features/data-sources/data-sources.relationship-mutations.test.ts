// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MyDataSource } from "@opencop/api";
import { QueryClient } from "@tanstack/react-query";
import { describe, expect, it } from "vitest";
import { userQueries } from "../users/users.queries";
import { dataSourceMutations, dataSourceQueries } from "./data-sources.queries";

type SuccessHandler = (data: unknown, variables: unknown) => void;

const mySource = (overrides: Partial<MyDataSource>): MyDataSource => ({
    role: "VIEWER",
    directRole: null,
    serverId: "source-1",
    serverName: "Source",
    serverState: "CONNECTED",
    via: "DIRECT",
    access: "R",
    transportType: "TCP",
    defaultVisible: true,
    ...overrides,
});

describe("data source relationship mutation cache coherence", () => {
    it("does not automatically retry a panel-scoped connection detail request", () => {
        expect(dataSourceQueries.detail("source-1").retry).toBe(false);
    });

    it.each([
        ["addLayers", { serverId: "source-1", layerIds: ["layer-1"], role: "ADMIN" }],
        ["deleteLayer", { serverId: "source-1", layerId: "layer-1" }],
    ] as const)("%s invalidates the effective source list", (mutationName, variables) => {
        const queryClient = new QueryClient();
        const myListKey = dataSourceQueries.myList().queryKey;
        queryClient.setQueryData(myListKey, []);
        const mutation = dataSourceMutations[mutationName](queryClient);

        (mutation.onSuccess as SuccessHandler)(undefined, variables);

        expect(queryClient.getQueryState(myListKey)?.isInvalidated).toBe(true);
    });

    it.each([
        ["addUsers", { serverId: "source-1", userId: ["user-1"], role: "OWNER" }],
        ["deleteUser", { serverId: "source-1", userId: "user-1" }],
    ] as const)(
        "%s invalidates active and archived effective source lists",
        (mutationName, variables) => {
            const queryClient = new QueryClient();
            const activeListKey = dataSourceQueries.myList().queryKey;
            const archivedListKey = dataSourceQueries.myList(true).queryKey;
            queryClient.setQueryData(activeListKey, []);
            queryClient.setQueryData(archivedListKey, []);
            const mutation = dataSourceMutations[mutationName](queryClient);

            (mutation.onSuccess as SuccessHandler)(undefined, variables);

            expect(queryClient.getQueryState(activeListKey)?.isInvalidated).toBe(true);
            expect(queryClient.getQueryState(archivedListKey)?.isInvalidated).toBe(true);
        },
    );

    it("update invalidates source lists, opened detail, and assigned-source summaries", () => {
        const queryClient = new QueryClient();
        const activeAdminListKey = dataSourceQueries.adminList().queryKey;
        const archivedMyListKey = dataSourceQueries.myList(true).queryKey;
        const detailKey = dataSourceQueries.detail("source-1").queryKey;
        const firstAssignmentKey = userQueries.assignedDataSources("user-1").queryKey;
        const secondAssignmentKey = userQueries.assignedDataSources("user-2").queryKey;
        for (const key of [
            activeAdminListKey,
            archivedMyListKey,
            detailKey,
            firstAssignmentKey,
            secondAssignmentKey,
        ]) {
            queryClient.setQueryData(key, []);
        }
        const mutation = dataSourceMutations.update(queryClient);

        (mutation.onSuccess as SuccessHandler)(undefined, {
            id: "source-1",
            data: { access: "RW" },
        });

        for (const key of [
            activeAdminListKey,
            archivedMyListKey,
            detailKey,
            firstAssignmentKey,
            secondAssignmentKey,
        ]) {
            expect(queryClient.getQueryState(key)?.isInvalidated).toBe(true);
        }
    });

    it("update clears the cached editor/time rather than leaving the previous ones in place", () => {
        // If the background invalidation's refetch then fails, `invalidateQueries` keeps whatever
        // is in the cache at that point — so this asserts the patch itself already cleared the
        // stale pair, not just that a refetch was requested.
        const queryClient = new QueryClient();
        const detailKey = dataSourceQueries.detail("source-1").queryKey;
        queryClient.setQueryData(detailKey, {
            id: "source-1",
            name: "Old name",
            state: "CONNECTED",
            access: "R",
            transportType: "TCP",
            defaultVisible: true,
            updatedAt: "2026-01-01T00:00:00Z",
            updatedBy: "previous-editor",
        });
        const mutation = dataSourceMutations.update(queryClient);

        (mutation.onSuccess as SuccessHandler)(undefined, {
            id: "source-1",
            data: { name: "New name" },
        });

        expect(queryClient.getQueryData(detailKey)).toMatchObject({
            name: "New name",
            updatedAt: null,
            updatedBy: null,
        });
    });

    it("update clears the previous editor/time on the cached My Sources row too", () => {
        const queryClient = new QueryClient();
        const myListKey = dataSourceQueries.myList().queryKey;
        queryClient.setQueryData(myListKey, [
            mySource({
                serverId: "source-1",
                serverName: "Old name",
                updatedAt: "2026-01-01T00:00:00Z",
                updatedBy: "previous-editor",
            }),
            mySource({
                serverId: "source-2",
                serverName: "Untouched",
                updatedAt: "2026-02-02T00:00:00Z",
                updatedBy: "other-editor",
            }),
        ]);
        const mutation = dataSourceMutations.update(queryClient);

        (mutation.onSuccess as SuccessHandler)(undefined, {
            id: "source-1",
            data: { name: "New name" },
        });

        expect(queryClient.getQueryData(myListKey)).toMatchObject([
            { serverName: "New name", updatedAt: null, updatedBy: null },
            {
                serverName: "Untouched",
                updatedAt: "2026-02-02T00:00:00Z",
                updatedBy: "other-editor",
            },
        ]);
    });
});
