// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useCallback, useMemo } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { assertNever } from "assert-never";
import { type EventDetails } from "@opencop/api";
import { Element, getElementKey, toElementRef } from "@opencop/elements";
import type { PartialOCFeature } from "@opencop/elements";
import { eventMutations, eventQueries } from "./events.queries";
import { stampBroadcastTimestamps } from "./broadcast-feature.util";
import { planSave } from "./plan-save";
import { useEventsEditingStore } from "./events-editing.store";
import { useEventDetails } from "./use-event-details.hook";
import { usePickedElementRef } from "~/features/map/use-picked-element.hook";
import { save } from "~/features/map/interaction-runtime/session/commands";
import { useMapManager } from "~/features/map/MapManagerContext";
import { useElementActions, useSourcesWithState } from "@opencop/ingest";
import type { EventMutationTarget } from "~/features/map/editability";

/** Read overlay at call time — stable module-scope reference for useCallback. */
const getOverlay = () => useEventsEditingStore.getState().overlay;

/**
 * A move's re-broadcast failed *after* its delete committed: the event is gone
 * from its old layer and now exists on no remote source. Callers must surface
 * that state — a generic "save failed" would imply nothing changed.
 */
export class MoveRebroadcastError extends Error {
    cause: unknown;

    constructor(cause: unknown) {
        super("Re-broadcast failed after the event was removed from its old layer", { cause });
        this.name = "MoveRebroadcastError";
    }
}

function isBroadcastResponseFeature(value: unknown): value is PartialOCFeature {
    if (!value || typeof value !== "object") return false;

    const feature = value as Partial<PartialOCFeature>;
    const opencop = feature.properties?.opencop;

    return (
        feature.type === "Feature" &&
        !!opencop &&
        typeof opencop === "object" &&
        typeof opencop.id === "string" &&
        typeof opencop.objectId === "string" &&
        typeof opencop.source === "string" &&
        typeof opencop.displayName === "string" &&
        !!opencop.element &&
        typeof opencop.element === "object" &&
        !!opencop.timestampz &&
        typeof opencop.timestampz === "object"
    );
}

/**
 * Save the event currently being edited.
 *
 * @param mutationTarget The remote mutation route for an existing event, from
 *   `getEditability`.
 * @param move Relocate the event to the chosen layer instead of patching it in
 *   place: it is deleted from its current source, then broadcast to the target.
 */
export function useEventSave(
    mutationTarget?: EventMutationTarget,
    move = false,
): () => Promise<void> {
    const { getLatestGeojson } = useEventDetails();
    const queryClient = useQueryClient();
    const pickedRef = usePickedElementRef();
    const mapManager = useMapManager();
    const disabledSourcesArray = useSourcesWithState("disabled");
    const disabledSources = useMemo(() => new Set(disabledSourcesArray), [disabledSourcesArray]);
    const { get, getDraft, delete: deleteElements } = useElementActions();

    const { mutateAsync: createBroadcast } = useMutation(eventMutations.broadcastCreate());
    const { mutateAsync: patchBroadcast } = useMutation(eventMutations.broadcastPatch());
    const { mutateAsync: patchSourceBroadcast } = useMutation(
        eventMutations.broadcastSourcePatch(),
    );
    // Move-only. This is the raw remote delete; `useEventDelete` is the
    // user-facing delete and additionally drops the element from the
    // ElementStore and clears the selection — which would destroy the element
    // `applyBroadcastResponse` rebuilds and re-selects under its new key.
    const { mutateAsync: deleteBroadcast } = useMutation(eventMutations.broadcastDelete());

    return useCallback(async () => {
        const overlay = getOverlay();
        const geojson = getLatestGeojson();
        if (!geojson) {
            throw new Error("No geojson to save");
        }

        const originalSource = geojson.properties.opencop.source;
        // `||`, not `??`: clearing the combobox leaves `""` in the overlay, and
        // an empty target must fall back to the origin rather than re-key the
        // event into a bogus empty source.
        const broadcastTarget = (overlay?.source as string) || originalSource;
        // Captured before any mutation: a move's delete can evict the element
        // from the store mid-flight (the server republishes the deleted event as
        // stale, which ElementStore treats as a removal of the stored entry even
        // while it holds the draft). `buildUpdatedElement` falls back to this
        // snapshot when the live lookup comes back empty.
        const preMutationElement = pickedRef
            ? (getDraft() ?? get(getElementKey(pickedRef)))
            : undefined;

        // One typed decision keys create-vs-patch off the *original* source,
        // never the chosen target; this hook only dispatches.
        const action = planSave(
            originalSource,
            geojson.properties.opencop.objectId,
            broadcastTarget,
            mutationTarget,
            move,
        );
        switch (action.kind) {
            case "local":
                saveLocal();
                break;
            case "create":
                await saveBroadcastCreate(geojson, action.targetLayer);
                break;
            case "patch":
                await saveBroadcastPatch(geojson, action.target, action.objectId);
                break;
            case "move":
                await saveBroadcastMove(
                    geojson,
                    action.fromLayer,
                    action.objectId,
                    action.targetLayer,
                );
                break;
            default:
                // Compile error if a future SaveAction variant is added without a
                // case here; throws at runtime so a missed variant can't silently
                // skip the save.
                assertNever(action);
        }

        /**
         * Save a local-only element. Creates a new Element with overlay
         * values baked in so the store sees a new reference and the detail
         * panel refreshes.
         */
        function saveLocal() {
            const updatedElement = buildUpdatedElement();
            save(updatedElement);
        }

        function buildSourceFeature(geo: PartialOCFeature): PartialOCFeature {
            return stampBroadcastTimestamps(
                {
                    ...geo,
                    properties: {
                        opencop: {
                            ...geo.properties.opencop,
                            ...overlay,
                            timestampz: {
                                ...geo.properties.opencop.timestampz,
                                ...((overlay?.timestampz as Record<string, unknown>) ?? {}),
                            },
                        },
                    },
                },
                Date.now(),
            );
        }

        function applyBroadcastResponse(targetId: string, response: unknown) {
            const responseFeature = isBroadcastResponseFeature(response) ? response : undefined;
            const responseDetails = responseFeature?.properties.opencop;

            const updatedElement = buildUpdatedElement({
                id: responseDetails?.id,
                objectId: responseDetails?.objectId,
                source: responseDetails?.source,
                time: responseDetails?.timestampz.time ?? undefined,
                start: responseDetails?.timestampz.start ?? undefined,
                stale: responseDetails?.timestampz.stale ?? undefined,
            });
            save(updatedElement);

            const newRef = updatedElement ? toElementRef(updatedElement) : null;
            if (newRef) {
                const detailQueryKey = eventQueries.detail(
                    newRef.sourceId,
                    newRef.objectId,
                ).queryKey;

                queryClient.setQueryData<EventDetails | undefined>(detailQueryKey, (previous) =>
                    responseDetails
                        ? {
                              ...(previous ?? {}),
                              ...responseDetails,
                              id: responseDetails.id,
                              objectId: responseDetails.objectId,
                              source: responseDetails.source,
                              displayName: responseDetails.displayName,
                              element: responseDetails.element,
                              timestampz: responseDetails.timestampz,
                          }
                        : previous,
                );

                queryClient.invalidateQueries({ queryKey: detailQueryKey });
            }

            if (disabledSources.has(targetId) || disabledSources.has(broadcastTarget)) {
                if (newRef) {
                    const element = get(getElementKey(newRef));
                    // Routed through the element actions so the owning source
                    // performs the delete (local rows live in LocalSource).
                    if (element) deleteElements(element.key);
                }
                mapManager.clearSelection();
            }
        }

        async function saveBroadcastCreate(geo: PartialOCFeature, targetLayer: string) {
            const response = await createBroadcast({
                targetLayer,
                feature: buildSourceFeature(geo),
            });
            applyBroadcastResponse(targetLayer, response);
        }

        async function saveBroadcastPatch(
            geo: PartialOCFeature,
            target: { kind: "layer" | "source"; id: string },
            objectId: string,
        ) {
            const feature = buildSourceFeature(geo);
            if (target.kind === "source") {
                const response = await patchSourceBroadcast({
                    sourceId: target.id,
                    objectId,
                    feature,
                });
                applyBroadcastResponse(target.id, response);
                return;
            }
            const response = await patchBroadcast({ targetLayer: target.id, objectId, feature });
            applyBroadcastResponse(target.id, response);
        }

        /**
         * Relocate a layer event: drop it from the layer it currently lives on,
         * then broadcast the edited feature to the chosen layer. (`planSave`
         * refuses moves for data-source events, so there is no source route.)
         *
         * The delete runs first so the event is never live in both sources at
         * once (two copies would stream in over the WebSocket, and the backend
         * keys events by objectId within a source). The cost of that ordering is
         * that a failed create leaves the event deleted from its old layer —
         * that failure is rethrown as `MoveRebroadcastError` so the form can
         * tell the user the event is already gone, instead of implying the save
         * changed nothing.
         *
         * The feature is serialised *before* either mutation: `buildSourceFeature`
         * reads the live element, and nothing must depend on it surviving the
         * delete.
         */
        async function saveBroadcastMove(
            geo: PartialOCFeature,
            fromLayer: string,
            objectId: string,
            targetLayer: string,
        ) {
            const feature = buildSourceFeature(geo);

            await deleteBroadcast({ targetLayer: fromLayer, objectId });

            let response: unknown;
            try {
                response = await createBroadcast({ targetLayer, feature });
            } catch (error) {
                // The delete has already committed; the toast must say so.
                throw new MoveRebroadcastError(error);
            }

            // Applied before the old cache entry is dropped: this re-adds the
            // element under its new key and re-selects it, so the detail panel
            // never observes a window with no selected event.
            applyBroadcastResponse(targetLayer, response);
            queryClient.removeQueries({
                queryKey: eventQueries.detail(fromLayer, objectId).queryKey,
            });
        }

        /**
         * Build a new Element with overlay edits applied. The new reference
         * triggers usePickedElement → useEventDetails to re-compute, so the
         * detail panel shows updated values. Geometry and style references
         * are shared with the original (already mutated during editing).
         */
        function buildUpdatedElement(overrides?: {
            id?: string;
            objectId?: string;
            source?: string;
            time?: number;
            start?: number;
            stale?: number;
        }): Element | undefined {
            if (!overlay || !pickedRef) return undefined;
            // The live entry wins so a WebSocket update that landed mid-save is
            // not discarded; the fallback only covers the evicted case.
            const element = getDraft() ?? get(getElementKey(pickedRef)) ?? preMutationElement;
            if (!element) return undefined;

            const ts = overlay.timestampz as Record<string, unknown> | undefined;
            return new Element({
                type: element.type,
                id: overrides?.id ?? element.id,
                objectId: overrides?.objectId ?? element.objectId,
                source: overrides?.source ?? (overlay.source as string) ?? element.source,
                displayName: (overlay.displayName as string) ?? element.getDisplayName(),
                start: overrides?.start ?? (ts?.start as number) ?? element.start,
                stale: overrides?.stale ?? (ts?.stale as number) ?? element.stale,
                time: overrides?.time ?? Date.now(),
                heading: element.heading,
                course: element.course,
                speed: element.speed,
                hae: element.hae,
                code: element.getCode(),
                modifiers: { ...element.getModifiers() },
                geometry: element.geometry,
                style: element.getStyle(),
                attributes:
                    (overlay.attributes as Record<string, unknown>) ?? element.getAttributes(),
            });
        }
    }, [
        get,
        deleteElements,
        createBroadcast,
        deleteBroadcast,
        getLatestGeojson,
        patchBroadcast,
        patchSourceBroadcast,
        queryClient,
        mapManager,
        move,
        mutationTarget,
        disabledSources,
        pickedRef,
        getDraft,
    ]);
}
