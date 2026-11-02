// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Dev-only performance instrumentation bridge.
 *
 * Exposes {@link window.__opencop_perf} for Playwright perf tests to inject
 * test data, query internal state, and trigger mutations. All element
 * construction uses the production-path random generators from
 * `@opencop/elements`, so injected data exercises the full rendering pipeline
 * identically to real features.
 *
 * Guarded by `import.meta.env.DEV` at the call-site in MapManagerContext.
 */

import type { Element } from "@opencop/elements";
import {
    Point,
    Symbol,
    Polygon,
    Line,
    Circle,
    Ellipse,
    Rectangle,
    Angle,
    Arrow,
    randomPointStyle,
    randomShapeStyle,
    randomStrokeStyle,
    randomArrowStyle,
    getElementKey,
} from "@opencop/elements";
import type { MapboxOverlay } from "@deck.gl/mapbox";
import { primitiveId } from "@opencop/scene";
import { codec } from "./scene-binding/ref-codec";
import type { ElementStore } from "@opencop/ingest";
import {
    RESOLUTION_WIDTH_MS,
    type HistoricalChunkResolution,
} from "~/features/historical/data-plane/HistoricalChunk";
import type { HistoricalDataManager } from "~/features/historical/data-plane/HistoricalDataManager";
import type { HistoricalPlan } from "~/features/historical/data-plane/HistoricalSession";
import { DateTime } from "luxon";
import * as uuid from "uuid";

// ---------------------------------------------------------------------------
// Global type declarations for dev-only perf instrumentation
// ---------------------------------------------------------------------------

declare global {
    interface Window {
        /** Dev-only perf bridge — set by registerPerfBridge(), read by Playwright tests. */
        __opencop_perf?: PerfBridge;
        /** Dev-only DeckGL overlay reference — set by DeckLayerContext. */
        __opencop_deck_overlay?: MapboxOverlay;
    }
}

/** Minimal type for the perf bridge (full shape defined by registerPerfBridge). */
export interface PerfBridge {
    _map?: unknown;
    getElementCount(): number;
    getElementCountByType(): Record<string, number>;
    getBinaryMemoryBytes(): number;
    getBinaryMemoryUsedBytes(): number;
    getSourceMemory(): Record<
        string,
        { elements: number; capacityBytes: number; usedBytes: number }
    >;
    injectElements(config: InjectConfig): void;
    clearAllElements(): void;
    clearBySource(sourceId: string): void;
    updatePositions(jitterDeg: number): void;
    updatePositionsStaggered(jitterDeg: number, batchSize: number): Promise<number>;
    hideSource(sourceId: string): void;
    showSource(sourceId: string): void;
    getSourceCount(sourceId: string): number;
    pickElementAt(
        x: number,
        y: number,
    ): {
        source: string;
        objectId: string;
        type: string;
        code: string | undefined;
        displayName: string;
        position: [number, number];
    } | null;
    getElement(
        sourceId: string,
        objectId: string,
    ): {
        objectId: string;
        type: string;
        code: string | undefined;
        displayName: string;
        position: [number, number];
        source: string;
    } | null;
    getAllLabels(): Array<{
        text: string;
        position: [number, number];
        elementType: string;
    }>;
    /**
     * Historical-mode perf surface. Populated only while a `HistoricalDataManager`
     * is registered (DEV; the provider registers/clears it). Lets perf tests run a
     * synthetic historical session (no backend), inject bucketed elements, scrub
     * the playhead, and query what the GPU filter displays.
     */
    historical: HistoricalPerfBridge;
}

/** Bucket resolution accepted by the synthetic historical session. */
type HistoricalResolution = HistoricalChunkResolution;

export interface HistoricalSyntheticSessionConfig {
    /** Number of time buckets (chunks) in the synthetic window. */
    bucketCount: number;
    /** Bucket width. Default `"MINUTE_1"`. */
    resolution?: HistoricalResolution;
    /** Window start in epoch ms. Default: aligned `now - bucketCount * bucketWidthMs`. */
    fromMs?: number;
}

export interface HistoricalInjectConfig {
    type: ElementType;
    count: number;
    center: [number, number]; // [lng, lat]
    spread: number; // degrees
    source?: string;
    vertexCount?: number; // polygon/line vertex count (default: random 3-8)
    displayName?: string; // base name (default: "Perf historical {type}")
    sidcs?: string[]; // symbol-only: cycle these SIDCs instead of the 8 built-ins
}

export interface HistoricalStreamConfig {
    /** Elements delivered per chunk. */
    elementsPerChunk: number;
    type: ElementType;
    center: [number, number]; // [lng, lat]
    spread: number; // degrees
    /**
     * Chunk width. The chunk count is *derived* — chunks tile the active
     * session's real range at this resolution, on the same grid the backend +
     * histogram use. Pick a coarser resolution for wide ranges (a 24h range is
     * 24 chunks at `"HOUR_1"`, 1440 at `"MINUTE_1"`). Default `"HOUR_1"`.
     */
    resolution?: HistoricalResolution;
    /** Delay between chunk deliveries in ms (simulates arrival cadence). Default 50. */
    intervalMs?: number;
    vertexCount?: number;
    displayName?: string;
    sidcs?: string[];
}

export interface HistoricalPerfBridge {
    isActive(): boolean;
    beginSyntheticSession(config: HistoricalSyntheticSessionConfig): void;
    injectElements(config: HistoricalInjectConfig): void;
    /**
     * Stream chunks of elements over time through the real ingestion seams:
     * each chunk records a chunk-range ledger entry (histogram chunk overlay),
     * delivers to the store (per-chunk pipeline rebuild + GPU upload), and bumps
     * subscribers; a synthetic density histogram is seeded at the end. Resolves
     * when the stream completes. The synthetic session first resets the source's
     * chunk ledger + store (see `HistoricalSession.resetForPerf`) so it doesn't
     * collide with chunks the real activated session may have already delivered.
     */
    beginSyntheticStream(config: HistoricalStreamConfig): Promise<void>;
    setPlayheadBucket(bucketIndex: number): void;
    getActiveBucketIndex(): number;
    getDataFilterRange(): [number, number];
    getElementCount(): number;
    getDisplayedCount(): number;
    /**
     * Identities of the historical elements currently inside the playhead
     * window — i.e. what the GPU filter is displaying at the active playhead.
     * Empty when the playhead sits where nothing is alive. Historical elements
     * are not visible to the top-level `pickElementAt` / `getElement`, so this is
     * how a DEV test enumerates them.
     */
    listDisplayed(): HistoricalDisplayedRef[];
    /**
     * Select a displayed historical element through the real map-selection path,
     * opening the read-only detail panel exactly as a click would. Targets the
     * given `objectId`, or the first displayed element when omitted. Returns the
     * selected identity, or `null` when nothing matching is displayed.
     */
    selectDisplayed(objectId?: string): HistoricalDisplayedRef | null;
    /** Clear any map selection (closes the detail panel). */
    clearSelection(): void;
    /**
     * Displayed historical elements paired with their canvas `[x, y]` pixel
     * positions (via the live map viewport). Skips any element whose position
     * can't be projected. Lets a DEV test right-click a real historical element
     * to open its context menu — deck.gl picking can't resolve them.
     */
    displayedScreenPoints(): HistoricalScreenPoint[];
    clearAllElements(): void;
}

/**
 * Identity of a historical element in the manager store (DEV perf / e2e tests).
 *
 * Structurally mirrored (by hand) in `e2e/helpers/historical-helpers.ts` —
 * Playwright specs run in a separate TS project and can't import from this app
 * package, so the shape is duplicated there. Keep both declarations in sync; a
 * drift won't be caught by the type checker (they're structurally compatible),
 * only by a live e2e run.
 */
export interface HistoricalDisplayedRef {
    objectId: string;
    /** Owning source id. */
    source: string;
    /** The store + map-selection key (historical per-version id key). */
    key: string;
    /** Element type (`symbol`, `point`, …). */
    type: string;
    displayName: string;
}

/** A displayed historical element with its canvas pixel position. */
export interface HistoricalScreenPoint {
    objectId: string;
    source: string;
    /** Canvas-relative x pixel. */
    x: number;
    /** Canvas-relative y pixel. */
    y: number;
}

/** Registration handed to the bridge by the historical provider (DEV only). */
export interface HistoricalPerfRegistration {
    manager: HistoricalDataManager;
    setPlayhead: (iso: string | null) => void;
    /**
     * Drive the map selection so a DEV test can open (and close) the read-only
     * historical detail panel by element key — no screen-coordinate math and no
     * dependence on deck.gl picking, which does not resolve historical elements.
     * Passing `null` clears the selection. Implemented by the provider, which
     * owns the map-selection store.
     */
    selectByKey: (key: string | null) => void;
    /**
     * Project a `[lng, lat]` world position to canvas `[x, y]` pixels via the
     * live map viewport, so a DEV test can right-click a historical element at
     * its real screen location (deck.gl picking can't locate historical
     * elements). Returns undefined before the viewport is bound.
     */
    projectToScreen: (lngLat: [number, number]) => [number, number] | undefined;
    /**
     * Seed the histogram density query cache with a synthetic distribution
     * (the density bars come from a REST query the synthetic session doesn't
     * hit). Implemented by the provider, which holds the QueryClient + the
     * source/range/bars that form the query key.
     */
    seedHistogram: (opts: { totalActive: number }) => void;
}

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export type ElementType =
    | "point"
    | "symbol"
    | "polygon"
    | "line"
    | "circle"
    | "ellipse"
    | "rectangle"
    | "angle"
    | "arrow";

export interface InjectConfig {
    type: ElementType;
    count: number;
    center: [number, number]; // [lng, lat]
    spread: number; // degrees
    source?: string;
    stale?: number; // seconds from now (default: 600)
    vertexCount?: number; // polygon/line vertex count (default: random 3-8)
    displayName?: string; // base name (default: "Perf {type}")
    sidcs?: string[]; // symbol-only: cycle these SIDCs instead of the 8 built-ins (for icon-atlas variety testing)
}

interface SourceMemory {
    elements: number;
    capacityBytes: number;
    usedBytes: number;
}

// Cycling SIDC codes for visual diversity (friendly/hostile/neutral/unknown)
const SIDC_CODES = [
    "10031000001101000000", // friendly ground unit
    "10061000001101000000", // hostile ground unit
    "10041000001101000000", // neutral ground unit
    "10011000001101000000", // unknown ground unit
    "10031000001101050000", // friendly infantry
    "10061000001101030000", // hostile armor
    "10041000001101080000", // neutral vehicle
    "10031000001101070000", // friendly recon
];

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function rand(min: number, max: number): number {
    return Math.random() * (max - min) + min;
}

function delay(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

function nearbyCoord(center: [number, number], spread: number): [number, number] {
    return [center[0] + rand(-spread, spread), center[1] + rand(-spread, spread)];
}

function nearbyCoords(center: [number, number], spread: number, count: number): [number, number][] {
    const coords: [number, number][] = [];
    for (let i = 0; i < count; i++) {
        coords.push(nearbyCoord(center, spread));
    }
    return coords;
}

// ---------------------------------------------------------------------------
// Element factories — spatial-aware wrappers around production constructors
// ---------------------------------------------------------------------------

function createPoint(
    center: [number, number],
    spread: number,
    meta: { source: string; stale: number; displayName: string },
): Element {
    return Point.create({
        position: nearbyCoord(center, spread),
        style: randomPointStyle(),
        meta: {
            objectId: uuid.v4(),
            source: meta.source,
            stale: meta.stale,
            displayName: meta.displayName,
        },
    });
}

function createSymbol(
    center: [number, number],
    spread: number,
    index: number,
    meta: { source: string; stale: number; displayName: string; sidcs?: string[] },
): Element {
    const codes = meta.sidcs?.length ? meta.sidcs : SIDC_CODES;
    const sidc = codes[index % codes.length];
    return Symbol.create({
        position: nearbyCoord(center, spread),
        sidc,
        size: rand(20, 40),
        meta: {
            objectId: uuid.v4(),
            source: meta.source,
            stale: meta.stale,
            displayName: meta.displayName,
        },
    });
}

function createPolygon(
    center: [number, number],
    spread: number,
    vertexCount: number,
    meta: { source: string; stale: number; displayName: string },
): Element {
    return Polygon.create({
        positions: nearbyCoords(center, spread * 0.5, vertexCount),
        style: randomShapeStyle(),
        meta: {
            objectId: uuid.v4(),
            source: meta.source,
            stale: meta.stale,
            displayName: meta.displayName,
        },
    });
}

function createLine(
    center: [number, number],
    spread: number,
    vertexCount: number,
    meta: { source: string; stale: number; displayName: string },
): Element {
    return Line.create({
        positions: nearbyCoords(center, spread * 0.5, vertexCount),
        style: randomStrokeStyle(),
        meta: {
            objectId: uuid.v4(),
            source: meta.source,
            stale: meta.stale,
            displayName: meta.displayName,
        },
    });
}

function createCircle(
    center: [number, number],
    spread: number,
    meta: { source: string; stale: number; displayName: string },
): Element {
    return Circle.create({
        origin: nearbyCoord(center, spread),
        radius: rand(500, 10_000),
        style: randomShapeStyle(),
        meta: {
            objectId: uuid.v4(),
            source: meta.source,
            stale: meta.stale,
            displayName: meta.displayName,
        },
    });
}

function createEllipse(
    center: [number, number],
    spread: number,
    meta: { source: string; stale: number; displayName: string },
): Element {
    return Ellipse.create({
        origin: nearbyCoord(center, spread),
        major: rand(1000, 15_000),
        minor: rand(500, 8_000),
        bearing: rand(0, 360),
        style: randomShapeStyle(),
        meta: {
            objectId: uuid.v4(),
            source: meta.source,
            stale: meta.stale,
            displayName: meta.displayName,
        },
    });
}

function createRectangle(
    center: [number, number],
    spread: number,
    meta: { source: string; stale: number; displayName: string },
): Element {
    const origin = nearbyCoord(center, spread);
    const hw = spread * 0.2;
    const hh = spread * 0.15;
    return Rectangle.create({
        positions: [
            [origin[0] - hw, origin[1] - hh],
            [origin[0] + hw, origin[1] - hh],
            [origin[0] + hw, origin[1] + hh],
            [origin[0] - hw, origin[1] + hh],
        ],
        style: randomShapeStyle(),
        meta: {
            objectId: uuid.v4(),
            source: meta.source,
            stale: meta.stale,
            displayName: meta.displayName,
        },
    });
}

function createAngle(
    center: [number, number],
    spread: number,
    meta: { source: string; stale: number; displayName: string },
): Element {
    const vertex = nearbyCoord(center, spread);
    const armLen = spread * 0.3;
    return Angle.create({
        first: [vertex[0] + rand(-armLen, armLen), vertex[1] + rand(-armLen, armLen)],
        second: vertex,
        third: [vertex[0] + rand(-armLen, armLen), vertex[1] + rand(-armLen, armLen)],
        style: randomStrokeStyle(),
        meta: {
            objectId: uuid.v4(),
            source: meta.source,
            stale: meta.stale,
            displayName: meta.displayName,
        },
    });
}

function createArrow(
    center: [number, number],
    spread: number,
    meta: { source: string; stale: number; displayName: string },
): Element {
    const start = nearbyCoord(center, spread);
    const end = nearbyCoord(center, spread);
    return Arrow.create({
        start,
        end,
        style: randomArrowStyle(),
        meta: {
            objectId: uuid.v4(),
            source: meta.source,
            stale: meta.stale,
            displayName: meta.displayName,
        },
    });
}

type ElementFactory = (
    center: [number, number],
    spread: number,
    index: number,
    vertexCount: number,
    meta: { source: string; stale: number; displayName: string; sidcs?: string[] },
) => Element;

const FACTORIES: Record<ElementType, ElementFactory> = {
    point: (c, s, _i, _v, m) => createPoint(c, s, m),
    symbol: (c, s, i, _v, m) => createSymbol(c, s, i, m),
    polygon: (c, s, _i, v, m) => createPolygon(c, s, v, m),
    line: (c, s, _i, v, m) => createLine(c, s, v, m),
    circle: (c, s, _i, _v, m) => createCircle(c, s, m),
    ellipse: (c, s, _i, _v, m) => createEllipse(c, s, m),
    rectangle: (c, s, _i, _v, m) => createRectangle(c, s, m),
    angle: (c, s, _i, _v, m) => createAngle(c, s, m),
    arrow: (c, s, _i, _v, m) => createArrow(c, s, m),
};

// ---------------------------------------------------------------------------
// Historical-mode perf surface
// ---------------------------------------------------------------------------

// Set by the historical provider (DEV only) whenever it creates/disposes the
// active manager. The historical bridge methods resolve the manager lazily
// through this so they always target the current session.
let historicalReg: HistoricalPerfRegistration | null = null;

/**
 * Register (or clear, with `null`) the active historical manager so the perf
 * bridge's `historical` namespace can drive it. Called from
 * `HistoricalDataProvider` under `import.meta.env.DEV`.
 */
export function setHistoricalPerfManager(reg: HistoricalPerfRegistration | null): void {
    historicalReg = reg;
}

function requireHistorical(): HistoricalPerfRegistration {
    if (!historicalReg) {
        throw new Error(
            "Historical perf bridge inactive — enter historical mode and click Activate " +
                "(creates the manager), then call __opencop_perf.historical.beginSyntheticSession().",
        );
    }
    return historicalReg;
}

function requireHistoricalPlan(reg: HistoricalPerfRegistration): HistoricalPlan {
    const plan = reg.manager.session.getPlan();
    if (!plan) {
        throw new Error(
            "No synthetic session — call __opencop_perf.historical.beginSyntheticSession() first.",
        );
    }
    return plan;
}

/**
 * The elements currently inside the playhead window — the single source of truth
 * for `listDisplayed`, `selectDisplayed`, and `displayedScreenPoints`. Uses the
 * same `[lo, hi] = dataFilterRange` filter as `getDisplayedCount` (which keeps
 * its own count-only loop deliberately — see the note there — to avoid
 * allocating a ref/element array on a hot perf-testing path).
 */
function collectDisplayedElements(reg: HistoricalPerfRegistration): Element[] {
    const [lo, hi] = reg.manager.dataFilterRange;
    const elements: Element[] = [];
    // Iterate (do NOT spread getAll() into push) — the spread overflows
    // V8's argument-count cap at perf scale. Mirrors getDisplayedCount.
    for (const el of reg.manager.store.getAll()) {
        const fv = el.filterValue;
        if (fv >= lo && fv <= hi) elements.push(el);
    }
    return elements;
}

function toDisplayedRef(el: Element): HistoricalDisplayedRef {
    return {
        objectId: el.objectId,
        source: el.source,
        key: el.key,
        type: el.type,
        displayName: el.getDisplayName(),
    };
}

const historicalBridge: HistoricalPerfBridge = {
    isActive(): boolean {
        return historicalReg !== null;
    },

    beginSyntheticSession(config: HistoricalSyntheticSessionConfig): void {
        const reg = requireHistorical();
        const resolution = config.resolution ?? "MINUTE_1";
        const bucketWidthMs = RESOLUTION_WIDTH_MS[resolution];
        const { bucketCount } = config;
        // Align the window start down to a bucket boundary so bucket↔ms math is exact.
        const rawFrom = config.fromMs ?? Date.now() - bucketCount * bucketWidthMs;
        const fromMs = Math.floor(rawFrom / bucketWidthMs) * bucketWidthMs;
        // Drop any elements the real (activated) session delivered BEFORE seeding
        // the plan: `__beginPerfSession` ends with `setState("enabled")`, which
        // bumps the manager's subscriber and triggers a pipeline rebuild — clear
        // the store first so that rebuild doesn't paint a flash of stale real data.
        reg.manager.store.deleteAll();
        reg.manager.session.__beginPerfSession({
            fromMs,
            bucketWidthMs,
            totalChunkCount: bucketCount,
            resolution,
        });
    },

    injectElements(config: HistoricalInjectConfig): void {
        const reg = requireHistorical();
        const plan = requireHistoricalPlan(reg);
        const {
            type,
            count,
            center,
            spread,
            source = "perf-historical",
            vertexCount,
            displayName,
            sidcs,
        } = config;

        const factory = FACTORIES[type];
        if (!factory) throw new Error(`Unknown element type: ${type}`);

        const vCount = vertexCount ?? Math.floor(rand(3, 9));
        const baseName = displayName ?? `Perf historical ${type}`;
        // Stale is irrelevant for historical elements (elementClass exempts them); use a
        // far-future value so construction is happy.
        const staleMs = Date.now() + 3_600_000;

        const elements: Element[] = [];
        for (let i = 0; i < count; i++) {
            const el = factory(center, spread, i, vCount, {
                source,
                stale: staleMs,
                displayName: `${baseName} ${i + 1}`,
                sidcs,
            });
            // Spread elements across the window so a slice is alive at any
            // playhead and scrubbing changes the visible slice. The manager
            // shows elements whose filterValue ∈ (playheadRel, playheadRel +
            // bucketWidthMs]; placing one at (b+1)*bucketWidthMs makes it visible
            // while the playhead sits in bucket b (mirrors the real decode path).
            const bucket = i % plan.totalChunkCount;
            el.setFilterValue((bucket + 1) * plan.bucketWidthMs);
            elements.push(el);
        }
        reg.manager.store.add(elements);
    },

    async beginSyntheticStream(config: HistoricalStreamConfig): Promise<void> {
        const reg = requireHistorical();
        const {
            elementsPerChunk,
            type,
            center,
            spread,
            resolution = "HOUR_1",
            intervalMs = 50,
            vertexCount,
            displayName,
            sidcs,
        } = config;

        const factory = FACTORIES[type];
        if (!factory) throw new Error(`Unknown element type: ${type}`);

        const bucketWidthMs = RESOLUTION_WIDTH_MS[resolution];
        // Tile the active session's real range so chunks land on the same grid
        // the histogram bins use (like real data) — avoids a synthetic window
        // that mismatches the visible range.
        const params = reg.manager.session.sourceParams;
        const fromMs =
            Math.floor(DateTime.fromISO(params.from, { zone: "utc" }).toMillis() / bucketWidthMs) *
            bucketWidthMs;
        const bucketCount = Math.max(
            1,
            Math.ceil(
                (DateTime.fromISO(params.to, { zone: "utc" }).toMillis() - fromMs) / bucketWidthMs,
            ),
        );
        const vCount = vertexCount ?? Math.floor(rand(3, 9));
        const baseName = displayName ?? `Perf historical ${type}`;
        const staleMs = Date.now() + 3_600_000;

        // Drop any elements the real (activated) session delivered BEFORE seeding
        // the stream's plan, so the first `setState`-driven rebuild starts clean
        // (see `beginSyntheticSession`).
        reg.manager.store.deleteAll();
        reg.manager.session.__beginPerfStream("perf-job", {
            fromMs,
            bucketWidthMs,
            totalChunkCount: bucketCount,
            resolution,
        });

        for (let chunk = 0; chunk < bucketCount; chunk++) {
            const elements: Element[] = [];
            for (let j = 0; j < elementsPerChunk; j++) {
                const el = factory(center, spread, j, vCount, {
                    source: "perf-historical",
                    stale: staleMs,
                    displayName: `${baseName} c${chunk} ${j + 1}`,
                    sidcs,
                });
                // Elements in chunk N are alive at bucket N (filterValue at the
                // chunk's right edge, mirroring the real decode tagging).
                el.setFilterValue((chunk + 1) * bucketWidthMs);
                elements.push(el);
            }
            const chunkFromMs = fromMs + chunk * bucketWidthMs;
            reg.manager.session.__perfDeliverChunk(
                chunk,
                chunkFromMs,
                chunkFromMs + bucketWidthMs,
                elements,
            );
            // Always yield (delay(0) is a macrotask) so the deferred element
            // microtask flushes and React commits between chunks — matching
            // real per-chunk streaming cadence.
            await delay(intervalMs);
            // Bail if the session was torn down while we were yielded — otherwise
            // we'd keep delivering chunks (and ultimately `store.add`) to a
            // disposed source the provider has already cleared.
            if (historicalReg !== reg) return;
        }

        reg.seedHistogram({ totalActive: bucketCount * elementsPerChunk });
        reg.manager.session.__perfCompleteStream(bucketCount);
    },

    setPlayheadBucket(bucketIndex: number): void {
        const reg = requireHistorical();
        const plan = requireHistoricalPlan(reg);
        // Aim at the middle of the bucket so the playhead lands unambiguously in it.
        const ms =
            plan.fromMs + bucketIndex * plan.bucketWidthMs + Math.floor(plan.bucketWidthMs / 2);
        reg.setPlayhead(DateTime.fromMillis(ms, { zone: "utc" }).toISO() ?? "");
    },

    getActiveBucketIndex(): number {
        return requireHistorical().manager.activeBucketIndex;
    },

    getDataFilterRange(): [number, number] {
        return requireHistorical().manager.dataFilterRange;
    },

    getElementCount(): number {
        return requireHistorical().manager.store.getCount();
    },

    getDisplayedCount(): number {
        // Kept as its own count-only loop (rather than
        // `collectDisplayedElements(reg).length`) on purpose: this is a hot
        // perf-testing probe, so we avoid allocating an intermediate element
        // array just to count. The filter is identical to
        // `collectDisplayedElements`; keep the two in sync if it ever changes.
        const reg = requireHistorical();
        const [lo, hi] = reg.manager.dataFilterRange;
        let count = 0;
        // Iterate (do NOT `push(...getAll())`): the spread overflows V8's
        // argument-count cap at perf scale (the historical diagnostics bug).
        for (const el of reg.manager.store.getAll()) {
            const fv = el.filterValue;
            if (fv >= lo && fv <= hi) count++;
        }
        return count;
    },

    listDisplayed(): HistoricalDisplayedRef[] {
        return collectDisplayedElements(requireHistorical()).map(toDisplayedRef);
    },

    selectDisplayed(objectId?: string): HistoricalDisplayedRef | null {
        const reg = requireHistorical();
        const displayed = collectDisplayedElements(reg);
        const el = objectId ? displayed.find((e) => e.objectId === objectId) : displayed[0];
        if (!el) return null;
        const ref = toDisplayedRef(el);
        reg.selectByKey(ref.key);
        return ref;
    },

    clearSelection(): void {
        requireHistorical().selectByKey(null);
    },

    displayedScreenPoints(): HistoricalScreenPoint[] {
        const reg = requireHistorical();
        const points: HistoricalScreenPoint[] = [];
        for (const el of collectDisplayedElements(reg)) {
            const world = el.geometry.getPos(0) as [number, number];
            const screen = reg.projectToScreen(world);
            if (screen) {
                points.push({
                    objectId: el.objectId,
                    source: el.source,
                    x: screen[0],
                    y: screen[1],
                });
            }
        }
        return points;
    },

    clearAllElements(): void {
        requireHistorical().manager.store.deleteAll();
    },
};

// ---------------------------------------------------------------------------
// Bridge registration
// ---------------------------------------------------------------------------

/**
 * Every element on the map: the store's elements and, while one is drawn or edited, the
 * draft the store holds apart from them. An edited element is both; it counts once.
 */
function* onTheMap(store: ElementStore): Iterable<Element> {
    yield* store.getAll();
    const draft = store.getDraft();
    if (draft && !store.get(draft.key)) yield draft;
}

export function registerPerfBridge(store: ElementStore): void {
    // Mirror the production hide/show flow (filters panel): hiding a source
    // deletes its elements and unsubscribes the websocket; showing re-subscribes
    // and the server re-delivers them. The bridge has no websocket, so we snapshot
    // the deleted elements here and re-add them on show to stand in for that
    // re-delivery.
    const hiddenSnapshots = new Map<string, Element[]>();

    const bridge = {
        // --- State queries ---

        getElementCount(): number {
            return [...onTheMap(store)].length;
        },

        getElementCountByType(): Record<string, number> {
            const result: Record<string, number> = {};
            for (const element of onTheMap(store)) {
                result[element.type] = (result[element.type] ?? 0) + 1;
            }
            return result;
        },

        /**
         * Binary typed-array memory metrics. The live + historical map now
         * render through diffable `LayerDataset` specs, which hold `Element`
         * objects rather than packed binary buffers, so there is no binary
         * memory to report — these return 0/empty. Retained (rather than
         * removed) so the cross-branch perf suite, which calls them on every
         * branch, records 0 here instead of erroring on a missing method.
         */
        getBinaryMemoryBytes(): number {
            return 0;
        },

        getBinaryMemoryUsedBytes(): number {
            return 0;
        },

        getSourceMemory(): Record<string, SourceMemory> {
            return {};
        },

        // --- Data injection ---

        injectElements(config: InjectConfig): void {
            const {
                type,
                count,
                center,
                spread,
                source = "perf-test",
                stale = 600,
                vertexCount,
                displayName,
                sidcs,
            } = config;

            const factory = FACTORIES[type];
            if (!factory) throw new Error(`Unknown element type: ${type}`);

            const staleMs = Date.now() + stale * 1000;
            const vCount = vertexCount ?? Math.floor(rand(3, 9));
            const baseName = displayName ?? `Perf ${type}`;

            const elements: Element[] = [];
            for (let i = 0; i < count; i++) {
                elements.push(
                    factory(center, spread, i, vCount, {
                        source,
                        stale: staleMs,
                        displayName: `${baseName} ${i + 1}`,
                        sidcs,
                    }),
                );
            }

            store.add(elements);
        },

        // --- Mutations ---

        clearAllElements(): void {
            hiddenSnapshots.clear();
            store.deleteAll();
        },

        clearBySource(sourceId: string): void {
            hiddenSnapshots.delete(sourceId);
            store.deleteBySource(sourceId);
        },

        updatePositions(jitterDeg: number): void {
            const elements = [...store.getAll()];
            for (const element of elements) jitterElement(element, jitterDeg);
            // Re-adding an element the store holds announces it as updated.
            store.add(elements);
        },

        /**
         * Staggered position updates — spreads work across animation frames.
         * Each frame updates `batchSize` elements, yielding between batches
         * via requestAnimationFrame. Simulates real WebSocket-driven updates
         * where elements arrive over time rather than all at once.
         *
         * Returns a promise that resolves when all elements have been updated.
         */
        hideSource(sourceId: string): void {
            // Snapshot before deleting so showSource can restore (production
            // relies on websocket re-delivery, which the bridge doesn't have).
            hiddenSnapshots.set(sourceId, [...store.getBySource(sourceId)]);
            // Match production: mark hidden (fires the visibility/compaction
            // signal the pipeline emits) then delete the elements. The delete
            // drives the actual removal from the rendering specs via the
            // pipeline's onDeleteBatch handler.
            store.hideSource(sourceId);
            store.deleteBySource(sourceId);
        },

        showSource(sourceId: string): void {
            // Clear the hidden flag first so re-added elements aren't skipped by
            // the pipeline's hidden-source guard, then re-deliver the snapshot.
            store.showSource(sourceId);
            const snapshot = hiddenSnapshots.get(sourceId);
            if (snapshot) {
                store.add(snapshot);
                hiddenSnapshots.delete(sourceId);
            }
        },

        getSourceCount(sourceId: string): number {
            return store.getSourceCount(sourceId);
        },

        // --- Element queries (for e2e assertions) ---

        pickElementAt(x: number, y: number) {
            const deck = window.__opencop_deck_overlay;
            if (!deck) return null;

            const result = deck.pickObject({ x, y, radius: 4 });
            const id = result ? primitiveId(result) : undefined;
            const ref = id ? codec.decode(id) : undefined;
            if (ref?.object.type !== "element") return null;

            const draft = store.getDraft();
            const element =
                store.get(ref.object.key) ?? (draft?.key === ref.object.key ? draft : undefined);
            if (!element) return null;

            const pos = element.geometry.getPos(0) as [number, number];
            return {
                source: element.source,
                objectId: element.objectId,
                type: element.type,
                code: element.getCode(),
                displayName: element.getDisplayName(),
                position: pos,
            };
        },

        getElement(sourceId: string, objectId: string) {
            const element = store.get(getElementKey({ sourceId, objectId }));
            if (!element) return null;

            const pos = element.geometry.getPos(0) as [number, number];
            return {
                objectId: element.objectId,
                type: element.type,
                code: element.getCode(),
                displayName: element.getDisplayName(),
                position: pos,
                source: element.source,
            };
        },

        getAllLabels() {
            const labels: Array<{
                text: string;
                position: [number, number];
                elementType: string;
            }> = [];

            for (const element of store.getAll()) {
                const text = element.getDisplayName();
                if (!text) continue;
                labels.push({
                    text,
                    position: element.geometry.getPos(0) as [number, number],
                    elementType: element.type,
                });
            }

            return labels;
        },

        updatePositionsStaggered(jitterDeg: number, batchSize: number): Promise<number> {
            return new Promise((resolve) => {
                const allElements = [...store.getAll()];

                let offset = 0;
                const totalElements = allElements.length;

                function processBatch(): void {
                    const end = Math.min(offset + batchSize, totalElements);
                    const batch = allElements.slice(offset, end);
                    for (const element of batch) jitterElement(element, jitterDeg);
                    // Re-adding elements the store holds announces them as updated.
                    store.add(batch);
                    offset = end;

                    if (offset < totalElements) {
                        // Use setTimeout(0) instead of rAF — rAF may not fire
                        // if the renderer is still recovering from a heavy operation
                        setTimeout(processBatch, 0);
                    } else {
                        resolve(totalElements);
                    }
                }

                if (totalElements > 0) {
                    setTimeout(processBatch, 0);
                } else {
                    resolve(0);
                }
            });
        },

        // --- Historical mode (see HistoricalPerfBridge) ---

        historical: historicalBridge,
    };

    /** Jitter an element's first position by the given amount. */
    function jitterElement(element: Element, jitterDeg: number): void {
        const pos = element.geometry.getPos(0) as [number, number];
        element.geometry.setPos(
            [pos[0] + rand(-jitterDeg, jitterDeg), pos[1] + rand(-jitterDeg, jitterDeg)],
            0,
        );
    }

    window.__opencop_perf = bridge;
}
