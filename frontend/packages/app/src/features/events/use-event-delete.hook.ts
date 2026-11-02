// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useCallback } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { getElementKey, localEventSource } from "@opencop/elements";
import { ApiError, getErrorMessage } from "@opencop/api";
import { useTranslation } from "react-i18next";
import { eventMutations, eventQueries } from "./events.queries";
import { useEventDetails } from "./use-event-details.hook";
import { useClearPickedElement } from "~/features/map/use-picked-element.hook";
import { useElementActions } from "@opencop/ingest";
import { useToast } from "~/features/notifications/use-toast.hook";
import type { EventMutationTarget } from "~/features/map/editability";

export function useEventDelete(mutationTarget?: EventMutationTarget): () => Promise<void> {
    const { t } = useTranslation();
    const { getLatestGeojson } = useEventDetails();
    const { mutateAsync: deleteLayerBroadcast } = useMutation(eventMutations.broadcastDelete());
    const { mutateAsync: deleteSourceBroadcast } = useMutation(
        eventMutations.broadcastSourceDelete(),
    );
    const clearPickedElement = useClearPickedElement();
    const { get, delete: deleteElement } = useElementActions();
    const { showToast } = useToast();
    const queryClient = useQueryClient();

    return useCallback(async () => {
        const geojson = getLatestGeojson();
        if (!geojson) return;
        const { objectId, source } = geojson.properties.opencop;
        if (!source) return;
        const element = get(getElementKey({ source, objectId }));

        if (source === localEventSource) {
            // Local-only element — no server state to update, just remove it.
            if (element) deleteElement(element.key);
            clearPickedElement();
            return;
        }

        try {
            if (!mutationTarget) {
                throw new Error("Missing mutation target for remote event");
            }

            if (mutationTarget.kind === "source") {
                await deleteSourceBroadcast({
                    sourceId: mutationTarget.id,
                    objectId,
                });
            } else {
                await deleteLayerBroadcast({
                    targetLayer: mutationTarget.id,
                    objectId,
                });
            }
            if (element) deleteElement(element.key);
            queryClient.removeQueries({
                queryKey: eventQueries.detail(source, objectId).queryKey,
            });
            clearPickedElement();
        } catch (error) {
            if (error instanceof ApiError && error.status === 404) {
                if (element) deleteElement(element.key);
                clearPickedElement();
                return;
            }

            showToast({
                type: "error",
                title: t("events.deleteFailed"),
                description: getErrorMessage(error, t("events.failedToDelete")),
            });
        }
    }, [
        clearPickedElement,
        deleteElement,
        deleteLayerBroadcast,
        deleteSourceBroadcast,
        get,
        getLatestGeojson,
        mutationTarget,
        queryClient,
        showToast,
        t,
    ]);
}
