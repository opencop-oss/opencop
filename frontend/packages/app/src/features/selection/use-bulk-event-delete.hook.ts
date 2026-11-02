// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useCallback, useMemo, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { getElementKey, localEventSource, type Element } from "@opencop/elements";
import { ApiError } from "@opencop/api";
import { useElementActions } from "@opencop/ingest";
import { eventMutations, eventQueries } from "~/features/events/events.queries";
import { useToast } from "~/features/notifications/use-toast.hook";
import {
    clearPickedElementIfMatches,
    useClearPickedElement,
} from "~/features/map/use-picked-element.hook";
import { useAreaSelectionStore } from "./area-selection.store";
import { BULK_REQUEST_BATCH_SIZE, settleInBatches } from "./settle-in-batches.util";
import type { SelectionEditability } from "./use-selection-editability.hook";

interface BulkEventDelete {
    blockedElements: readonly Element[];
    /** Count of checked elements owned via a data source, which may re-broadcast and reappear after deletion. */
    dataSourceDeleteCount: number;
    isDeleting: boolean;
    deleteSelected: () => Promise<void>;
}

/**
 * Deletes the checked elements — all or nothing on permission: while any
 * checked element is read-only (`blockedElements`), or while `permissionStatus`
 * isn't "ready", `deleteSelected` does nothing. `access` must cover every
 * checked element. Generalizes `use-event-delete.hook.ts` (single, focused element) for a
 * selection that can span multiple layers and data sources at once.
 *
 * There is no batch-delete endpoint — both `broadcastDelete` and
 * `broadcastSourceDelete` are single-object mutations — so one request fires
 * per element, `BULK_REQUEST_BATCH_SIZE` at a time via `Promise.allSettled`
 * per batch (the lasso has no selection-size cap, so an unbounded selection
 * would otherwise fire hundreds of concurrent requests at once). Matches the
 * single-delete hook's per-element shape and its "404 means already gone"
 * handling rather than failing the whole batch on one already-deleted
 * element.
 */
export function useBulkEventDelete(
    checkedElements: readonly Element[],
    access: Pick<SelectionEditability, "editability" | "permissionStatus">,
): BulkEventDelete {
    const { editability, permissionStatus } = access;
    const { t } = useTranslation();
    const { showToast } = useToast();
    const queryClient = useQueryClient();
    const { delete: deleteElements } = useElementActions();
    const clearPickedElement = useClearPickedElement();
    const { mutateAsync: deleteLayerBroadcast } = useMutation(eventMutations.broadcastDelete());
    const { mutateAsync: deleteSourceBroadcast } = useMutation(
        eventMutations.broadcastSourceDelete(),
    );
    const [isDeleting, setIsDeleting] = useState(false);

    const blockedElements = useMemo(
        () =>
            permissionStatus === "ready"
                ? checkedElements.filter(
                      (element) => !editability.get(getElementKey(element))?.canDelete,
                  )
                : [],
        [checkedElements, editability, permissionStatus],
    );

    const dataSourceDeleteCount = useMemo(
        () =>
            checkedElements.filter(
                (element) => editability.get(getElementKey(element))?.reason === "dataSourceOwner",
            ).length,
        [checkedElements, editability],
    );

    const deleteSelected = useCallback(async () => {
        if (
            permissionStatus !== "ready" ||
            checkedElements.length === 0 ||
            blockedElements.length > 0
        ) {
            return;
        }

        setIsDeleting(true);
        try {
            const deleteOne = async (element: Element) => {
                if (element.source === localEventSource) return;

                const target = editability.get(getElementKey(element))?.mutationTarget;
                // Defensive: the blocked guard means every checked element is canDelete,
                // local elements returned above, and getEditability always pairs a
                // remote canDelete with a mutationTarget.
                if (!target) {
                    throw new Error(`Missing mutation target for ${getElementKey(element)}`);
                }

                if (target.kind === "source") {
                    await deleteSourceBroadcast({
                        sourceId: target.id,
                        objectId: element.objectId,
                    });
                } else {
                    await deleteLayerBroadcast({
                        targetLayer: target.id,
                        objectId: element.objectId,
                    });
                }
            };

            // Batched because the lasso has no selection-size cap (MapManager.pickInsidePolygon
            // takes every element inside the ring), so an unbatched delete could put hundreds of
            // broadcast DELETEs in flight at once. Same precedent as notifications.ts's
            // markAllNotificationsAsRead; see docs/best-practices/data-fetching/react-query.md
            // § "Bulk per-item requests".
            const outcomes = await settleInBatches(
                checkedElements,
                BULK_REQUEST_BATCH_SIZE,
                deleteOne,
            );

            const deletedKeys = new Set<string>();
            let failedCount = 0;
            outcomes.forEach((outcome, i) => {
                const element = checkedElements[i];
                if (outcome.status === "fulfilled") {
                    deletedKeys.add(element.key);
                    return;
                }
                if (outcome.reason instanceof ApiError && outcome.reason.status === 404) {
                    // Already gone server-side — treat like a success, matching
                    // use-event-delete.hook.ts's single-object precedent.
                    deletedKeys.add(element.key);
                    return;
                }
                failedCount++;
            });

            if (deletedKeys.size > 0) {
                deleteElements(deletedKeys);
                useAreaSelectionStore.getState().removeDeleted(deletedKeys);
                clearPickedElementIfMatches(deletedKeys, clearPickedElement);
                for (const element of checkedElements) {
                    if (deletedKeys.has(element.key)) {
                        queryClient.removeQueries({
                            queryKey: eventQueries.detail(element.source, element.objectId)
                                .queryKey,
                        });
                    }
                }
            }

            if (failedCount === 0) return; // Every deletable element succeeded — no failure toast needed.

            const parts: string[] = [];
            if (deletedKeys.size > 0) {
                parts.push(t("selectionPanel.deletedCount", { count: deletedKeys.size }));
            }
            parts.push(t("selectionPanel.failedCount", { count: failedCount }));

            showToast({
                type: "error",
                title:
                    deletedKeys.size === 0
                        ? t("selectionPanel.deleteFailedTitle")
                        : t("selectionPanel.deletePartialTitle"),
                description: parts.join(" "),
            });
        } finally {
            setIsDeleting(false);
        }
    }, [
        blockedElements.length,
        checkedElements,
        clearPickedElement,
        deleteElements,
        deleteLayerBroadcast,
        deleteSourceBroadcast,
        editability,
        permissionStatus,
        queryClient,
        showToast,
        t,
    ]);

    return { blockedElements, dataSourceDeleteCount, isDeleting, deleteSelected };
}
