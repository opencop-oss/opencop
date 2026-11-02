// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * What the interaction runtime REQUIRES of its host, declared by the runtime
 * in its own vocabulary.
 *
 * Three of the four are the map's to satisfy — `MapManager.bindInteraction`
 * builds them from its own bindings, actions and pipeline and hands them to
 * `attach` — and the fourth is the app's: the tool registry and the modes and
 * preferences the runtime reads, gathered by the composition point. The
 * runtime imports no feature and reaches the map only through this value.
 *
 * The privileged input bindings (`onMap` pointer events, `onDeck`,
 * `onDocument`) exist only as a value handed to one party, which is what
 * makes "who may bind the pointer" a fact of construction rather than a
 * convention every new listener has to remember.
 */
import type { PickedRef } from "@opencop/contributions";
import type { PickingInfo } from "@deck.gl/core";
import type { MapEventType } from "maplibre-gl";
import type { Element } from "@opencop/elements";
import type { ToolDescriptor } from "@opencop/tools";

/**
 * A bound input listener. `unsub()` means "no further calls" — the event being
 * dispatched right now included, which is what lets the runner rebind from
 * inside a handler without the outgoing handlers seeing the click that ended
 * their session.
 */
export interface InputSubscription {
    readonly event: string;
    unsub(): void;
}

/** What a deck pick hands a listener. */
export interface DeckPick {
    readonly picking: PickingInfo;
    /** Mouse button of the source event (0 left, 2 right); undefined when unknown. */
    readonly button?: number;
    /** Every element under the pointer, nearest first. */
    readonly picks: Element[];
    /**
     * The object the nearest pick was drawn for, decoded from its scene primitive's
     * id; absent when the nearest pick is not a scene primitive.
     */
    readonly ref?: PickedRef;
}

/**
 * Whether a deck click came from the primary button — or from an unknown one,
 * which the clicks deck synthesizes itself are. deck fires `onClick` for every
 * button, so every click consumer applies this once, through here.
 */
export function isPrimaryButton(info: Pick<DeckPick, "button">): boolean {
    return info.button === undefined || info.button === 0;
}

export type DeckEvent = "onHover" | "onClick" | "onDragStart" | "onDrag" | "onDragEnd";
export type DeckListener = (info: DeckPick, element?: Element) => void;

/**
 * Input, and the host state the runtime observes. The bindings are
 * privileged: whoever holds this value owns the pointer. The reads are
 * observational — they feed a derivation and never change a session.
 */
export interface InteractionSources {
    /** MapLibre events, with `ev.lngLat` and `ev.originalEvent`; queued until the map binds. */
    onMap<T extends keyof MapEventType>(
        event: T,
        listener: (ev: MapEventType[T]) => void,
    ): InputSubscription;
    /** Deck picks — the resolved element under the pointer and everything beneath it. */
    onDeck(event: DeckEvent, listener: DeckListener): InputSubscription;
    /**
     * Capture-phase document events — for the input a map-only listener never
     * sees, such as a drag's release that lands off the canvas.
     */
    onDocument<K extends keyof DocumentEventMap>(
        event: K,
        listener: (ev: DocumentEventMap[K]) => void,
    ): InputSubscription;
    /** Something clickable is under the pointer — an element, or a drawn thing the host knows responds to a click. The idle cursor follows it. */
    hoveringPickable(): boolean;
    /** An element is under the pointer. The idle double-click-zoom lock follows it. */
    hoveringElement(): boolean;
    /** Hear either hover fact change. */
    subscribeHover(listener: () => void): () => void;
}

/**
 * The verbs the runtime performs on the host. The two selections are not
 * read or cleared through here: each registers itself as an Escape claimant
 * (`arbitration/escape-claimants.ts`) from the feature that owns it.
 */
export interface InteractionActions {
    selectElement(element: Element): void;
    /**
     * Clear the map selection the way an empty-space click does — whatever
     * listens for a dismissal (the mapbar's panels) hears it too.
     */
    dismissSelection(): void;
    /** Several elements under one click: let the operator say which one they meant. */
    disambiguate(candidates: Element[], screen: { x: number; y: number }, truncated: boolean): void;
    dismissDisambiguation(): void;
    /**
     * A tool's gathered selection becomes host state — the area selection the
     * events panel lists and the map outlines. An empty hand-over clears it.
     */
    selectArea(elements: readonly Element[], boundary: readonly [number, number][]): void;
    /** Every element with at least one vertex inside the closed polygon. */
    pickInsidePolygon(vertices: readonly [number, number][]): Element[];
    /** A lng/lat pair to canvas pixels through the live viewport; undefined before the map binds. */
    projectToScreen(lngLat: [number, number]): [number, number] | undefined;
    /**
     * Drop the deck click synthesized from the press→release pair a session
     * just consumed, if deck synthesizes one at all.
     */
    consumeNextDeckClick(): void;
    setCursor(cursor: string): void;
    disablePan(): void;
    enablePan(): void;
    disableDoubleClickZoom(): void;
    enableDoubleClickZoom(): void;
    /**
     * Withhold hover clears while `suppress` is true — a map-owned surface
     * over the canvas makes deck report hover-on-nothing beneath its DOM — and
     * apply the clear the surface left standing when released. Driven by the
     * runtime's hover policy, derived from who owns the pointer.
     */
    suppressHoverClear(suppress: boolean): void;
}

/**
 * The lifecycle's contract with the element store: add and delete, and the
 * store's draft. While a draft is held, store events for its key are
 * suppressed.
 */
export interface CustodySink {
    add(elements: Element | Element[]): void;
    delete(key: string): void;
    draft(element: Element): void;
    clear(): void;
    commit(): void;
    isLocked(): boolean;
}

/**
 * The app-level environment: the tool registry, and the modes and preferences
 * the runtime reads. Not the map's to supply — the composition point gathers
 * it from the features that own each piece.
 */
export interface InteractionEnvironment {
    tool(id: string): ToolDescriptor | undefined;
    tools(): readonly ToolDescriptor[];
    /** The tool that edits this element, or `undefined` for the generic edit. */
    editToolFor(element: Element): ToolDescriptor | undefined;
    /** Hear registry changes — a tool registered or removed. */
    subscribeTools(listener: () => void): () => void;
    /** The operator's current value for a tool's style input, as the toolbar persists it. */
    styleValue<T>(toolId: string, label: string, defaultValue: T): T;
    /** A tool's contributed code threw — attribute the defect to the tool (a plugin's, or the app's own). */
    reportToolDefect(toolId: string, error: unknown): void;
    /** The tool ran clean again — reopen its once-per-tool defect latch. */
    clearToolDefect(toolId: string): void;
    /** The host is showing a historical snapshot; live drawing has no meaning over it. */
    isHistorical(): boolean;
}

/**
 * How a pure derivation reaches a descriptor: supplied by the caller, never
 * imported from the registry, so the derivations stay testable with fixtures.
 * The environment's `tool` is the one the runtime passes.
 */
export type DescriptorResolver = (id: string) => ToolDescriptor | undefined;

export interface InteractionHost {
    readonly sources: InteractionSources;
    readonly actions: InteractionActions;
    readonly custody: CustodySink;
    readonly environment: InteractionEnvironment;
}

/**
 * The runtime as its host holds it. `attach` binds everything to the host;
 * `detach` ends any live session (releasing its custody) and unbinds
 * everything, in that order. A detached runtime can be attached again.
 */
export interface InteractionRuntime {
    attach(host: InteractionHost): void;
    detach(): void;
}
