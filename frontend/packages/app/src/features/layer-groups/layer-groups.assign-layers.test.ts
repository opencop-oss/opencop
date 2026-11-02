// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { QueryClient } from "@tanstack/react-query";
import { describe, expect, it } from "vitest";
import { server, http, HttpResponse, API_BASE } from "~/test";
import { dataSourceQueries } from "~/features/data-sources/data-sources.queries";
import { layerQueries } from "~/features/layers/layers.queries";
import { layerGroupMutations, layerGroupQueries } from "./layer-groups.queries";

describe("layerGroupMutations.assignLayersToGroups", () => {
    it("POSTs all selected layer ids with one shared role to the batch endpoint", async () => {
        const queryClient = new QueryClient();
        const options = layerGroupMutations.assignLayersToGroups(queryClient);

        // Capture the real HTTP request the mutation drives through `apiFetch`.
        let callCount = 0;
        let seenUrl: string | null = null;
        let seenMethod: string | null = null;
        let seenBody: unknown;
        server.use(
            http.post(`${API_BASE}/layer-groups/group-1/layers`, async ({ request }) => {
                callCount += 1;
                seenUrl = request.url;
                seenMethod = request.method;
                seenBody = await request.json();
                return HttpResponse.json([]);
            }),
        );

        // mutationFn gained a second `context` arg in TanStack Query v5; call it directly
        // with just the variables for this contract test.
        const mutationFn = options.mutationFn as (vars: unknown) => Promise<unknown>;

        // The single-group case still issues exactly one POST — this mutation subsumes the former
        // `assignLayers`, so a one-element `groups` array must stay equivalent to it.
        await mutationFn({
            groups: [{ id: "group-1", name: "Alpha" }],
            layerIds: ["layer-a", "layer-b", "layer-c"],
            role: "EDITOR",
        });

        expect(callCount).toBe(1);
        expect(seenUrl).toBe(`${API_BASE}/layer-groups/group-1/layers`);
        expect(seenMethod).toBe("POST");
        expect(seenBody).toEqual({
            layerIds: ["layer-a", "layer-b", "layer-c"],
            role: "EDITOR",
        });
    });

    it.each([
        [
            "assignLayersToGroups",
            { groups: [{ id: "group-1", name: "Alpha" }], layerIds: ["layer-1"], role: "EDITOR" },
        ],
        ["removeLayer", { groupId: "group-1", layerId: "layer-1" }],
    ] as const)("%s invalidates all derived effective-access lists", (mutationName, variables) => {
        const queryClient = new QueryClient();
        const effectiveListKeys = [
            layerQueries.myList().queryKey,
            layerQueries.myList(true).queryKey,
            dataSourceQueries.myList().queryKey,
            dataSourceQueries.myList(true).queryKey,
        ];
        for (const queryKey of effectiveListKeys) queryClient.setQueryData(queryKey, []);
        const mutation = layerGroupMutations[mutationName](queryClient);

        const settle = (mutation.onSettled ?? mutation.onSuccess) as (
            data: unknown,
            errorOrVariables: unknown,
            variables?: unknown,
        ) => void;
        if (mutation.onSettled) settle(undefined, undefined, variables);
        else settle(undefined, variables);

        for (const queryKey of effectiveListKeys) {
            expect(queryClient.getQueryState(queryKey)?.isInvalidated).toBe(true);
        }
    });

    describe("assignLayersToGroups", () => {
        const GROUPS = [
            { id: "group-1", name: "Alpha" },
            { id: "group-2", name: "Bravo" },
        ];

        it("fans out one request per group and resolves when all succeed", async () => {
            const seen: string[] = [];
            server.use(
                http.post(`${API_BASE}/layer-groups/:groupId/layers`, ({ request }) => {
                    seen.push(request.url);
                    return HttpResponse.json([]);
                }),
            );
            const options = layerGroupMutations.assignLayersToGroups(new QueryClient());
            const mutationFn = options.mutationFn as (vars: unknown) => Promise<unknown>;

            await mutationFn({ groups: GROUPS, layerIds: ["layer-1"], role: "EDITOR" });

            expect(seen.sort()).toEqual([
                `${API_BASE}/layer-groups/group-1/layers`,
                `${API_BASE}/layer-groups/group-2/layers`,
            ]);
        });

        it("rejects naming only the groups that failed, without rolling back the rest", async () => {
            let succeeded = 0;
            server.use(
                http.post(`${API_BASE}/layer-groups/group-1/layers`, () => {
                    succeeded += 1;
                    return HttpResponse.json([]);
                }),
                http.post(`${API_BASE}/layer-groups/group-2/layers`, () =>
                    HttpResponse.json({ message: "denied" }, { status: 403 }),
                ),
            );
            const options = layerGroupMutations.assignLayersToGroups(new QueryClient());
            const mutationFn = options.mutationFn as (vars: unknown) => Promise<unknown>;

            // The message is what both call sites surface, so it must name the failure, not count it.
            await expect(
                mutationFn({ groups: GROUPS, layerIds: ["layer-1"], role: "EDITOR" }),
            ).rejects.toThrow("Bravo");
            expect(succeeded).toBe(1);
        });

        it("reports the committed ids so a retry can skip them", async () => {
            server.use(
                http.post(`${API_BASE}/layer-groups/group-1/layers`, () => HttpResponse.json([])),
                http.post(`${API_BASE}/layer-groups/group-2/layers`, () =>
                    HttpResponse.json({ message: "denied" }, { status: 403 }),
                ),
            );
            const options = layerGroupMutations.assignLayersToGroups(new QueryClient());
            const mutationFn = options.mutationFn as (vars: unknown) => Promise<unknown>;

            // Without succeededIds the callers can't prune, and a retry re-POSTs group-1. The
            // endpoint upserts, so a retry carrying a different role would silently rewrite the
            // role that already committed there.
            await expect(
                mutationFn({ groups: GROUPS, layerIds: ["layer-1"], role: "EDITOR" }),
            ).rejects.toMatchObject({
                succeededIds: ["group-1"],
                failedNames: ["Bravo"],
            });
        });

        it("invalidates both directions on the failure path too", () => {
            const queryClient = new QueryClient();
            const keys = [
                layerGroupQueries.layers("group-1").queryKey,
                layerGroupQueries.forLayer("layer-1").queryKey,
            ];
            for (const queryKey of keys) queryClient.setQueryData(queryKey, []);
            const options = layerGroupMutations.assignLayersToGroups(queryClient);

            // onSettled, not onSuccess: a partial failure still committed some assignments.
            (options.onSettled as (d: unknown, e: unknown, v: unknown) => void)(
                undefined,
                new Error("Bravo"),
                {
                    groups: GROUPS,
                    layerIds: ["layer-1"],
                    role: "EDITOR",
                },
            );

            for (const queryKey of keys) {
                expect(queryClient.getQueryState(queryKey)?.isInvalidated).toBe(true);
            }
        });
    });

    it.each([
        [
            "assignLayersToGroups",
            {
                groups: [{ id: "group-1", name: "Alpha" }],
                layerIds: ["layer-1", "layer-2"],
                role: "EDITOR",
            },
        ],
        ["removeLayer", { groupId: "group-1", layerId: "layer-1" }],
    ] as const)(
        "%s invalidates the per-layer reverse view the layer drawer reads",
        (mutationName, variables) => {
            const queryClient = new QueryClient();
            const affected = "layerIds" in variables ? variables.layerIds : [variables.layerId];
            for (const layerId of affected) {
                queryClient.setQueryData(layerGroupQueries.forLayer(layerId).queryKey, []);
            }
            // An untouched layer, to prove the invalidation is targeted rather than a blanket sweep.
            queryClient.setQueryData(layerGroupQueries.forLayer("layer-other").queryKey, []);
            const mutation = layerGroupMutations[mutationName](queryClient);

            const settle = (mutation.onSettled ?? mutation.onSuccess) as (
                data: unknown,
                errorOrVariables: unknown,
                variables?: unknown,
            ) => void;
            if (mutation.onSettled) settle(undefined, undefined, variables);
            else settle(undefined, variables);

            // Without this the drawer keeps showing stale membership immediately after an assign.
            for (const layerId of affected) {
                const state = queryClient.getQueryState(
                    layerGroupQueries.forLayer(layerId).queryKey,
                );
                expect(state?.isInvalidated).toBe(true);
            }
            expect(
                queryClient.getQueryState(layerGroupQueries.forLayer("layer-other").queryKey)
                    ?.isInvalidated,
            ).toBe(false);
        },
    );
});
