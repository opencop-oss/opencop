// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The interaction runtime's unit-test harness: a fake host built the way
 * `MapManager.bindInteraction` builds the real one — over an in-memory element
 * store, a custody that holds the draft apart from it, and a map manager that records
 * and fires listeners — plus event shapes, fixture descriptors, and the
 * per-test reset. Shared by the `features/map/interaction-runtime` test files.
 *
 * `fakeDeps()` returns the host AND the fakes it delegates to, so a test can
 * assert on the manager's spies (`deps.mapManager.selectElement`) or the store
 * (`deps.store`) while the runtime sees only the contract. The environment is
 * the real one — the real registry, the real contribution guard — because the
 * seams under test are between the runtime and the map, not the registry.
 */
import { afterEach, beforeEach, expect, vi } from "vitest";
import { Line, type Element } from "@opencop/elements";
import type { ElementActions } from "@opencop/ingest";
import { __resetToolRegistry } from "~/features/contributions/tool-registry";
import type { MapManager } from "~/features/map/MapManager";
import { useMapStore } from "~/features/map/core/map.store";
import { useWatchboxPanelStore } from "~/features/watchbox/watchbox-panel.store";
import { useStackedObjectSelectionStore } from "~/features/map/stacked-object-selection.store";
import {
    clearAreaSelection,
    useAreaSelectionStore,
} from "~/features/selection/area-selection.store";
import { createInteractionEnvironment } from "~/features/map/interaction-environment";
import type { InteractionHost } from "~/features/map/interaction-runtime/contract";
import {
    attachPointerOwners,
    createInteractionRuntime,
} from "~/features/map/interaction-runtime/create";
import { getInteractionState } from "~/features/map/interaction-runtime/session/interaction.store";
import {
    ELEMENT_STORE_ID,
    traceGrammar,
    createSequentialGrammar,
    type ToolDescriptor,
} from "@opencop/tools";
import {
    activate,
    beginDraft,
    complete,
    draftCreated,
    resetInteraction,
    setLocked,
} from "~/features/map/interaction-runtime/session/commands";
import { attachRunner } from "~/features/map/interaction-runtime/arbitration/runner";
import { attachSelectionPolicy } from "~/features/map/selection.policy";

/**
 * The runtime's custody and extensibility guarantees, pinned:
 *
 * 1. Element custody lives in commands (`custody.ts`) — a runner detach/attach
 *    cycle (StrictMode's setup→cleanup→setup) preserves the in-flight element.
 * 2. A lasso-class interaction tool is REGISTRY DATA: a fixture descriptor
 *    registered by name activates, arms, and gates without the app knowing
 *    its name anywhere — and its freehand trace runs through the same runtime
 *    as every shape, completing through `onComplete`.
 * 3. `setParams` is one atomic destroy-and-restart — no half-torn state.
 * 4. A finished measure-class product is an EDIT session WITHOUT custody: the
 *    same session kind a just-drawn shape gets, holding its element directly,
 *    never touching the store or the pipeline, re-armable from where it stands.
 * 5. Idle is a session like any other: the runner binds ambient picking for it
 *    and unbinds it the moment a session begins — no per-click gate check.
 * 6. Custody follows the element store in `produces` and nothing else: a
 *    descriptor that produces into it draws a persisted element the session
 *    stores and locks; one that does not draws a session-local product, whatever completion does with it — and
 *    the session releases what it holds even after its descriptor is gone.
 * 7. One session kind for every tool while it draws: a shape, a measurement
 *    and a lasso are all `draw` sessions, and the host's gates read the
 *    in-flight predicate, never a tool's name or a phase of its own.
 * 8. The commands make no UI call: the selection follows the session through
 *    the lifecycle notification, so `fakeDeps` attaches the selection policy
 *    the way `MapManagerProvider` does and the assertions below read what it did.
 */

const detachPolicies: Array<() => void> = [];

type AnyListener = (...args: unknown[]) => void;

/**
 * A fake host over fakes a test can inspect, attaching no feature policy.
 * `fakeDeps()` is this plus the selection policy the app attaches.
 */
export function fakeHost() {
    const store = new Map<string, Element>();
    // The draft the runtime holds in custody, kept apart from the store until committed.
    let draft: Element | undefined;
    const actions = {
        get: (key: string) => store.get(key),
        add: (el: Element | Element[]) => {
            for (const e of Array.isArray(el) ? el : [el]) store.set(e.key, e);
        },
        delete: (key: string) => {
            store.delete(key);
        },
    } as unknown as ElementActions;

    // Map listeners are recorded so a test can drive the runner's bindings the
    // way maplibre would: `fire` iterates a copy of the list, so a handler the
    // runner unbinds mid-dispatch is still reached — and, as with the real
    // `MapManager`, a subscription is inert once unsubscribed, so that reach
    // delivers nothing. (That contract is pinned in `MapManager.test.ts`.)
    const mapListeners = new Map<string, AnyListener[]>();
    const fire = (event: string, ev: unknown) => {
        for (const listener of [...(mapListeners.get(event) ?? [])]) listener(ev);
    };
    // Deck listeners likewise, keyed separately: `fireDeck` delivers (info, element).
    const deckListeners = new Map<string, AnyListener[]>();
    const fireDeck = (event: string, ...args: unknown[]) => {
        for (const listener of [...(deckListeners.get(event) ?? [])]) listener(...args);
    };
    const record = (
        listeners: Map<string, AnyListener[]>,
        event: string,
        listener: AnyListener,
    ) => {
        let live = true;
        const wrapped: AnyListener = (...args) => {
            if (live) listener(...args);
        };
        listeners.set(event, [...(listeners.get(event) ?? []), wrapped]);
        return {
            event,
            unsub: () => {
                live = false;
                listeners.set(
                    event,
                    (listeners.get(event) ?? []).filter((l) => l !== wrapped),
                );
            },
        };
    };
    // The privileged bindings, as the real manager builds them INSIDE
    // `bindInteraction` from its private methods: not on the manager's public
    // type, so not on the fake either — they are the sources' alone.
    const bindings = {
        onMap: (event: string, listener: AnyListener) => record(mapListeners, event, listener),
        onDeck: (event: string, listener: AnyListener) => record(deckListeners, event, listener),
        // The real thing: the trace tests release through `document.dispatchEvent`.
        onDocument: (event: string, listener: EventListener) => {
            document.addEventListener(event, listener, true);
            return {
                event,
                unsub: () => document.removeEventListener(event, listener, true),
            };
        },
    };
    const mapManager = {
        setCursor: vi.fn(),
        defaultCursor: vi.fn(),
        disablePan: vi.fn(),
        enablePan: vi.fn(),
        disableDoubleClickZoom: vi.fn(),
        enableDoubleClickZoom: vi.fn(),
        suppressHoverClear: vi.fn(),
        handleEmptyClick: vi.fn(),
        setHover: vi.fn(),
        selectElement: vi.fn(),
        projectToScreen: vi.fn(() => [0, 0] as [number, number]),
        pickInsidePolygon: vi.fn(() => []),
        consumeNextDeckClick: vi.fn(),
        update: vi.fn(),
    } as unknown as MapManager;

    // The same facades `MapManager.bindInteraction` builds, over the fakes.
    const host: InteractionHost = {
        sources: {
            onMap: (event, listener) => bindings.onMap(event, listener as AnyListener),
            onDeck: (event, listener) => bindings.onDeck(event, listener as AnyListener),
            onDocument: (event, listener) =>
                bindings.onDocument(event, listener as unknown as EventListener),
            hoveringPickable: () =>
                useMapStore.getState().hovered !== null ||
                useWatchboxPanelStore.getState().hovered !== null,
            hoveringElement: () => useMapStore.getState().hovered !== null,
            subscribeHover: (listener) => {
                const unsubMap = useMapStore.subscribe((state, previous) => {
                    if (state.hovered !== previous.hovered) listener();
                });
                const unsubWatchbox = useWatchboxPanelStore.subscribe((state, previous) => {
                    if (state.hovered !== previous.hovered) listener();
                });
                return () => {
                    unsubMap();
                    unsubWatchbox();
                };
            },
        },
        actions: {
            selectElement: (element) => mapManager.selectElement(element),
            dismissSelection: () => mapManager.handleEmptyClick(),
            disambiguate: (candidates, screen, truncated) =>
                useStackedObjectSelectionStore.getState().open(candidates, screen, truncated),
            dismissDisambiguation: () => useStackedObjectSelectionStore.getState().dismiss(),
            selectArea: (elements, boundary) => {
                if (elements.length === 0) clearAreaSelection();
                else useAreaSelectionStore.getState().select([...elements], boundary);
            },
            pickInsidePolygon: (vertices) => mapManager.pickInsidePolygon(vertices),
            projectToScreen: (lngLat) => mapManager.projectToScreen(lngLat),
            consumeNextDeckClick: () => mapManager.consumeNextDeckClick(),
            setCursor: (cursor) => mapManager.setCursor(cursor),
            disablePan: () => mapManager.disablePan(),
            enablePan: () => mapManager.enablePan(),
            disableDoubleClickZoom: () => mapManager.disableDoubleClickZoom(),
            enableDoubleClickZoom: () => mapManager.enableDoubleClickZoom(),
            suppressHoverClear: (suppress) => mapManager.suppressHoverClear(suppress),
        },
        custody: {
            add: (elements) => actions.add(elements),
            delete: (key) => actions.delete(key),
            draft: (element) => {
                draft = element;
            },
            clear: () => {
                draft = undefined;
            },
            commit: () => {
                if (draft) actions.add(draft);
                draft = undefined;
            },
            isLocked: () => draft !== undefined,
        },
        environment: createInteractionEnvironment(),
    };

    return { host, mapManager, actions, store, held: () => draft, fire, fireDeck };
}

/** A fake host with the selection policy attached, as the app attaches it. */
export function fakeDeps() {
    const deps = fakeHost();
    detachPolicies.push(attachSelectionPolicy(deps.mapManager));
    return deps;
}

/**
 * Attach the REAL runtime to a fake host — the composition `MapManager.
 * bindInteraction` performs — for a test that needs what only the composition
 * point wires: the pointer's owners and the runner over them, the session's
 * Escape claimant, the surface closing on a session start. Returns the detach.
 */
export function attachRuntime(deps: { host: InteractionHost }): () => void {
    const runtime = createInteractionRuntime();
    runtime.attach(deps.host);
    return () => runtime.detach();
}

/**
 * The pointer's arbitration alone — the three owners and the runner over them,
 * without the rest of the runtime — for a test of what the runner does and
 * does not touch when it detaches and re-attaches. Returns the detach.
 */
export function attachPointerArbitration(deps: { host: InteractionHost }): () => void {
    const detachOwners = attachPointerOwners();
    const detachRunner = attachRunner(deps.host);
    return () => {
        detachRunner();
        detachOwners();
    };
}

/**
 * A deck pick-info as `MapManager` hands it to `onClick` listeners. `hit` is
 * the nearest thing under the pointer when it is not an element — the layer
 * that drew it, its datum, and the ref of the store object it was drawn for,
 * when it was drawn for one.
 */
export function deckClick(
    picks: Element[],
    button = 0,
    hit?: {
        readonly layer: string;
        readonly object: unknown;
        readonly ref?: { readonly type: string; readonly key: string };
    },
) {
    return {
        button,
        picks,
        picking: { x: 10, y: 20, layer: hit ? { id: hit.layer } : null, object: hit?.object },
        ref: hit?.ref,
    };
}

/** A maplibre-shaped mouse event at a lng/lat with a screen position and button. */
export function mapMouse(lngLat: [number, number], point: { x: number; y: number }, button = 0) {
    return {
        lngLat: { lng: lngLat[0], lat: lngLat[1], toArray: () => lngLat },
        point,
        originalEvent: { button, clientX: point.x, clientY: point.y },
        preventDefault: vi.fn(),
    };
}

export const traceFixture = (onComplete: ToolDescriptor["onComplete"]): ToolDescriptor => ({
    id: "fixtureTrace",
    label: "fixture-trace",
    icon: () => null,
    draw: { grammar: traceGrammar },
    onComplete,
});

export const lineFixture: ToolDescriptor = {
    id: "fixtureLine",
    label: "fixture",
    icon: () => null,
    produces: { store: ELEMENT_STORE_ID, type: "line" },
    draw: {
        grammar: createSequentialGrammar({
            min: 2,
            create: (cursor) => Line.create({ positions: cursor }),
        }),
    },
};

/** A measure-class tool: a line draw with no element-store product, so its product stays session-local. */
export const measureFixture: ToolDescriptor = {
    id: "fixtureMeasure",
    label: "fixture-measure",
    icon: () => null,
    draw: {
        grammar: createSequentialGrammar({
            min: 2,
            create: (cursor) => Line.create({ positions: cursor }),
        }),
    },
};

/** Drive a measure-class fixture from waiting to a finished product. */
export function finishMeasurement(deps: ReturnType<typeof fakeDeps>) {
    expect(activate("fixtureMeasure", { params: { shape: "line" } })).toEqual({ ok: true });
    const armed = getInteractionState().session;
    const epoch = armed.kind === "draw" ? armed.epoch : -1;
    const draft = Line.create({ positions: [0, 0] });
    draftCreated(draft, epoch);
    beginDraft(draft, epoch);
    setLocked(2, epoch);
    expect(complete()).toEqual({ ok: true });
    void deps;
    return draft;
}

/**
 * Register the per-test reset every runtime test file needs: an empty tool
 * registry and an idle session before each test, and the same plus detached
 * selection policies after.
 */
export function installInteractionRuntimeHarness(): void {
    beforeEach(() => {
        __resetToolRegistry();
        resetInteraction();
    });

    afterEach(() => {
        resetInteraction();
        __resetToolRegistry();
        detachPolicies.splice(0).forEach((detach) => detach());
    });
}
