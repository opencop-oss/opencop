// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    keepPreviousData,
    mutationOptions,
    QueryClient,
    queryOptions,
} from "@tanstack/react-query";
import {
    fetchEventDetailsLatest,
    fetchEventHistory,
    createBroadcastEvent,
    patchBroadcastEvent,
    deleteBroadcastEvent,
    patchSourceBroadcastEvent,
    deleteSourceBroadcastEvent,
    pushEvent,
    type HistoryParams,
    retrieveRemarks,
    updateRemark,
    addRemark,
    deleteRemark,
    listEventAttachments,
    uploadEventAttachment,
    registerEventAttachmentUrl,
    deleteEventAttachment,
    type FileAttachment,
    type UrlAttachment,
    type StreamKind,
    type EventAttachment,
    type EventDetails,
    prepareAttachmentStream,
} from "@opencop/api";
import type { ElementRef, PartialOCFeature } from "@opencop/elements";
import { createThumbnail } from "@opencop/ui";
import { sortBy } from "lodash";

const DETAIL_REFRESH_MS = 10_000;

export const RTSP_KLV_EVENT_KIND = "rtsp.klv.v1" as const;

/** Oldest first: the attachments query has no ORDER BY, and the first one is the default stream. */
export const selectUrlAttachments = (items: EventAttachment[]): UrlAttachment[] =>
    sortBy(
        items.filter(
            (a): a is UrlAttachment => a.kind === "URL" && a.record.usage === "EVENT_DETAIL",
        ),
        (a) => a.record.createdAt,
    );

export const eventQueries = {
    all: ["events"] as const,
    resource: ({ sourceId, objectId }: ElementRef) =>
        [...eventQueries.all, sourceId, objectId] as const,
    /**
     * Query options for the event detail endpoint.
     *
     * @param objectId Stable event identifier used as part of the REST lookup
     *   key (via `/events/{sourceId}/object/{objectId}/details/latest`).
     *   The backend always returns the most recent state for this event,
     *   eliminating race conditions where a per-update UUID arrives via
     *   WebSocket before the database write completes.
     */
    detail: (sourceId: string, objectId: string) =>
        queryOptions({
            queryKey: [...eventQueries.resource({ sourceId, objectId }), "detail"] as const,
            queryFn: ({ signal }) => fetchEventDetailsLatest(sourceId, objectId, { signal }),
            enabled: !!sourceId && !!objectId,
            staleTime: DETAIL_REFRESH_MS,
            refetchInterval: DETAIL_REFRESH_MS,
            placeholderData: keepPreviousData,
            // Keep the detail cache monotonic in `timestampz.time`. The read-model
            // (`geo.features`) is populated by an async Kafka projection that lags the
            // write — the write endpoint publishes to Kafka and returns an in-memory
            // feature, while a separate consumer persists the queryable row. So a
            // refetch issued right after a write (notably the refetchOnMount that
            // `applyBroadcastResponse`'s `invalidateQueries` enables) can return a
            // *stale, older* version that would clobber the optimistic post-write
            // update. `timestampz.time` increases on every write (server stamps `now`;
            // `version` is hardcoded "1", so it can't discriminate). Drop any
            // read older than what we already hold.
            structuralSharing: (prev, next) => {
                const previous = prev as EventDetails | undefined;
                const incoming = next as EventDetails | undefined;
                if (
                    previous?.timestampz?.time != null &&
                    incoming?.timestampz?.time != null &&
                    incoming.timestampz.time < previous.timestampz.time
                ) {
                    return prev;
                }
                return next;
            },
            // A 404 from this query immediately after a broadcast *create* is expected,
            // not a failure: the just-created event hasn't propagated to the read-model
            // yet (same projection lag as above), and the panel renders from the
            // optimistic element meanwhile. Tagged so the global QueryCache error handler
            // can recognise this benign case instead of surfacing it as a real error.
            // (The browser still logs the network 404; this marker is how we
            // document and detect that it is expected.)
            meta: { expectedReadModelLag404: true },
        }),
    histories: ({ sourceId, objectId }: ElementRef) =>
        [...eventQueries.resource({ sourceId, objectId }), "history"] as const,
    history: (sourceId: string, objectId: string, params?: HistoryParams) =>
        queryOptions({
            queryKey: [...eventQueries.histories({ sourceId, objectId }), params] as const,
            queryFn: ({ signal }) => fetchEventHistory(sourceId, objectId, params, { signal }),
            placeholderData: keepPreviousData,
            enabled: !!sourceId && !!objectId && sourceId !== "local",
        }),
    remarks: (sourceId: string, objectId: string) =>
        queryOptions({
            queryKey: [...eventQueries.resource({ sourceId, objectId }), "remarks"] as const,
            queryFn: () => retrieveRemarks(sourceId, objectId),
            enabled: !!sourceId && !!objectId && sourceId !== "local",
            staleTime: DETAIL_REFRESH_MS,
            refetchInterval: DETAIL_REFRESH_MS,
            placeholderData: keepPreviousData,
        }),
    allAttachments: (sourceId: string, objectId: string) =>
        queryOptions({
            queryKey: [...eventQueries.resource({ sourceId, objectId }), "attachments"] as const,
            queryFn: ({ signal }) => listEventAttachments(sourceId, objectId, { signal }),
            enabled:
                !!sourceId && !!objectId && sourceId !== "local" && !objectId.startsWith("local:"),
            staleTime: DETAIL_REFRESH_MS,
            refetchInterval: DETAIL_REFRESH_MS,
        }),
};

export const streamQueries = {
    prepareStream: (sourceId: string, objectId: string, attachmentId: string) =>
        queryOptions({
            queryKey: [
                ...eventQueries.resource({ sourceId, objectId }),
                "stream",
                "prepare",
                attachmentId,
            ] as const,
            queryFn: ({ signal }) =>
                prepareAttachmentStream(sourceId, objectId, attachmentId, { signal }),
            staleTime: 5 * 60 * 1000,
            gcTime: 10 * 60 * 1000,
            retry: false,
            refetchOnWindowFocus: false,
            refetchOnMount: false,
        }),
};

export const eventMutations = {
    broadcastCreate: (
        onSuccess?: (
            data: PartialOCFeature | undefined,
            variables: { targetLayer: string; feature: PartialOCFeature },
        ) => void,
    ) =>
        mutationOptions({
            mutationKey: [...eventQueries.all, "broadcast", "create"],
            mutationFn: createBroadcastEvent,
            onSuccess,
        }),
    broadcastPatch: (
        onSuccess?: (
            data: PartialOCFeature | undefined,
            variables: { targetLayer: string; objectId: string; feature: PartialOCFeature },
        ) => void,
    ) =>
        mutationOptions({
            mutationKey: [...eventQueries.all, "broadcast", "patch"],
            mutationFn: patchBroadcastEvent,
            onSuccess,
        }),
    broadcastSourcePatch: (
        onSuccess?: (
            data: PartialOCFeature | undefined,
            variables: { sourceId: string; objectId: string; feature: PartialOCFeature },
        ) => void,
    ) =>
        mutationOptions({
            mutationKey: [...eventQueries.all, "broadcast", "source", "patch"],
            mutationFn: patchSourceBroadcastEvent,
            onSuccess,
        }),
    broadcastDelete: () =>
        mutationOptions({
            mutationKey: [...eventQueries.all, "broadcast", "delete"],
            mutationFn: deleteBroadcastEvent,
        }),
    broadcastSourceDelete: () =>
        mutationOptions({
            mutationKey: [...eventQueries.all, "broadcast", "source", "delete"],
            mutationFn: deleteSourceBroadcastEvent,
        }),
    remarks: {
        add: (queryClient: QueryClient, sourceId: string, objectId: string) =>
            mutationOptions({
                mutationKey: [...eventQueries.resource({ sourceId, objectId }), "remarks"] as const,
                mutationFn: (req: { content: string }) => addRemark(sourceId, objectId, req),
                onSuccess: () => {
                    queryClient.invalidateQueries({
                        queryKey: [...eventQueries.resource({ sourceId, objectId }), "remarks"],
                    });
                },
            }),
        update: (queryClient: QueryClient, sourceId: string, objectId: string) =>
            mutationOptions({
                mutationKey: [...eventQueries.resource({ sourceId, objectId }), "remarks"] as const,
                mutationFn: (req: { id: string; content: string }) =>
                    updateRemark(sourceId, objectId, req),
                onSuccess: () =>
                    queryClient.invalidateQueries({
                        queryKey: [...eventQueries.resource({ sourceId, objectId }), "remarks"],
                    }),
            }),
        delete: (queryClient: QueryClient, sourceId: string, objectId: string) =>
            mutationOptions({
                mutationKey: [...eventQueries.resource({ sourceId, objectId }), "remarks"] as const,
                mutationFn: (req: { id: string }) => deleteRemark(sourceId, objectId, req.id),
                onSuccess: () => {
                    queryClient.invalidateQueries({
                        queryKey: [...eventQueries.resource({ sourceId, objectId }), "remarks"],
                    });
                },
            }),
    },
    push: () =>
        mutationOptions({
            mutationKey: [...eventQueries.all, "push"],
            mutationFn: ({ sourceId, feature }: { sourceId: string; feature: PartialOCFeature }) =>
                pushEvent(sourceId, feature),
        }),
    attachments: {
        upload: (queryClient: QueryClient) =>
            mutationOptions({
                mutationKey: [...eventQueries.all, "attachments", "upload"],
                mutationFn: async ({
                    sourceId,
                    objectId,
                    file,
                }: {
                    sourceId: string;
                    objectId: string;
                    file: File;
                }): Promise<FileAttachment> => {
                    const thumbnail = await createThumbnail(file);
                    return uploadEventAttachment(
                        sourceId,
                        objectId,
                        file,
                        thumbnail ? [thumbnail] : [],
                    );
                },
                onSuccess: (data, { sourceId, objectId }) => {
                    queryClient.setQueryData<EventAttachment[]>(
                        [...eventQueries.resource({ sourceId, objectId }), "attachments"],
                        (old) => (old ? [...old, data] : [data]),
                    );
                },
                onSettled: (_data, _error, { sourceId, objectId }) => {
                    queryClient.invalidateQueries({
                        queryKey: [...eventQueries.resource({ sourceId, objectId }), "attachments"],
                    });
                },
            }),
        registerUrl: (queryClient: QueryClient) =>
            mutationOptions({
                mutationKey: [...eventQueries.all, "attachments", "registerUrl"],
                mutationFn: ({
                    sourceId,
                    objectId,
                    url,
                    streamKind,
                }: {
                    sourceId: string;
                    objectId: string;
                    url: string;
                    streamKind: StreamKind;
                }): Promise<UrlAttachment> =>
                    registerEventAttachmentUrl(sourceId, objectId, url, streamKind),
                onSuccess: (data, { sourceId, objectId }) => {
                    queryClient.setQueryData<EventAttachment[]>(
                        eventQueries.allAttachments(sourceId, objectId).queryKey,
                        (old) => (old ? [...old, data] : [data]),
                    );
                },
                onSettled: (_data, _error, { sourceId, objectId }) => {
                    queryClient.invalidateQueries({
                        queryKey: eventQueries.allAttachments(sourceId, objectId).queryKey,
                    });
                },
            }),
        delete: (queryClient: QueryClient) =>
            mutationOptions({
                mutationKey: [...eventQueries.all, "attachments", "delete"],
                mutationFn: ({
                    sourceId,
                    objectId,
                    mediaId,
                }: {
                    sourceId: string;
                    objectId: string;
                    mediaId: string;
                }) => deleteEventAttachment(sourceId, objectId, mediaId),
                onMutate: async ({ sourceId, objectId, mediaId }) => {
                    await queryClient.cancelQueries({
                        queryKey: [...eventQueries.resource({ sourceId, objectId }), "attachments"],
                    });

                    const previous = queryClient.getQueryData<EventAttachment[]>([
                        ...eventQueries.resource({ sourceId, objectId }),
                        "attachments",
                    ]);

                    queryClient.setQueryData<EventAttachment[]>(
                        [...eventQueries.resource({ sourceId, objectId }), "attachments"],
                        (old = []) => old.filter((a) => a.record.id !== mediaId),
                    );

                    return { previous };
                },
                onError: (_error, { sourceId, objectId }, context) => {
                    if (context?.previous) {
                        queryClient.setQueryData(
                            [...eventQueries.resource({ sourceId, objectId }), "attachments"],
                            context.previous,
                        );
                    }
                },
                onSettled: (_data, _error, { sourceId, objectId, mediaId }) => {
                    queryClient.invalidateQueries({
                        queryKey: [...eventQueries.resource({ sourceId, objectId }), "attachments"],
                    });
                    queryClient.removeQueries({
                        queryKey: [
                            ...eventQueries.resource({ sourceId, objectId }),
                            "stream",
                            "prepare",
                            mediaId,
                        ],
                    });
                },
            }),
    },
};
