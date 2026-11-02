// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { mutationOptions, QueryClient, queryOptions } from "@tanstack/react-query";
import {
    createWatchbox,
    deleteWatchbox,
    getWatchbox,
    getWatchboxDetections,
    getWatchboxes,
    patchWatchbox,
    testWatchboxFilter,
} from "./watchbox.api";

export const watchboxQueries = {
    all: ["watchboxes"],
    watchbox: (id: string) =>
        queryOptions({
            refetchInterval: 30_000,
            queryKey: [...watchboxQueries.all, id],
            queryFn: (opts) => getWatchbox(id, opts),
        }),
    watchboxes: () =>
        queryOptions({
            refetchInterval: 30_000,
            queryKey: [...watchboxQueries.all],
            queryFn: (opts) => getWatchboxes(opts),
        }),
    /**
     * No `refetchInterval`: live alerts are written straight into this cache
     * (`recordDetection`) instead of invalidating it, so a burst of alerts costs
     * no requests. `refetchOnMount: "always"` reconciles with the server every
     * time the tab opens, even over a page a live alert already wrote into.
     */
    detections: (watchboxId: string) =>
        queryOptions({
            queryKey: [...watchboxQueries.all, watchboxId, "detections"],
            queryFn: (opts) => getWatchboxDetections(watchboxId, opts),
            refetchOnMount: "always",
        }),
} as const;

export const watchboxMutations = {
    create: (queryClient: QueryClient) =>
        mutationOptions({
            mutationFn: createWatchbox,
            mutationKey: [...watchboxQueries.all, "create"],
            // The shared prefix also matches every open detections(*) key, so
            // this refetches any open Notifications tab too — at most one
            // extra request today.
            // Returned, not fired and forgotten: `mutateAsync` resolves only after
            // the list refetched, so the saved row is in the watchbox store before
            // the form ends the draw session and the draft disappears.
            onSuccess: () => queryClient.invalidateQueries({ queryKey: watchboxQueries.all }),
        }),
    patch: (queryClient: QueryClient) =>
        mutationOptions({
            mutationFn: patchWatchbox,
            mutationKey: [...watchboxQueries.all, "patch"],
            // A settled patch can change server-side state or capabilities even when
            // it returns a conflict. The shared prefix refreshes the list, detail,
            // and any active detections query. Returned for the same reason as
            // `create`: the adjusted polygon is in the watchbox store before the
            // edit session ends.
            onSettled: () => queryClient.invalidateQueries({ queryKey: watchboxQueries.all }),
        }),
    delete: (queryClient: QueryClient) =>
        mutationOptions({
            mutationFn: deleteWatchbox,
            mutationKey: [...watchboxQueries.all, "delete"],
            // Refetch on failure too: a denied or already-gone delete means the list's
            // cached capabilities/row are stale, not just the successful-delete case.
            onSettled: async (_data, error, id) => {
                await queryClient.invalidateQueries({
                    queryKey: watchboxQueries.watchboxes().queryKey,
                    exact: true,
                });
                // After a successful refetch, never before: evicting while the detail
                // is still mounted makes it refetch the id that was just deleted.
                if (!error) {
                    queryClient.removeQueries({ queryKey: watchboxQueries.watchbox(id).queryKey });
                }
            },
        }),
    testFilter: () =>
        mutationOptions({
            mutationFn: testWatchboxFilter,
            mutationKey: [...watchboxQueries.all, "test-filter"],
        }),
} as const;
