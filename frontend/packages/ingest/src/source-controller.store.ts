// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import Logger from "js-logger";
import { Source, type SourceState } from "./sources/Source";
import { create, type StoreApi, type UseBoundStore } from "zustand";

const logger = Logger.get("source_controller");

export type SourceControllerStore = UseBoundStore<StoreApi<SourceControllerState>>;
export type SourceInfo = Record<string, SourceState>;
type SourceId = string;

export interface SourceControllerState {
    sourceStates: SourceInfo;
    /**
     * Reconcile one **named slice** of the desired source set.
     *
     * The controller keeps the union of every slice as its live source set. On
     * each call it replaces `namespace`'s slice, recomputes the union, then
     * evicts sources no longer in it (disabling each and purging its elements)
     * and admits sources newly in it. Withdrawing a contributor is
     * `reconcile(namespace, [])`.
     *
     * Slices exist because the desired set is owned by different features
     * mounted at different depths (live sources near the router, the historical
     * session inside the map view); each contributes its own slice by name and
     * never has to know about the others. On an id already present, the existing
     * instance is kept and the incoming one dropped — callers rely on this
     * (e.g. a historical subject source must not silently fail to register
     * behind a live source for the same layer, which is why historical ids are
     * namespaced).
     *
     * @param namespace Stable per-contributor key.
     * @param sources The contributor's current desired sources.
     */
    reconcile(namespace: string, sources: Iterable<Source>): void;
    /**
     * Enable a {@link Source} (subscribe / start streaming). No-op if it is not
     * in the current union or is already enabled/syncing.
     */
    enable(sourceId: string): void;
    /**
     * Disable a {@link Source} and purge its elements from the {@link ElementStore}.
     */
    disable(sourceId: string): void;
    /** Recompute {@link sourceStates} from the live sources' current states. */
    syncStates(): void;
}

interface CreateSourceControllerStoreOptions {
    purgeSource?: (sourceId: string) => void;
}

export function createSourceControllerStore(
    options?: CreateSourceControllerStoreOptions,
): SourceControllerStore {
    /** The live union — one entry per source id currently admitted. */
    const sources = new Map<SourceId, Source>();
    /** Each contributor's latest desired slice, keyed by namespace. */
    const slices = new Map<string, Source[]>();
    const purgeSource = options?.purgeSource ?? (() => {});

    const disableAndPurge = (source: Source) => {
        source.disable();
        // A source may hold rows under ids other than its own (see
        // `Source.ownedSourceIds`), so purge everything it owns.
        source.ownedSourceIds.forEach((sourceId) => purgeSource(sourceId));
    };

    /** The union of all slices, keyed by id, keeping the first-seen instance. */
    const computeUnion = (): Map<SourceId, Source> => {
        const union = new Map<SourceId, Source>();
        // Tracks which namespace first claimed each id, so a collision warning
        // can name both offending contributors instead of just the shared id.
        const claimedBy = import.meta.env.DEV ? new Map<SourceId, string>() : null;
        for (const [namespace, slice] of slices) {
            for (const source of slice) {
                // "Keep first" must hold in production too, not just DEV — the
                // membership check is the invariant; only the diagnostic naming
                // (and `claimedBy`) is DEV-only.
                if (union.has(source.id)) {
                    if (claimedBy) {
                        logger.warn(
                            `Source id "${source.id}" is contributed by both the ` +
                                `"${claimedBy.get(source.id)}" and "${namespace}" slices; ` +
                                `keeping the first. Two features are claiming the same source — ` +
                                `give each a distinct id or a single owning namespace.`,
                        );
                    }
                    continue;
                }
                union.set(source.id, source);
                claimedBy?.set(source.id, namespace);
            }
        }
        return union;
    };

    return create<SourceControllerState>()((set, get) => ({
        sourceStates: {},

        reconcile(namespace, incoming) {
            slices.set(namespace, [...incoming]);
            const union = computeUnion();

            // Evict sources that dropped out of the union.
            for (const [id, source] of [...sources]) {
                if (!union.has(id)) {
                    disableAndPurge(source);
                    sources.delete(id);
                }
            }

            // Admit newly-present sources. On an id match the existing instance
            // is already in `sources`, so it is kept and the incoming dropped.
            for (const [id, source] of union) {
                if (!sources.has(id)) sources.set(id, source);
            }

            get().syncStates();
        },

        enable(sourceId) {
            const source = sources.get(sourceId);
            if (!source || source.state === "enabled" || source.state === "syncing") return;

            source.enable((sourceState) =>
                set((s) => ({ sourceStates: { ...s.sourceStates, [source.id]: sourceState } })),
            );
            logger.debug(`Enabled source ${source.id}`);

            get().syncStates();
        },

        disable(sourceId: string) {
            const source = sources.get(sourceId);
            if (!source) return;

            disableAndPurge(source);
            logger.debug(`Disabled source ${source.id}.`);

            get().syncStates();
        },

        syncStates() {
            set(() => {
                const sourceStates: SourceInfo = {};

                for (const [id, source] of sources) {
                    sourceStates[id] = source.state;
                }

                return { sourceStates };
            });
        },
    }));
}
