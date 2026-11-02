// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { type QueryClient, mutationOptions, queryOptions } from "@tanstack/react-query";
import {
    getDataSources,
    getMyDataSources,
    getSourceCandidatesForLayer,
    getUsersForDataSource,
    getLayersForDataSource,
    createConnectionV2,
    deleteDataSource,
    addLayersToDataSource,
    deleteLayerFromDataSource,
    addUsersToDataSource,
    deleteUserFromDataSource,
    disconnectDataSource,
    getDataSource,
    patchDataSource,
    reconnectDataSource,
} from "@opencop/api";
import type {
    ConnectionCreatedV2Response,
    CreateConnectionV2Request,
    DataSourceInfo,
    DataSourcePatch,
    MyDataSource,
} from "@opencop/api";
import { auditQueries } from "../admin/audit/audit.queries";
import { layerQueries } from "../layers/layers.queries";
import { userQueries } from "../users/users.queries";

/**
 * In dev builds, the historical test sources aren't returned by the backend, so
 * we inject them into any list that includes active sources. Returns a new array
 * (does not mutate the input) so React Query cache entries stay isolated.
 *
 * The first entry (`…0000`, "Historical Test Source (dev)") is the long-standing
 * single dev source — the backend `HistoricalSimulationParameters.DEFAULT_SOURCE_ID`
 * matches it, e2e references it by name, so keep its id and name stable. It is the
 * all-zero nil UUID, which `uuid.validate` accepts; the extra entries must also be
 * **valid RFC-4122 UUIDs** (note the `4` version + `8` variant nibbles), because a
 * source id flows into `ElementMessage.source` and the historical decode runs it
 * through `uuid.stringify` — a non-RFC id (e.g. `…0001`, version nibble 0 and not
 * nil) throws "Stringified UUID is invalid". The extra entries exist to exercise
 * multi-source historical view in dev; seed each with
 * `POST /v1/history/test-data/generate` (one call per `sourceId`). Global admins
 * bypass source authorization, so these synthetic ids pass histogram + tracks
 * without any access rows.
 */
const DEV_TEST_SOURCES: ReadonlyArray<Pick<MyDataSource, "serverId" | "serverName">> = [
    {
        serverId: "00000000-0000-0000-0000-000000000000",
        serverName: "Historical Test Source (dev)",
    },
    {
        serverId: "00000000-0000-4000-8000-000000000001",
        serverName: "Historical Test — Ground (dev)",
    },
    {
        serverId: "00000000-0000-4000-8000-000000000002",
        serverName: "Historical Test — Maritime (dev)",
    },
];

function withDevTestSource(sources: MyDataSource[]): MyDataSource[] {
    if (!import.meta.env.DEV) return sources;
    const present = new Set(sources.map((s) => s.serverId));
    const injected: MyDataSource[] = DEV_TEST_SOURCES.filter((s) => !present.has(s.serverId)).map(
        (s) => ({
            serverId: s.serverId,
            serverName: s.serverName,
            role: "OWNER",
            directRole: "OWNER",
            serverState: "CONNECTED",
            via: "DIRECT",
            access: "RW",
            transportType: "TCP",
            defaultVisible: true,
        }),
    );
    return [...injected, ...sources];
}

export const dataSourceQueries = {
    all: ["dataSources"] as const,
    lists: () => [...dataSourceQueries.all, "list"] as const,
    adminLists: () => [...dataSourceQueries.lists(), "admin"] as const,
    adminList: (archived = false) =>
        queryOptions({
            queryKey: [...dataSourceQueries.adminLists(), archived] as const,
            queryFn: ({ signal }) =>
                getDataSources({ signal, recordVisibility: archived ? "ARCHIVED" : "ACTIVE" }),
        }),
    myLists: () => [...dataSourceQueries.lists(), "mine"] as const,
    myList: (archived = false) =>
        queryOptions({
            queryKey: [...dataSourceQueries.myLists(), archived] as const,
            queryFn: async ({ signal }) => {
                const sources = await getMyDataSources({
                    signal,
                    recordVisibility: archived ? "ARCHIVED" : "ACTIVE",
                });
                return archived ? sources : withDevTestSource(sources);
            },
        }),
    candidatesForLayer: (layerId: string) =>
        queryOptions({
            queryKey: [...dataSourceQueries.lists(), "candidates", "layer", layerId] as const,
            queryFn: ({ signal }) => getSourceCandidatesForLayer(layerId, { signal }),
            enabled: !!layerId,
        }),
    details: () => [...dataSourceQueries.all, "detail"] as const,
    detail: (id: string) =>
        queryOptions({
            queryKey: [...dataSourceQueries.details(), id] as const,
            queryFn: ({ signal }) => getDataSource(id, { signal }),
            enabled: !!id,
            retry: false,
        }),
    users: (id: string) =>
        queryOptions({
            queryKey: [...dataSourceQueries.detail(id).queryKey, "users"] as const,
            queryFn: ({ signal }) => getUsersForDataSource(id, { signal }),
            enabled: !!id,
        }),
    layers: (id: string) =>
        queryOptions({
            queryKey: [...dataSourceQueries.detail(id).queryKey, "layers"] as const,
            queryFn: ({ signal }) => getLayersForDataSource(id, { signal }),
            enabled: !!id,
        }),
};

/**
 * Writes a patch the server has already acknowledged into every cache the source
 * panels read, before the background invalidation runs.
 *
 * `invalidateQueries` swallows refetch failures by design and leaves the previous
 * payload in the cache, so a failed readback otherwise keeps rendering the
 * pre-patch value with no error anywhere in the UI — the drawer prefers
 * `connection.access` over the summary, and the summary table reads the list. The
 * patch is applied post-`204`, so this is a confirmed value, not an optimistic
 * guess; a refetch that does succeed overwrites it with the server's own payload.
 *
 * The patch itself never carries `updatedAt`/`updatedBy` — those are server-owned — so merging it
 * in naively would leave the previous editor's name and time in place beside the new value. This
 * clears both instead, so a failed readback shows "unknown" rather than misattributing the edit.
 */
function applyAcknowledgedPatch(
    queryClient: QueryClient,
    id: string,
    patch: DataSourcePatch,
): void {
    queryClient.setQueryData(dataSourceQueries.detail(id).queryKey, (previous) =>
        previous ? { ...previous, ...patch, updatedAt: null, updatedBy: null } : previous,
    );
    queryClient.setQueriesData<MyDataSource[]>(
        { queryKey: dataSourceQueries.myLists() },
        (previous) =>
            previous?.map((source) =>
                source.serverId === id
                    ? {
                          ...source,
                          ...(patch.name !== undefined && { serverName: patch.name }),
                          ...(patch.access !== undefined && { access: patch.access }),
                          ...(patch.defaultVisible !== undefined && {
                              defaultVisible: patch.defaultVisible,
                          }),
                          updatedAt: null,
                          updatedBy: null,
                      }
                    : source,
            ),
    );
    queryClient.setQueriesData<DataSourceInfo[]>(
        { queryKey: dataSourceQueries.adminLists() },
        (previous) =>
            previous?.map((source) =>
                source.id === id
                    ? { ...source, ...patch, updatedAt: null, updatedBy: null }
                    : source,
            ),
    );
}

export const dataSourceMutations = {
    createV2: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...dataSourceQueries.all, "createV2"],
            mutationFn: (params: {
                request: CreateConnectionV2Request;
                files: Record<string, File[]>;
            }): Promise<ConnectionCreatedV2Response> =>
                createConnectionV2(params.request, params.files),
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: dataSourceQueries.lists() });
            },
        }),
    delete: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...dataSourceQueries.all, "delete"],
            mutationFn: (serverId: string) => deleteDataSource(serverId),
            onSuccess: (_, serverId) => {
                queryClient.invalidateQueries({ queryKey: dataSourceQueries.lists() });
                queryClient.removeQueries({
                    queryKey: dataSourceQueries.detail(serverId).queryKey,
                });
            },
        }),
    addLayers: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...dataSourceQueries.all, "addLayers"],
            mutationFn: ({
                serverId,
                layerIds,
                role,
            }: {
                serverId: string;
                layerIds: string[];
                role?: "VIEWER" | "ADMIN" | "OWNER";
            }) => addLayersToDataSource(serverId, layerIds, role),
            onSuccess: (_, { serverId, layerIds }) => {
                queryClient.invalidateQueries({
                    queryKey: dataSourceQueries.myList().queryKey,
                });
                queryClient.invalidateQueries({
                    queryKey: dataSourceQueries.layers(serverId).queryKey,
                });
                for (const id of layerIds) {
                    queryClient.invalidateQueries({
                        queryKey: layerQueries.dataSources(id).queryKey,
                    });
                }
            },
        }),
    deleteLayer: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...dataSourceQueries.all, "deleteLayer"],
            mutationFn: ({ serverId, layerId }: { serverId: string; layerId: string }) =>
                deleteLayerFromDataSource(serverId, layerId),
            onSuccess: (_, { serverId, layerId }) => {
                queryClient.invalidateQueries({
                    queryKey: dataSourceQueries.myList().queryKey,
                });
                queryClient.invalidateQueries({
                    queryKey: dataSourceQueries.layers(serverId).queryKey,
                });
                queryClient.invalidateQueries({
                    queryKey: layerQueries.dataSources(layerId).queryKey,
                });
            },
        }),
    addUsers: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...dataSourceQueries.all, "addUsers"],
            mutationFn: ({
                serverId,
                userId,
                role,
            }: {
                serverId: string;
                userId: string[];
                role: "VIEWER" | "ADMIN" | "OWNER";
            }) => addUsersToDataSource(serverId, userId, role),
            onSuccess: (_, { serverId, userId }) => {
                queryClient.invalidateQueries({
                    queryKey: dataSourceQueries.myLists(),
                });
                queryClient.invalidateQueries({
                    queryKey: dataSourceQueries.users(serverId).queryKey,
                });
                for (const id of userId) {
                    queryClient.invalidateQueries({
                        queryKey: userQueries.assignedDataSources(id).queryKey,
                    });
                }
            },
        }),
    deleteUser: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...dataSourceQueries.all, "deleteUser"],
            mutationFn: ({ serverId, userId }: { serverId: string; userId: string }) =>
                deleteUserFromDataSource(serverId, userId),
            onSuccess: (_, { serverId, userId }) => {
                queryClient.invalidateQueries({
                    queryKey: dataSourceQueries.myLists(),
                });
                queryClient.invalidateQueries({
                    queryKey: dataSourceQueries.users(serverId).queryKey,
                });
                queryClient.invalidateQueries({
                    queryKey: userQueries.assignedDataSources(userId).queryKey,
                });
            },
        }),
    update: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...dataSourceQueries.all, "update"],
            mutationFn: ({ id, data }: { id: string; data: DataSourcePatch }) =>
                patchDataSource(id, data),
            onSuccess: async (_, { id, data }) => {
                applyAcknowledgedPatch(queryClient, id, data);
                await Promise.all([
                    queryClient.invalidateQueries({ queryKey: dataSourceQueries.lists() }),
                    queryClient.invalidateQueries({
                        queryKey: dataSourceQueries.detail(id).queryKey,
                    }),
                    queryClient.invalidateQueries({
                        queryKey: userQueries.details(),
                        predicate: (query) => query.queryKey.at(-1) === "dataSources",
                    }),
                ]);
            },
        }),
    disconnect: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...dataSourceQueries.all, "disconnect"],
            mutationFn: (serverId: string) => disconnectDataSource(serverId),
            onSuccess: (_, serverId) => {
                queryClient.invalidateQueries({
                    queryKey: dataSourceQueries.detail(serverId).queryKey,
                });
                queryClient.invalidateQueries({ queryKey: dataSourceQueries.lists() });
            },
            // On settle, not success: Core can commit the row before the response reaches this
            // browser, so a lost response leaves the outcome unknown. Same rule as the other
            // audit-writing mutations.
            onSettled: () => queryClient.invalidateQueries({ queryKey: auditQueries.all }),
        }),
    reconnect: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: [...dataSourceQueries.all, "reconnect"],
            mutationFn: (serverId: string) => reconnectDataSource(serverId),
            onSuccess: (_, serverId) => {
                queryClient.invalidateQueries({
                    queryKey: dataSourceQueries.detail(serverId).queryKey,
                });
                queryClient.invalidateQueries({ queryKey: dataSourceQueries.lists() });
            },
            // On settle, not success: Core can commit the row before the response reaches this
            // browser, so a lost response leaves the outcome unknown. Same rule as the other
            // audit-writing mutations.
            onSettled: () => queryClient.invalidateQueries({ queryKey: auditQueries.all }),
        }),
};
