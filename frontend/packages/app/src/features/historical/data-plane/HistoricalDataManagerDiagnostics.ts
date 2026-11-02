// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * DEV-only instrumentation for {@link HistoricalDataManager}. Tracks
 * playhead-bucket transitions and provides analytical dumps over the
 * pipeline + store contents. Chunk- and decode-related diagnostics live
 * in {@link HistoricalSessionDiagnostics} alongside the side-tables they
 * write into.
 *
 * Augments the `globalThis.__historicalDebug` bridge installed by the source
 * diagnostics with `dumpObjects()`, `dumpPlayhead()`, and `dumpUnrendered()`.
 * Production keeps `null` for the diagnostics reference and the manager's hot
 * paths skip every diagnostic call via `?.`.
 */
import type { Element } from "@opencop/elements";
import Logger from "js-logger";

import type { HistoricalDataManager } from "./HistoricalDataManager";

const logger = Logger.get("historical");

interface PerObjectInfo {
    firstBucketIndex: number | null;
    bucketsPresent: Set<number>;
    samplesPerBucket: Map<number, number>;
}

/** Hooks the manager calls in DEV mode. Production keeps `null` for this. */
export interface HistoricalDataManagerDiagnostics {
    onPlayheadBucketChange(prevIndex: number, nextIndex: number, playheadMs: number | null): void;
    dispose(): void;
}

class HistoricalDataManagerDiagnosticsImpl implements HistoricalDataManagerDiagnostics {
    private readonly manager: HistoricalDataManager;

    constructor(manager: HistoricalDataManager) {
        this.manager = manager;
        const bridge = globalThis.__historicalDebug;
        if (bridge && bridge.source === manager.session) {
            bridge.dumpObjects = () => this.dumpObjectMembership();
            bridge.dumpPlayhead = () => {
                const diag = this.computeDisplayDiagnostics(this.manager.activeBucketIndex);
                logger.info(
                    `[historical-debug] playhead bucket=${diag.bucketIndex}\n` +
                        `  displayed: ${diag.displayedCount} samples / ${diag.displayedObjectIds} objects\n` +
                        `  totalKnownObjects: ${diag.totalObjectIds}`,
                );
                return diag;
            };
            bridge.dumpUnrendered = () => this.dumpUnrendered();
        }
    }

    onPlayheadBucketChange(prevIndex: number, nextIndex: number, _playheadMs: number | null): void {
        void _playheadMs;
        const diag = this.computeDisplayDiagnostics(nextIndex);
        logger.info(
            `playhead bucket change: ${prevIndex} → ${nextIndex} | ` +
                `displayed=${diag.displayedCount} (uniqObj=${diag.displayedObjectIds}) | ` +
                `totalKnownObjects=${diag.totalObjectIds}`,
        );
    }

    dispose(): void {
        const bridge = globalThis.__historicalDebug;
        if (bridge && bridge.source === this.manager.session) {
            bridge.dumpObjects = undefined;
            bridge.dumpPlayhead = undefined;
            bridge.dumpUnrendered = undefined;
        }
    }

    // -----------------------------------------------------------------------
    // Dump methods (callable via `globalThis.__historicalDebug` in DEV)
    // -----------------------------------------------------------------------

    /**
     * Count store elements that can never render — the "arrived but not
     * displayed" failure mode.
     *
     * An element only draws when its `filterValue` (`effectiveStaleMs -
     * windowStartMs`) lands inside a playhead window over the plan's
     * `[0, totalDuration]` span. A chunk decoded before the plan event is tagged
     * with `windowStartMs = 0`, so its `filterValue` is an absolute epoch far
     * outside that band (or `+Infinity`) — retained by the store, but
     * GPU-filtered at every playhead position. This reports those.
     */
    private dumpUnrendered(): {
        storeEntries: number;
        storeUniqueObjectIds: number;
        windowStartMs: number | null;
        reachableRange: { minRel: number; maxRel: number } | null;
        unreachable: number;
        infinite: number;
        samplesUnreachable: number[];
    } {
        // An element is reachable when its plan-relative filterValue sits within
        // one bucket-width of the plan's chunk span; outside that it's filtered
        // at every playhead. Null plan means we can't judge reachability yet.
        const plan = this.manager.session.getPlan();
        const reachableRange =
            plan === null
                ? null
                : {
                      minRel: -plan.bucketWidthMs,
                      maxRel: plan.totalChunkCount * plan.bucketWidthMs + plan.bucketWidthMs,
                  };

        let storeEntries = 0;
        let unreachable = 0;
        let infinite = 0;
        const objectIds = new Set<string>();
        const samplesUnreachable: number[] = [];

        for (const el of this.manager.store.getAll()) {
            storeEntries += 1;
            objectIds.add(el.objectId);

            const fv = el.filterValue;
            if (typeof fv !== "number" || reachableRange === null) continue;
            if (!Number.isFinite(fv)) {
                infinite += 1;
                unreachable += 1;
            } else if (fv < reachableRange.minRel || fv > reachableRange.maxRel) {
                unreachable += 1;
                if (samplesUnreachable.length < 5) samplesUnreachable.push(fv);
            }
        }

        const result = {
            storeEntries,
            storeUniqueObjectIds: objectIds.size,
            windowStartMs: plan?.fromMs ?? null,
            reachableRange,
            unreachable,
            infinite,
            samplesUnreachable,
        };
        logger.info("[historical-debug] unrendered store elements", result);
        return result;
    }

    private dumpObjectMembership(): void {
        const perObject = this.buildPerObjectSummary();
        const rows = [...perObject.entries()]
            .map(([objectId, info]) => ({
                objectId,
                buckets: [...info.bucketsPresent].sort((a, b) => a - b).join(","),
                bucketCount: info.bucketsPresent.size,
                totalSamples: [...info.samplesPerBucket.values()].reduce((s, n) => s + n, 0),
                firstBucket: info.firstBucketIndex,
            }))
            .sort((a, b) => b.bucketCount - a.bucketCount);
        // eslint-disable-next-line no-console -- diagnostic bridge: console.table has no js-logger equivalent
        console.table(rows);
    }

    private computeDisplayDiagnostics(bucketIndex: number): {
        bucketIndex: number;
        displayedCount: number;
        displayedObjectIds: number;
        totalObjectIds: number;
    } {
        const perObject = this.buildPerObjectSummary();

        let displayedCount = 0;
        const displayedObjects = new Set<string>();

        for (const [objectId, info] of perObject) {
            if (info.bucketsPresent.has(bucketIndex)) {
                displayedCount += info.samplesPerBucket.get(bucketIndex) ?? 0;
                displayedObjects.add(objectId);
            }
        }

        return {
            bucketIndex,
            displayedCount,
            displayedObjectIds: displayedObjects.size,
            totalObjectIds: perObject.size,
        };
    }

    private buildPerObjectSummary(): Map<string, PerObjectInfo> {
        const out = new Map<string, PerObjectInfo>();
        // Iterate the store directly. Do NOT collect into one array via
        // `push(...)`: the spread turns every element into a separate call
        // argument, which overflows the engine's argument-count limit at
        // historical scale (manifests as a misleading "Maximum call stack size
        // exceeded" RangeError).
        for (const el of this.manager.store.getAll()) {
            this.ingestElement(out, el);
        }
        return out;
    }

    private ingestElement(out: Map<string, PerObjectInfo>, el: Element): void {
        const objectId = el.objectId;
        const filterVal = el.filterValue;
        const bucketIndex =
            typeof filterVal === "number" && Number.isFinite(filterVal) ? filterVal : undefined;
        let rec = out.get(objectId);
        if (!rec) {
            rec = {
                firstBucketIndex: null,
                bucketsPresent: new Set<number>(),
                samplesPerBucket: new Map<number, number>(),
            };
            out.set(objectId, rec);
        }
        if (bucketIndex !== undefined) {
            rec.bucketsPresent.add(bucketIndex);
            rec.samplesPerBucket.set(bucketIndex, (rec.samplesPerBucket.get(bucketIndex) ?? 0) + 1);
            if (rec.firstBucketIndex === null || bucketIndex < rec.firstBucketIndex) {
                rec.firstBucketIndex = bucketIndex;
            }
        }
    }
}

export function installHistoricalDataManagerDiagnostics(
    manager: HistoricalDataManager,
): HistoricalDataManagerDiagnostics {
    return new HistoricalDataManagerDiagnosticsImpl(manager);
}
