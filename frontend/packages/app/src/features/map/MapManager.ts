// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MapEventType, Map as MapLibre } from "maplibre-gl";
import type { MapboxOverlay } from "@deck.gl/mapbox";
import type { ElementActions, ElementStore } from "@opencop/ingest";
import { WebMercatorViewport, type MapViewState, type PickingInfo } from "@deck.gl/core";
import {
    Element,
    type ElementRef,
    parseElementKey,
    toElementRef,
    getElementKey,
} from "@opencop/elements";
import * as turf from "@turf/turf";
// These Zustand stores are internal projection stores owned by MapManager.
// Always write through MapManager methods (selectElement, clearSelection,
// setHover, setTerrainEnabled, etc.) — never call store actions directly.
import { useStackedObjectSelectionStore } from "./stacked-object-selection.store";
import {
    clearAreaSelection,
    useAreaSelectionStore,
} from "~/features/selection/area-selection.store";
import Logger from "js-logger";
import type {
    DeckEvent,
    DeckPick,
    InteractionEnvironment,
    InteractionHost,
    InteractionRuntime,
} from "./interaction-runtime/contract";
import { useMapStore } from "./core/map.store";
import { useWatchboxPanelStore } from "../watchbox/watchbox-panel.store";
import { primitiveId } from "@opencop/scene";
import { codec, type PrimitiveRef } from "./scene-binding/ref-codec";
import { WATCHBOX_DRAFT_KEY, WATCHBOX_STORE_ID } from "../watchbox/watchbox.store";

export type DeckMetrics = {
    fps: number;
    setPropsTime: number;
    updateAttributesTime: number;
    framesRedrawn: number;
    pickTime: number;
    pickCount: number;
    gpuTime: number;
    gpuTimePerFrame: number;
    cpuTime: number;
    cpuTimePerFrame: number;
    bufferMemory: number;
    textureMemory: number;
    renderbufferMemory: number;
    gpuMemory: number;
};

export interface ManagerSubscription {
    event: string;
    unsub(): void;
}

/**
 * The camera events any feature may subscribe to through {@link MapManager.onMap}:
 * the view moving, zooming, rotating, pitching, and settling. Uncontended — no
 * session competes for them. The pointer events (`mousedown`, `mousemove`,
 * `mouseup`, `click`, `dblclick`, `contextmenu`) are not here: they exist only
 * on the `InteractionSources` value `bindInteraction` hands the runtime.
 */
export type CameraEvent =
    | "move"
    | "movestart"
    | "moveend"
    | "zoom"
    | "zoomstart"
    | "zoomend"
    | "rotate"
    | "rotatestart"
    | "rotateend"
    | "pitch"
    | "pitchstart"
    | "pitchend"
    | "idle";

/** A deck pick as the manager hands it on: the runtime's `DeckPick`, plus the camera it was taken under. */
type MapInfo = DeckPick & {
    viewState?: MapViewState;
    viewPort?: WebMercatorViewport;
};

type DeckListener = (info: MapInfo, element?: Element) => void;
type DeckListenerMap = {
    [K in DeckEvent]: Set<DeckListener>;
};

function resolveElementRef(
    element: Pick<Element, "source" | "objectId"> & { key?: string },
): ElementRef {
    if (element.key) {
        const ref = parseElementKey(element.key);
        if (ref) return ref;
    }
    return toElementRef(element);
}

/**
 * Single source of truth for the map.
 *
 * MapManager is an interaction facade: it owns picking, hover, selection and
 * editing, and leaves drawing to the scene binding's pipelines.
 *
 * Three things bind into it — the MapLibre map (`bindMap`), the deck overlay
 * (`bindDeck`) and the interaction runtime (`bindInteraction`) — and
 * `dispose()` drops all three, the runtime first.
 *
 * Its public input surface is deliberately narrow: {@link onMap} takes camera
 * events only, and the pointer is a read ({@link pointerLngLat}). The pointer
 * bindings — every `onMap` pointer event, the deck picks, capture-phase
 * document events — are private, and reach exactly one party as the
 * `InteractionSources` value `bindInteraction` builds. A method absent from
 * the type you can reach cannot be called.
 *
 * Known weight: the class spans seven roles — element resolution, picking,
 * the deck and map event buses, the interaction host's facades, selection,
 * hover, and the camera/gesture controls — and building the facades has to
 * live here because they close over the private bindings. Splitting the
 * roles apart is tracked as follow-up work; add an eighth with that in mind.
 */
export class MapManager {
    private static readonly logger = Logger.get("map");

    private readonly elementStore: ElementStore;
    /** Element mutations, routed through the sources that own them (local elements through `LocalSource`). */
    private readonly actions: ElementActions;

    private deckListeners: DeckListenerMap;
    /** The interaction runtime bound into this manager, if any. */
    private interaction?: InteractionRuntime;

    private map?: MapLibre;
    private viewState?: MapViewState;
    private viewPort?: WebMercatorViewport;

    /** MapLibre event listener cleanup functions (called on dispose). */
    private readonly mapListenerCleanups: (() => void)[] = [];
    /** Queued onMap listeners registered before bindMap was called. */
    private pendingMapListeners: {
        event: string;
        listener: (ev: MapEventType[keyof MapEventType]) => void;
    }[] = [];

    constructor(elementStore: ElementStore, actions: ElementActions) {
        this.elementStore = elementStore;
        this.actions = actions;
        this.deckListeners = {
            onHover: new Set(),
            onClick: new Set(),
            onDrag: new Set(),
            onDragEnd: new Set(),
            onDragStart: new Set(),
        };
    }

    dispose(): void {
        // The runtime first: it ends any live session — releasing its custody
        // while the store, the pipeline and the map are all still here — and
        // unbinds its handlers before the map and the deck are dropped.
        this.interaction?.detach();
        this.interaction = undefined;

        for (const set of Object.values(this.deckListeners)) set.clear();
        this.pendingMapListeners = [];
        // Remove MapLibre event listeners registered in bindMap.
        this.mapListenerCleanups.forEach((cleanup) => cleanup());
        this.mapListenerCleanups.length = 0;
        // Reset stores so overlay components clear on unmount.
        const { resetSelection, resetHover, resetTerrain } = useMapStore.getState();
        resetSelection();
        resetHover();
        resetTerrain();
        this.hoverClearPending = false;
        useStackedObjectSelectionStore.getState().dismiss();
        clearAreaSelection();
        useWatchboxPanelStore.getState().select(null);
        useWatchboxPanelStore.getState().hover(null);

        // Drop the bound instances so a disposed manager can't touch a dead map.
        this.map = undefined;
        this.deck = undefined;
        this.viewState = undefined;
        this.viewPort = undefined;
        this.pointer = null;
    }

    // -----------------------------------------------------------------------
    // Data mutation delegation
    // -----------------------------------------------------------------------

    /**
     * Resolve an element by key. Live and historical elements share one store —
     * `elementClass` keys them apart — so there is a single resolution path.
     */
    getElement(key: string): Element | undefined {
        return this.elementStore.get(key);
    }

    /**
     * Clear selection, hover, and the stacked-element picker — interaction
     * state keyed to element keys that stop resolving across a mode switch
     * (e.g. a historical stacked picker must not survive the switch back to
     * live).
     *
     * Called on every live/historical mode change (see `MapView`). It used to be
     * driven by the auxiliary-source set/clear; with a single store there is no
     * such transition to hang it on, but the interaction state still has to be
     * dropped — a historical element key never resolves in live mode, and the
     * selection is URL-persisted, so a stale key would survive a reload.
     */
    resetInteractionState(): void {
        const { resetSelection, resetHover } = useMapStore.getState();
        resetSelection();
        resetHover();
        this.hoverClearPending = false;
        useStackedObjectSelectionStore.getState().dismiss();
        clearAreaSelection();
    }

    /**
     * Dispatch to a SNAPSHOT of the listener set. The interaction runner
     * rebinds from inside a deck handler (a click that opens the disambiguation
     * popover hands the pointer to the surface, whose own click handler binds
     * right then), and `Set.forEach` visits entries added mid-iteration — the
     * new handler would see the very click that bound it, close the popover,
     * rebind the previous set, and loop without end. A listener bound during a
     * dispatch first hears the next event; one unsubscribed during it hears
     * nothing more (the `live` flag in `onDeck`).
     */
    private fireDeck(listeners: keyof DeckListenerMap, info: MapInfo): void {
        const element = this.elementFromInfo(info.picking);
        for (const l of [...this.deckListeners[listeners]]) l(info, element);
    }

    /** When set, the next deck onClick is dropped — a session consumed the
     *  press→release pair deck would synthesize it from (a trace's release).
     *  Cleared by the next map mousedown: the drop is scoped to that pair. */
    private consumeNextClick = false;

    /**
     * Drop the deck click synthesized from the press→release pair a session just
     * consumed, if deck synthesizes one at all. Scoped to that pair — a new
     * press clears it — so a release that produced no click cannot leave the
     * operator's next genuine click swallowed.
     */
    consumeNextDeckClick(): void {
        this.consumeNextClick = true;
    }

    private deckEventListener(
        event: keyof DeckListenerMap,
    ): (picking: PickingInfo, src?: { srcEvent?: Event }) => void {
        return (picking: PickingInfo, src?: { srcEvent?: Event }) => {
            if (event === "onClick" && this.consumeNextClick) {
                this.consumeNextClick = false;
                return;
            }

            let picks: Element[] = [];
            if (event === "onClick" && this.deck) {
                picks =
                    this.deck
                        .pickMultipleObjects({
                            x: picking.x,
                            y: picking.y,
                            radius: 4,
                            depth: 20,
                        })
                        ?.map((p) => this.elementFromInfo(p))
                        .filter((e): e is Element => e !== undefined) ?? [];
            }

            // deck fires onClick for EVERY mouse button; the mjolnir event
            // carries the source button so consumers can tell them apart.
            const srcEvent = src?.srcEvent;
            const button = srcEvent instanceof MouseEvent ? srcEvent.button : undefined;

            this.fireDeck(event, {
                picking,
                ref: refFromInfo(picking)?.object,
                button,
                viewState: this.viewState,
                viewPort: this.viewPort,
                picks,
            });
        };
    }

    onMetrics(listener: (metrics: DeckMetrics) => void): () => void {
        if (this.deck) this.deck.setProps({ _onMetrics: listener });
        return () => {
            if (this.deck) this.deck.setProps({ _onMetrics: undefined });
        };
    }

    // -----------------------------------------------------------------------
    // Initialization
    // -----------------------------------------------------------------------

    /** Initialize layers. Called after both map and deck are bound. */
    init(): void {
        MapManager.logger.info("Initializing manager");
    }

    // -----------------------------------------------------------------------
    // Deck / Map binding
    // -----------------------------------------------------------------------

    /**
     * Subscribe a listener to an event fired by Deck. `unsub()` means "no
     * further calls", the event being dispatched right now included — the same
     * contract as {@link bindMapEvent}, for the same reason: the interaction
     * runner rebinds from inside handlers. Private: the deck picks reach the
     * interaction runtime through `bindInteraction` and nobody else.
     */
    private onDeck(event: DeckEvent, listener: DeckListener): ManagerSubscription {
        let live = true;
        const wrapped: DeckListener = (info, element) => {
            if (live) listener(info, element);
        };
        this.deckListeners[event].add(wrapped);

        return {
            event,
            unsub: () => {
                live = false;
                this.deckListeners[event].delete(wrapped);
            },
        };
    }

    /**
     * Subscribe to a camera event — the view moving, zooming, settling. The
     * public half of the map's events: uncontended, so any feature may listen.
     * Queued until {@link bindMap} if the map is not bound yet.
     */
    onMap<T extends CameraEvent>(
        event: T,
        listener: (ev: MapEventType[T]) => void,
    ): ManagerSubscription {
        return this.bindMapEvent(event, listener);
    }

    /**
     * Subscribe a listener to any event fired by MapLibre, pointer events
     * included. Private: the interaction runtime reaches it as the `onMap` of
     * the `InteractionSources` value {@link bindInteraction} builds; features
     * get the camera-only {@link onMap}. If the map isn't bound yet, the
     * listener is queued and attached once {@link bindMap} is called.
     *
     * `unsub()` means "no further calls", including for the event being
     * dispatched right now. maplibre's `Evented.fire` iterates a copy of its
     * listener list, so a listener that another listener removes mid-dispatch
     * would otherwise still run once for that event — the interaction runner
     * rebinds from inside handlers, and the outgoing handlers must not see
     * the click that ended their session. The deck side ({@link onDeck},
     * `fireDeck`) keeps the same contract by the same means.
     */
    private bindMapEvent<T extends keyof MapEventType>(
        event: T,
        listener: (ev: MapEventType[T]) => void,
    ): ManagerSubscription {
        let live = true;
        const wrapped = (ev: MapEventType[T]) => {
            if (live) listener(ev);
        };

        if (this.map) {
            const sub = this.map.on(event, wrapped);
            return {
                event,
                unsub() {
                    live = false;
                    sub.unsubscribe();
                },
            };
        }

        // Queue for later attachment in bindMap
        const pending = {
            event: event as string,
            listener: wrapped as (ev: MapEventType[keyof MapEventType]) => void,
        };
        this.pendingMapListeners.push(pending);

        return {
            event,
            unsub: () => {
                live = false;
                // If already flushed to the map, remove the real listener
                if (this.map) {
                    this.map.off(event, wrapped);
                }
                // Also remove from pending queue in case it hasn't been flushed yet
                const idx = this.pendingMapListeners.indexOf(pending);
                if (idx >= 0) this.pendingMapListeners.splice(idx, 1);
            },
        };
    }

    /**
     * Subscribe a capture-phase listener on the document — the same unit of
     * cleanup as a map listener, for the input a map-only listener never
     * sees: a drag's release that lands off the canvas. Private: the
     * interaction runtime's, through `bindInteraction`.
     */
    private onDocument<K extends keyof DocumentEventMap>(
        event: K,
        listener: (ev: DocumentEventMap[K]) => void,
    ): ManagerSubscription {
        document.addEventListener(event, listener, true);
        return {
            event,
            unsub() {
                document.removeEventListener(event, listener, true);
            },
        };
    }

    /**
     * Binds the {@link MapboxOverlay deck} instance to the manager.
     */
    private deck?: MapboxOverlay;

    /**
     * Pick the topmost element at map-container-relative screen coordinates.
     * Uses deck.gl's pickObject with a small tolerance radius.
     * Returns the Element or undefined if nothing was picked.
     */
    pickAtScreen(x: number, y: number): Element | undefined {
        if (!this.deck) return undefined;
        const result = this.deck.pickObject({ x, y, radius: 4 });
        if (!result) return undefined;
        return this.elementFromInfo(result);
    }

    /**
     * Re-pick at a canvas-relative position with a higher depth, progressively
     * revealing more overlapping elements.
     */
    pickMoreAt(x: number, y: number, depth: number): Element[] {
        if (!this.deck) return [];
        return (
            this.deck
                .pickMultipleObjects({ x, y, radius: 4, depth })
                ?.map((p) => this.elementFromInfo(p))
                .filter((e): e is Element => e !== undefined) ?? []
        );
    }

    /**
     * Returns all elements that have at least one vertex inside the given
     * closed polygon, expressed as lng/lat vertex pairs.
     */
    pickInsidePolygon(lngLatVertices: readonly [number, number][]): Element[] {
        const polygon = turf.polygon([[...lngLatVertices, lngLatVertices[0]]]);
        const results: Element[] = [];
        for (const el of this.elementStore.getAll()) {
            try {
                const allPos = el.geometry.getAllPos();
                const dim = el.geometry.dim;
                for (let i = 0; i < allPos.length; i += dim) {
                    const pt = turf.point([allPos[i], allPos[i + 1]]);
                    if (turf.booleanPointInPolygon(pt, polygon)) {
                        results.push(el);
                        break;
                    }
                }
            } catch {
                // skip elements with invalid geometry
            }
        }
        return results;
    }

    /**
     * Geometric fallback pick: test if a lng/lat point falls inside any
     * polygon/multipolygon element. Catches clicks on non-pickable labels
     * sitting on top of features where deck.gl picking misses.
     */
    pickAtLngLat(lng: number, lat: number): Element | undefined {
        const pt = turf.point([lng, lat]);
        for (const el of this.elementStore.getAll()) {
            try {
                const geo = el.geometry.toGeoJSONGeometry();
                if (
                    (geo.type === "Polygon" || geo.type === "MultiPolygon") &&
                    turf.booleanPointInPolygon(pt, geo)
                ) {
                    return el;
                }
            } catch {
                // skip invalid geometry
            }
        }
        return undefined;
    }

    bindDeck(deck: MapboxOverlay): this {
        this.deck = deck;
        deck.setProps({
            ...Object.fromEntries(
                ["onClick", "onHover", "onDrag", "onDragStart", "onDragEnd"].map((event) => [
                    event,
                    this.deckEventListener(event as DeckEvent),
                ]),
            ),
        });
        // Hover STATE only — the cursor and the gesture locks are owned by the
        // interaction runtime's diffed appliers, which derive them from the
        // session snapshot + hover state. No cursor writes here.
        this.onDeck("onHover", (info, element) => {
            this.setHover(element ?? null);
            useWatchboxPanelStore.getState().hover(this.watchboxKeyFromInfo(info.picking) ?? null);
        });

        return this;
    }

    /**
     * Binds the interaction runtime to the manager — the third bind, beside
     * the map and the deck.
     *
     * The runtime declares what it requires (`interaction-runtime/contract.ts`);
     * the manager satisfies the three map-side interfaces from its own bindings,
     * actions and pipeline, right here, and hands them to `attach` as one value.
     * The privileged pointer bindings therefore reach exactly one party and are
     * not a public method anyone can call. The app-level environment (the tool
     * registry, the modes and preferences the runtime reads) is the caller's to
     * supply — it is not the map's to know.
     */
    bindInteraction(runtime: InteractionRuntime, environment: InteractionEnvironment): this {
        this.interaction?.detach();
        this.interaction = runtime;

        const host: InteractionHost = {
            sources: {
                onMap: (event, listener) => this.bindMapEvent(event, listener),
                onDeck: (event, listener) => this.onDeck(event, listener),
                onDocument: (event, listener) => this.onDocument(event, listener),
                // A saved watchbox is pickable, so it gets the idle pointer cursor,
                // but only an element suppresses double-click zoom. The runtime
                // reads both host facts and never learns what they are made of.
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
                selectElement: (element) => this.selectElement(element),
                dismissSelection: () => this.handleEmptyClick(),
                disambiguate: (candidates, screen, truncated) =>
                    useStackedObjectSelectionStore.getState().open(candidates, screen, truncated),
                dismissDisambiguation: () => useStackedObjectSelectionStore.getState().dismiss(),
                selectArea: (elements, boundary) => {
                    if (elements.length === 0) clearAreaSelection();
                    else useAreaSelectionStore.getState().select([...elements], boundary);
                },
                pickInsidePolygon: (vertices) => this.pickInsidePolygon(vertices),
                projectToScreen: (lngLat) => this.projectToScreen(lngLat),
                consumeNextDeckClick: () => this.consumeNextDeckClick(),
                setCursor: (cursor) => this.setCursor(cursor),
                disablePan: () => this.disablePan(),
                enablePan: () => this.enablePan(),
                disableDoubleClickZoom: () => this.disableDoubleClickZoom(),
                enableDoubleClickZoom: () => this.enableDoubleClickZoom(),
                suppressHoverClear: (suppress) => this.suppressHoverClear(suppress),
            },
            custody: {
                add: (elements) => this.actions.add(elements),
                delete: (key) => this.actions.delete(key),
                draft: (element) => this.actions.setDraft(element),
                clear: () => this.actions.clearDraft(),
                commit: () => this.actions.commitDraft(),
                isLocked: () => !!this.actions.getDraft(),
            },
            environment,
        };
        runtime.attach(host);
        return this;
    }

    /**
     * Binds the {@link MapLibre map} instance to the manager.
     */
    bindMap(map: MapLibre): this {
        this.map = map;

        const updateView = (map: MapLibre) => {
            this.viewState = {
                longitude: map.getCenter().lng,
                latitude: map.getCenter().lat,
                zoom: map.getZoom(),
                pitch: map.getPitch(),
                bearing: map.getBearing(),
            };

            this.viewPort = new WebMercatorViewport({
                ...this.viewState,
                width: map.getCanvas().clientWidth,
                height: map.getCanvas().clientHeight,
            });
        };

        const onMove = (ev: MapEventType["move"]) => {
            updateView(ev.target);
        };

        // A pending click-drop belongs to the press→release pair that armed it.
        // deck only synthesizes a click when that pair barely moved (a hammer
        // Tap: <9px, <250ms), so most traces produce none — and a one-shot
        // flag left armed would eat the operator's NEXT genuine click. A new
        // press supersedes it.
        const onMouseDown = () => {
            this.consumeNextClick = false;
        };
        const onMouseUp = () => {};
        // The pointer's last position, kept as a read — observational, like
        // hover: nothing competes for it and nothing acts on it here.
        const onMouseMove = (ev: MapEventType["mousemove"]) => {
            this.pointer = [ev.lngLat.lng, ev.lngLat.lat];
        };

        map.on("move", onMove);
        map.on("mousedown", onMouseDown);
        map.on("mouseup", onMouseUp);
        map.on("mousemove", onMouseMove);

        this.mapListenerCleanups.push(
            () => map.off("move", onMove),
            () => map.off("mousedown", onMouseDown),
            () => map.off("mouseup", onMouseUp),
            () => map.off("mousemove", onMouseMove),
        );

        this.defaultCursor();

        updateView(map);

        // Flush any listeners queued before the map was available.
        for (const pending of this.pendingMapListeners) {
            map.on(pending.event as keyof MapEventType, pending.listener);
            this.mapListenerCleanups.push(() =>
                map.off(pending.event as keyof MapEventType, pending.listener),
            );
        }
        this.pendingMapListeners = [];

        return this;
    }

    disablePan(): void {
        this.map?.dragPan.disable();
        MapManager.logger.debug("Disabled pan");
    }

    enablePan(): void {
        this.map?.dragPan.enable();
        MapManager.logger.debug("Enabled pan");
    }

    disableDoubleClickZoom(): void {
        this.map?.doubleClickZoom.disable();
        MapManager.logger.debug("Disabled double click zoom");
    }

    enableDoubleClickZoom(): void {
        this.map?.doubleClickZoom.enable();
        MapManager.logger.debug("Enabled double click zoom");
    }

    private pointer: [number, number] | null = null;

    /**
     * Where the pointer last was over the map, as lng/lat — null until it has
     * been over the canvas. The observational read a feature uses instead of a
     * `mousemove` subscription (the paste drop point, for one).
     */
    pointerLngLat(): [number, number] | null {
        return this.pointer;
    }

    /** Project a [lng, lat] pair to canvas [x, y] pixels. Returns undefined if viewport is not yet bound. */
    projectToScreen(lngLat: [number, number]): [number, number] | undefined {
        return this.viewPort?.project(lngLat) as [number, number] | undefined;
    }

    setBasemapVisibility(visible: boolean): void {
        const canvas = this.map?.getCanvas();
        if (!canvas) return;
        canvas.style.opacity = visible ? "1" : "0";
    }

    // -----------------------------------------------------------------------
    // Selection
    // -----------------------------------------------------------------------

    restoreSelection(selection: ElementRef): void {
        useMapStore.getState().select(getElementKey(selection));
    }

    selectElement(element: Element): void {
        this.restoreSelection(resolveElementRef(element));
    }

    clearSelection(): void {
        const { selected, resetSelection } = useMapStore.getState();
        if (!selected) return;
        resetSelection();
    }

    // -----------------------------------------------------------------------
    // Hover
    // -----------------------------------------------------------------------

    /** While true a hover clear is withheld — the interaction runtime's hover policy, see {@link suppressHoverClear}. */
    private hoverClearSuppressed = false;
    /** A clear arrived while withheld and is owed on release — unless a real hover target superseded it. */
    private hoverClearPending = false;

    /**
     * Update hover state. Called from the deck `onHover` handler.
     */
    setHover(element: Element | null): void {
        const { hovered, hover, resetHover } = useMapStore.getState();
        const reqHovered = element?.key;
        if (reqHovered === hovered) return;
        if (!reqHovered) {
            // A map surface over the canvas intercepts the mouse, so deck
            // reports hover-on-nothing beneath its DOM; the runtime withholds
            // that clear while the surface owns the pointer, and it is applied
            // on release if nothing real was hovered in between.
            if (this.hoverClearSuppressed) {
                this.hoverClearPending = true;
                return;
            }
            resetHover();
        } else {
            this.hoverClearPending = false;
            hover(reqHovered);
        }
    }

    /**
     * Withhold hover clears, or release them and apply the one clear a
     * surface left standing: the hover-on-nothing deck reported while the
     * pointer was over the surface's DOM, which is the highlight on the row
     * the operator picked from. A hover set while withheld — the pointer over
     * another element beside the surface — is not a clear and survives the
     * release. Written by the interaction runtime's hover-policy applier
     * alone, from who owns the pointer; a surface never has to remember to
     * release it.
     */
    suppressHoverClear(suppress: boolean): void {
        if (this.hoverClearSuppressed === suppress) return;
        this.hoverClearSuppressed = suppress;
        if (suppress || !this.hoverClearPending) return;
        this.hoverClearPending = false;
        this.setHover(null);
    }

    /** Handle an empty-space click: clear selection + notify dismiss listeners. */
    handleEmptyClick(): void {
        this.clearSelection();
        useMapStore.getState().bumpDismiss();
    }

    /**
     * Subscribe to an ElementStore event.
     */
    onStore(
        event: "add" | "remove" | "update",
        listener: (element: Element[]) => void,
    ): ManagerSubscription {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        this.elementStore.on(event as any, listener as any);
        const unsub = () => this.elementStore.off(event, listener);
        return {
            event,
            unsub() {
                unsub();
            },
        };
    }

    setCursor(cursor: string): void {
        if (this.map) this.map.getCanvas().style.cursor = cursor;
    }

    defaultCursor(): void {
        this.setCursor("default");
    }

    private elementFromInfo(info: PickingInfo): Element | undefined {
        const ref = refFromInfo(info);
        if (!ref || ref.object.type !== "element") return;
        return this.actions.get(ref.object.key);
    }

    private watchboxKeyFromInfo(info: PickingInfo): string | undefined {
        const ref = refFromInfo(info);
        // The watchbox being drawn is not a saved one: it has nothing to open or hover.
        if (!ref || ref.object.type !== WATCHBOX_STORE_ID || ref.object.key === WATCHBOX_DRAFT_KEY)
            return;
        return ref.object.key;
    }
}

function refFromInfo(info: PickingInfo): PrimitiveRef | undefined {
    const id = primitiveId(info);
    if (!id) return;
    return codec.decode(id);
}
