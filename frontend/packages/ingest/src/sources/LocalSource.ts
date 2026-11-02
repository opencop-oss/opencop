// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { elementFromFeature } from "@opencop/elements";
import type { Element, PartialOCFeature } from "@opencop/elements";
import { Source } from "./Source";
import Dexie from "dexie";
import Logger from "js-logger";

const logger = Logger.get("local_source");

type PersistedLocalFeature = PartialOCFeature & { key: string; source: string };
type LegacyPersistedLocalFeature = PartialOCFeature & { key: string; source?: string };

export class OpenCOPDatabase extends Dexie {
    elements!: Dexie.Table<PersistedLocalFeature, string>;

    constructor() {
        super("opencop");
        this.version(1).stores({ elements: "key, source, objectId" });
        // version(2) switches to GeoJSON storage and backfills the top-level
        // indexed `source` field for any early rows that still only carry the
        // nested `properties.opencop.source` value.
        this.version(2)
            .stores({ elements: "key, source, objectId" })
            .upgrade(async (tx) => {
                await tx
                    .table("elements")
                    .toCollection()
                    .modify((row: LegacyPersistedLocalFeature) => {
                        const source = row.properties?.opencop?.source;
                        if (!row.source && source) row.source = source;
                    });
            });
    }
}

export const db = new OpenCOPDatabase();

/**
 * Local {@link Source} for locally-drawn elements. Persists to IndexedDB via
 * Dexie so elements survive browser refresh.
 *
 * Elements are stored as {@link PartialOCFeature} GeoJSON blobs (via
 * {@link Element.toFeature}) and reconstructed on load via
 * {@link elementFromFeature}.
 *
 * {@link persist} and {@link remove} are called internally by {@link add} /
 * {@link delete}, which own the IndexedDB write. Callers mutate local data
 * through `add`/`delete` — never `persist`/`remove` directly. (The store no
 * longer mirrors deletes back into IndexedDB; ownership is inverted — see
 * `IngestProvider`.)
 */
export class LocalSource extends Source {
    /**
     * Bumped on every `onEnable`. The IndexedDB read is async, and the `LocalSource`
     * singleton is disabled/re-enabled on every live↔historical toggle, so a read
     * from an earlier enable cycle could resolve after a newer one started — the
     * `_state === "disabled"` checks below don't catch that (the newer enable set
     * the state back to `"syncing"`). Bailing on a generation mismatch does.
     */
    private _generation = 0;

    protected onEnable(): void {
        logger.info("onEnable — querying DB for source:", this.id);
        const generation = ++this._generation;
        db.elements
            .where("source")
            .equals(this.id)
            .toArray()
            .then((rows) => {
                logger.info(`DB returned ${rows.length} row(s) for source "${this.id}"`);
                if (this._state === "disabled" || generation !== this._generation) {
                    logger.info("Source disabled/superseded before DB query resolved — aborting");
                    return;
                }
                const elements: Element[] = [];
                for (const row of rows) {
                    try {
                        const el = elementFromFeature(row);
                        if (el) {
                            elements.push(el);
                        } else {
                            logger.warn("elementFromFeature returned null/undefined for row:", row);
                        }
                    } catch (err) {
                        logger.warn("Skipping invalid persisted element:", err);
                    }
                }
                logger.info(`Hydrated ${elements.length} element(s) from ${rows.length} row(s)`);
                if (elements.length > 0) {
                    logger.info(
                        "Dispatching elements to handler:",
                        elements.map((e) => e.key),
                    );
                    this.handler?.(elements);
                } else {
                    logger.info("No elements to dispatch — handler not called");
                }
                this.setState("enabled");
            })
            .catch((err) => {
                logger.warn("Failed to load persisted elements:", err);
                if (this._state === "disabled" || generation !== this._generation) {
                    logger.info(
                        "Source disabled/superseded before DB query rejected — keeping state",
                    );
                    return;
                }
                logger.info("Falling back to an empty local source after hydration failure");
                this.setState("enabled");
            });
    }

    protected onDisable(): void {
        // Nothing to tear down — no subscription, no hooks.
    }

    /**
     * Add locally-drawn element(s): persist them and project them into the store.
     *
     * This source owns its backing store (IndexedDB); the `ElementStore` is a
     * projection of it, exactly as the WebSocket source's store rows are a
     * projection of the backend. Callers mutate local data through here, never
     * by writing the store directly — that is what lets a source be *evicted*
     * (disabled on a mode switch) without destroying the user's data.
     */
    add(elements: Element | Element[]): void {
        const list = Array.isArray(elements) ? elements : [elements];
        if (list.length === 0) return;
        for (const element of list) this.persist(element);
        // One dispatch for the batch so the store emits a single "add".
        this.handler?.(list);
    }

    /**
     * Delete locally-drawn element(s): remove the persisted rows and project the
     * removal into the store as **stale-dated** elements.
     *
     * This is the same mechanism a WebSocket delete uses — the backend sends an
     * element whose `stale` is in the past and {@link ElementStore} treats it as
     * a delete broadcast — so the store has exactly one delete path for all
     * live-class data. `clone(true, …)` keeps `objectId` and `source`, so the
     * tombstone carries the same `key` as the element it retires.
     */
    delete(elements: Element | Element[]): void {
        const list = Array.isArray(elements) ? elements : [elements];
        if (list.length === 0) return;
        const staleAt = Date.now() - 1;
        for (const element of list) this.remove(element.key);
        this.handler?.(list.map((element) => element.clone(true, staleAt)));
    }

    /** Persist an element to IndexedDB. Fire-and-forget; does not block the save path. */
    persist(element: Element): void {
        logger.info("persist called for element key:", element.key);
        const feature = { ...element.toFeature(), key: element.key, source: element.source };
        db.elements
            .put(feature)
            .then(() => logger.info("persist succeeded for key:", element.key))
            .catch((err) => {
                logger.warn("Failed to persist element:", err);
            });
    }

    /** Remove a persisted element from IndexedDB by its store key. */
    remove(key: string): void {
        logger.info("remove called for key:", key);
        db.elements
            .delete(key)
            .then(() => logger.info("remove succeeded for key:", key))
            .catch((err) => {
                logger.warn("Failed to remove element:", err);
            });
    }
}
