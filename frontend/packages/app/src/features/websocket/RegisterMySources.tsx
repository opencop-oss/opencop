// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useEffect, useMemo, useState, useSyncExternalStore } from "react";
import { useWebSocket } from "./WebSocketContext";
import {
    destinationPrefix,
    useElementActions,
    useContributeSources,
    useLocalSource,
    useSourceActions,
    WebSocketSource,
} from "@opencop/ingest";
import { useQuery } from "@tanstack/react-query";
import { union } from "lodash";
import { layerQueries } from "../layers/layers.queries";
import { dataSourceQueries } from "../data-sources/data-sources.queries";
import { useFilterPanelStore } from "../filters/filter-panel.store";
import { useHistoricalMode } from "../historical/data-plane/historical.store";

/** Registers sources to controller. */
function createLiveSourceIdsStore(initialIds: string[] = []) {
    let snapshot = initialIds;
    const listeners = new Set<() => void>();

    return {
        getSnapshot: () => snapshot,
        subscribe: (listener: () => void) => {
            listeners.add(listener);
            return () => listeners.delete(listener);
        },
        setSnapshot: (nextIds: string[]) => {
            // Compare as sets: source ids form an unordered collection, so a
            // reordering that leaves the membership unchanged must not churn the
            // snapshot (a new array identity would re-render every subscriber).
            const prev = new Set(snapshot);
            if (snapshot.length === nextIds.length && nextIds.every((id) => prev.has(id))) {
                return;
            }

            snapshot = nextIds;
            listeners.forEach((listener) => listener());
        },
    };
}

export function RegisterMySources() {
    const { client } = useWebSocket();
    const { disable } = useSourceActions();
    const { add } = useElementActions();
    const localSource = useLocalSource();

    // Historical mode owns the map's data plane via its own WS subscription
    // (`/user/queue/history`). The isolation boundary is the desired source set
    // below: in historical mode it is empty, so the controller reconciles every
    // live source away (unsubscribing and purging its elements) along with the
    // local source. The raw init snapshot is ignored for the same reason, since
    // it arrives outside the controller. Returning to live re-admits them and the
    // backend re-sends the current picture.
    const historical = useHistoricalMode() === "historical";

    const {
        data: layerInfos,
        isSuccess: hasLayerInfos,
        isError: layerInfosError,
    } = useQuery(layerQueries.myList());
    const {
        data: dataSourceInfos,
        isSuccess: hasDataSourceInfos,
        isError: dataSourceInfosError,
    } = useQuery(dataSourceQueries.myList());
    const liveSourceListsReady =
        !historical &&
        hasLayerInfos &&
        hasDataSourceInfos &&
        !layerInfosError &&
        !dataSourceInfosError;

    const [liveSourceIdsStore] = useState(createLiveSourceIdsStore);
    const resolvedLiveIds = useSyncExternalStore(
        liveSourceIdsStore.subscribe,
        liveSourceIdsStore.getSnapshot,
        liveSourceIdsStore.getSnapshot,
    );

    useEffect(() => {
        if (!liveSourceListsReady) return;

        // Only successful query pairs are allowed to change the desired source
        // set. Keeping the last successful ids during loading/error states
        // prevents transient query shrinkage from reconciling away live sources
        // and purging their elements.
        liveSourceIdsStore.setSnapshot(
            layerInfos
                .map((info) => info.layerId)
                .concat(dataSourceInfos.map((info) => info.serverId)),
        );
    }, [liveSourceListsReady, layerInfos, dataSourceInfos, liveSourceIdsStore]);

    const ids = useMemo(() => (historical ? [] : resolvedLiveIds), [historical, resolvedLiveIds]);

    // The complete desired source set for the current mode. Reconciling to this
    // set *is* the mode switch: entering historical mode reconciles to an empty
    // live set, so the controller disables the WebSocket sources and the local
    // source and purges their elements from the store; leaving re-admits them,
    // and the backend re-sends the live picture while `LocalSource` re-hydrates
    // from IndexedDB. There is no imperative teardown anywhere.
    const sources = useMemo(
        () =>
            historical
                ? []
                : [
                      ...ids.map((id) =>
                          new WebSocketSource(id, client, destinationPrefix + id).bind(add),
                      ),
                      localSource,
                  ],
        [historical, ids, client, add, localSource],
    );

    // Contribute the live slice of the desired source set. The historical
    // feature contributes its own slice; one reconciler unions them.
    useContributeSources("live", sources);

    // Initialize filter panel visibility for sources the user has never seen before.
    // Once a source ID is recorded in knownSources, this effect skips it on future
    // runs — preserving any manual toggles the user has made since.
    useEffect(() => {
        if (historical || !liveSourceListsReady) return;

        const allInfos = [
            ...layerInfos.map((l) => ({ id: l.layerId, defaultVisible: l.defaultVisible })),
            ...dataSourceInfos.map((ds) => ({
                id: ds.serverId,
                defaultVisible: ds.defaultVisible,
            })),
        ];
        const allIds = new Set(allInfos.map((s) => s.id));

        const { knownSources, excludedIds, pinnedSources, initNewSources, removeKnownSources } =
            useFilterPanelStore.getState();

        // Reconcile stale ids (access revoked or deleted in another session). Runs before the
        // empty-list return so a user who lost every source is pruned too. Archived ids are absent
        // from the ACTIVE myList and are pruned here on purpose: restoring one returns it to the
        // panel with default visibility.
        const stale = union(knownSources, excludedIds, pinnedSources).filter(
            (id) => !allIds.has(id),
        );
        if (stale.length > 0) removeKnownSources(stale);

        if (allInfos.length === 0) return;

        const knownSet = new Set(knownSources);

        const newInfos = allInfos.filter((s) => !knownSet.has(s.id));
        if (newInfos.length === 0) return;

        initNewSources(newInfos);

        const toHide = newInfos.filter((s) => s.defaultVisible === false).map((s) => s.id);
        toHide.forEach((id) => {
            disable(id);
        });
    }, [historical, liveSourceListsReady, layerInfos, dataSourceInfos, disable]);

    return null;
}
