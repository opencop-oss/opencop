// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { createContext, use, useState, type PropsWithChildren } from "react";
import { localEventSource } from "@opencop/elements";
import { ElementStore } from "../ElementStore";
import {
    createSourceControllerStore,
    type SourceControllerStore,
} from "../source-controller.store";
import { LocalSource } from "../sources/LocalSource";

export interface IngestContextValue {
    store: ElementStore;
    controller: SourceControllerStore;
    /**
     * The locally-drawn-element source. Exposed because it *owns* local data
     * (IndexedDB): local mutations are routed to it rather than written straight
     * to the store, so the store stays a pure projection of its sources.
     */
    localSource: LocalSource;
    policy?: {
        shouldEnableOnRegister?: (sourceId: string) => boolean;
    };
}
const IngestContext = createContext<IngestContextValue | null>(null);

export interface IngestProviderProps extends PropsWithChildren {
    policy?: IngestContextValue["policy"];
}

export function IngestProvider({ children, policy }: IngestProviderProps) {
    const [value] = useState<IngestContextValue>(() => {
        const store = new ElementStore();
        const controller = createSourceControllerStore({
            purgeSource: store.deleteBySource.bind(store),
        });
        const localSource = new LocalSource(localEventSource).bind(store.add.bind(store));

        // `localSource` is NOT registered here. Like every other source it is
        // supplied to the controller's `reconcile` as part of the desired source
        // set for the current mode (see `RegisterMySources`), so entering
        // historical mode evicts it — clearing its rows from the store while its
        // IndexedDB rows survive — and leaving re-admits it, re-hydrating them.

        // NOTE: persistence is deliberately NOT a mirror of the store.
        //
        // It used to be: `store.on("remove") -> localSource.remove(key)`. That made
        // IndexedDB follow the store, so *any* store removal deleted user data —
        // including `deleteBySource`, which is how a source is purged when it is
        // merely disabled. Evicting the local source would therefore erase the
        // user's drawings. Ownership is now inverted: `LocalSource` owns IndexedDB
        // and projects into the store (see `LocalSource.add`/`delete`), so eviction
        // ("the source went away") and deletion ("the user removed this") are
        // distinct operations.
        //
        // The ONE store-driven removal we DO mirror is genuine stale expiry: when
        // the sweep evicts a local element because its user-set `stale` time
        // passed (the `expire` event, NOT the `remove` event), its persisted row
        // must go too — otherwise it lingers in IndexedDB forever, rehydrated as a
        // stale no-op on every load. This is safe precisely because `expire` fires
        // only on expiry, never on source eviction.
        store.on("expire", (elements) => {
            for (const element of elements) {
                if (element.source === localEventSource) localSource.remove(element.key);
            }
        });

        return { store, controller, localSource, policy };
    });

    return <IngestContext value={value}>{children}</IngestContext>;
}

/** @internal DO NOT USE OUTSIDE OF `@opencop/ingest`. Exported for current coupling in the map/historical data planes (`MapManagerContext`, `HistoricalDataContext`). */
export function useIngestContext(): IngestContextValue {
    const ctx = use(IngestContext);
    if (!ctx) throw new Error("useIngestContext must be used within an IngestProvider");
    return ctx;
}
