// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useCallback, useMemo, useRef, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { useSourcesWithState } from "@opencop/ingest";
import { getElementKey, type Element } from "@opencop/elements";
import type { MyLayerInfo } from "@opencop/api";
import type { Editability } from "~/features/map/editability";
import { eventMutations } from "~/features/events/events.queries";
import { useFilterPanelStore } from "~/features/filters/filter-panel.store";
import { useToast } from "~/features/notifications/use-toast.hook";
import { useAreaSelectionStore } from "./area-selection.store";
import { buildCopyFeature, isOnLayer, isStaleForCopy } from "./copy-to-layer.util";
import { BULK_REQUEST_BATCH_SIZE, settleInBatches } from "./settle-in-batches.util";
import type { SelectionEditability } from "./use-selection-editability.hook";

/**
 * Copying needs editor-or-higher on wherever the element lives now. That is exactly the
 * condition `getEditability` reports as `canDelete` for every origin (local, layer, data
 * source), so it is read from there.
 *
 * This is a product rule, not a backend one: the backend only checks editor-or-higher on the
 * target layer and never looks at the origin, since the client supplies the payload. Copying
 * only reads the origin, so the rule is stricter than the data flow needs; it is deliberate and
 * follows bulk delete's all-or-nothing pattern.
 */
function canCopyFrom(access: Editability | undefined): boolean {
    return access?.canDelete ?? false;
}

/**
 * How a `copySelected` call ended: `"ran"` once requests were sent (whatever their outcome),
 * `"busy"` when another copy is already running so this call did nothing, and `"skipped"` when a
 * guard (permissions, nothing checked, read-only items) stopped it before any request.
 */
type CopyRunResult = "ran" | "busy" | "skipped";

interface BulkCopyToLayer {
    /** Checked elements the user can't copy because they aren't editor-or-higher on their origin. */
    blockedElements: readonly Element[];
    /** How many checked elements are past their stale time right now, and so would be skipped. */
    countStale: () => number;
    /** How many checked, non-stale elements sit on each layer, keyed by layer id. */
    countFreshByLayer: () => Record<string, number>;
    /** Whether every checked element is past its stale time, as of the last change to the checked set. */
    allStale: boolean;
    isCopying: boolean;
    copySelected: (target: Pick<MyLayerInfo, "layerId" | "layerName">) => Promise<CopyRunResult>;
}

/**
 * Copies the checked elements into another layer — all or nothing on permission: while any
 * checked element is read-only (`blockedElements`), or while `permissionStatus` isn't "ready",
 * `copySelected` does nothing, and a call made while a copy is already running is ignored.
 * `access` must cover every checked element.
 *
 * Elements past their stale time are skipped rather than sent: the backend rejects a create
 * whose stale time has passed, and staleness changes with time, so unchecking can't fix it
 * the way it fixes a permission block. In practice this is a guard for the sub-second window
 * before the element store evicts an expired element and the selection drops it (see
 * `isStaleForCopy`), not something users normally hit. Elements already on the destination layer
 * are skipped too, so a mixed selection stays copyable without making accidental duplicates.
 * Both kinds of skip are counted in the result toast.
 *
 * There is no batch-create endpoint, so one request fires per element, `BULK_REQUEST_BATCH_SIZE`
 * at a time. The backend mints a new id for every copy, so nothing collides; copies reach the
 * map through the target layer's existing subscription, so nothing is written to the element
 * store here. Copied elements are unchecked afterwards so retrying a partial failure resends
 * only what failed — there is no automatic retry, since a create that actually landed would be
 * duplicated.
 *
 * Known limitation: remarks, contacts, kind, version and extensions are not copied. The element
 * store never holds them (they come from the per-event REST detail fetch, not the websocket), so
 * `toFeature()` can't emit them. Remarks are also their own sub-resource, not part of the create
 * payload. Fixing it means changing `Element` / `elementFromFeature`.
 */
export function useBulkCopyToLayer(
    checkedElements: readonly Element[],
    access: Pick<SelectionEditability, "editability" | "permissionStatus">,
): BulkCopyToLayer {
    const { editability, permissionStatus } = access;
    const { t } = useTranslation();
    const { showToast } = useToast();
    const hiddenSources = useFilterPanelStore((s) => s.hiddenSources);
    const disabledSources = useSourcesWithState("disabled");
    const { mutateAsync: createBroadcast } = useMutation(eventMutations.broadcastCreate());
    const [isCopying, setIsCopying] = useState(false);
    // Guards programmatic same-task calls (two `copySelected()` before a render commits), which
    // `isCopying` can't see. User clicks are already blocked by the loading Confirm button.
    const copyingRef = useRef(false);

    const blockedElements = useMemo(
        () =>
            permissionStatus === "ready"
                ? checkedElements.filter(
                      (element) => !canCopyFrom(editability.get(getElementKey(element))),
                  )
                : [],
        [checkedElements, editability, permissionStatus],
    );

    const countStale = useCallback(() => {
        const now = Date.now();
        return checkedElements.filter((element) => isStaleForCopy(element, now)).length;
    }, [checkedElements]);

    const countFreshByLayer = useCallback(() => {
        const now = Date.now();
        const counts: Record<string, number> = {};
        for (const element of checkedElements) {
            if (isStaleForCopy(element, now)) continue;
            counts[element.source] = (counts[element.source] ?? 0) + 1;
        }
        return counts;
    }, [checkedElements]);

    // The one render-time clock read: Copy's disabled reason has to be known at render. It is a
    // snapshot as of the last change to the checked set, which is enough because an expired element
    // is evicted (changing the set) within about half a second, and `copySelected` filters again.
    const allStale = useMemo(() => {
        const now = Date.now();
        return checkedElements.length > 0 && checkedElements.every((e) => isStaleForCopy(e, now));
    }, [checkedElements]);

    const copySelected = useCallback(
        async (target: Pick<MyLayerInfo, "layerId" | "layerName">): Promise<CopyRunResult> => {
            if (copyingRef.current) return "busy";
            if (
                permissionStatus !== "ready" ||
                checkedElements.length === 0 ||
                blockedElements.length > 0
            ) {
                return "skipped";
            }

            copyingRef.current = true;
            setIsCopying(true);
            try {
                const now = Date.now();
                const fresh = checkedElements.filter((element) => !isStaleForCopy(element, now));
                const skippedStale = checkedElements.length - fresh.length;
                // Already on the destination: a copy there would only be a duplicate.
                const copyable = fresh.filter((element) => !isOnLayer(element, target.layerId));
                const skippedSameLayer = fresh.length - copyable.length;

                // Batched because the lasso has no selection-size cap, so an unbatched copy could
                // put hundreds of creates in flight at once.
                const outcomes = await settleInBatches(
                    copyable,
                    BULK_REQUEST_BATCH_SIZE,
                    (element) =>
                        createBroadcast({
                            targetLayer: target.layerId,
                            feature: buildCopyFeature(element, target.layerId, now),
                        }),
                );

                const copiedKeys: string[] = [];
                outcomes.forEach((outcome, i) => {
                    if (outcome.status === "fulfilled") copiedKeys.push(getElementKey(copyable[i]));
                });
                const failed = outcomes.length - copiedKeys.length;
                useAreaSelectionStore.getState().setChecked(copiedKeys, false);

                const parts: string[] = [];
                if (copiedKeys.length > 0) {
                    parts.push(t("selectionPanel.copiedCount", { count: copiedKeys.length }));
                }
                if (skippedStale > 0) {
                    parts.push(t("selectionPanel.skippedStaleCount", { count: skippedStale }));
                }
                if (skippedSameLayer > 0) {
                    parts.push(
                        t("selectionPanel.skippedSameLayerCount", { count: skippedSameLayer }),
                    );
                }
                if (failed > 0) parts.push(t("selectionPanel.failedCount", { count: failed }));
                // Two separate reasons the copies won't show: the filter panel hides the layer,
                // or ingest has the source disabled (the save flow checks the latter).
                if (
                    copiedKeys.length > 0 &&
                    (hiddenSources.includes(target.layerId) ||
                        disabledSources.includes(target.layerId))
                ) {
                    parts.push(
                        t("selectionPanel.copyHiddenLayerNote", { layer: target.layerName }),
                    );
                }

                if (failed > 0) {
                    showToast({
                        type: "error",
                        title:
                            copiedKeys.length === 0
                                ? t("selectionPanel.copyFailedTitle")
                                : t("selectionPanel.copyPartialTitle"),
                        description: parts.join(" "),
                    });
                } else if (copiedKeys.length === 0) {
                    showToast({
                        type: "warning",
                        title: t("selectionPanel.copyNothingTitle"),
                        description: parts.join(" "),
                    });
                } else {
                    showToast({
                        type: skippedStale + skippedSameLayer > 0 ? "warning" : "success",
                        title: t("selectionPanel.copySuccessTitle", { layer: target.layerName }),
                        description: parts.join(" "),
                    });
                }
                return "ran";
            } finally {
                copyingRef.current = false;
                setIsCopying(false);
            }
        },
        [
            blockedElements.length,
            checkedElements,
            createBroadcast,
            disabledSources,
            hiddenSources,
            permissionStatus,
            showToast,
            t,
        ],
    );

    return { blockedElements, countStale, countFreshByLayer, allStale, isCopying, copySelected };
}
