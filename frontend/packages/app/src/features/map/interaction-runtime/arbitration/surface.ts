// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * A MAP SURFACE — something anchored over the map that owns some input while
 * it is open, but is not a session.
 *
 * The session answers "who owns the pointer". A menu anchored to a click is
 * the other case: it has no draft, no product and no custody, so it cannot be
 * a session, and yet while it is open it owns Escape.
 *
 * **Two mechanisms, on purpose.** A *foreign* overlay — a dialog, a combobox,
 * a select in some panel — has no reason to know the map runtime exists, and
 * the DOM sniff in `exceptions.ts` catches every one of them automatically,
 * including surfaces nobody anticipated. That sniff is right for them and
 * wrong for map-owned surfaces, where the runtime must know *which* surface
 * it is and *how to close it* — neither of which a data attribute can say.
 * This module is how a map-owned surface states those two things.
 *
 * One is open at a time. The two surfaces that exist cannot coexist (a
 * right-click outside the picker dismisses it before the context menu opens,
 * and a right-click on the picker never reaches the canvas), so the slot is
 * a single one rather than a stack. Opening a second surface closes the first
 * instead of hiding it.
 *
 * A surface owns keys; it does not own the session. Opening one never changes
 * a session, and starting a session closes it (the runtime's composition
 * point closes the open surface on `activated` and `edit-started`) — a
 * surface anchored to a click cannot stay meaningful once the operator has
 * started drawing.
 *
 * A surface owns the pointer while it is open, too — the `surface` slot of
 * the pointer channel (`./pointer-owners.ts`), second to a live session and
 * ahead of ambient. Its handler set is one thing: a primary click on the
 * canvas closes it. The click that opens the map context menu is ambient's
 * (`./sources/ambient-pointer.ts`) and reaches the menu through the opener
 * registered here — bound only while ambient owns the pointer, so a right-click
 * that belongs to a session never gets this far.
 *
 * The slot is arbitration's state — it is the `surface` claimant of the
 * Escape channel (`./escape-claimants.ts`) and the `surface` owner of the
 * pointer — and its writers are the functions below; the variable never
 * leaves this module.
 */
import Logger from "js-logger";
import { isPrimaryButton } from "../contract";
import type { EscapeClaimant } from "./escape-claimants";
import type { PointerOwner } from "./pointer-owners";

/**
 * The surfaces that exist. A closed set, because the id is not a label: it
 * decides whether opening replaces the slot or closes what was there, and it
 * is what outside code compares against to ask which surface is up.
 */
export type MapSurfaceId = "stacked-object-selection" | "map-context-menu";

export interface MapSurface {
    /** Stable identity, for diagnostics and for replace-on-reopen. */
    readonly id: MapSurfaceId;
    /**
     * Close this surface. Called when Escape reaches it, and when a session
     * starts beneath it. Must be safe to call when already closed.
     */
    close(): void;
}

let current: MapSurface | null = null;
const listeners = new Set<() => void>();

function setCurrent(surface: MapSurface | null): void {
    if (current === surface) return;
    current = surface;
    for (const listener of [...listeners]) listener();
}

/** The open map-owned surface, or null. */
export function currentSurface(): MapSurface | null {
    return current;
}

/** Hear the slot open, close or change surface. */
export function subscribeSurface(listener: () => void): () => void {
    listeners.add(listener);
    return () => {
        listeners.delete(listener);
    };
}

/**
 * Declare a map-owned surface open. Returns the disposer that withdraws it —
 * call it when the surface closes, however it closed.
 *
 * One surface is open at a time: opening a second closes the first rather
 * than hiding it behind an unreachable slot. Re-opening under the same id
 * replaces the entry, so a surface that re-opens without its disposer having
 * run cannot leave a phantom behind — the disposer withdraws the instance it
 * was issued for and no other, so a stale one cannot evict its replacement.
 */
export function openSurface(surface: MapSurface): () => void {
    if (current && current.id !== surface.id) closeSurface();
    setCurrent(surface);
    return () => {
        if (current === surface) setCurrent(null);
    };
}

/**
 * Close the open surface. Returns false when there was none.
 *
 * The slot is cleared before `close()` runs, so the surface's own teardown —
 * which calls the disposer — re-enters against an already-empty slot instead
 * of recursing.
 */
export function closeSurface(): boolean {
    const surface = current;
    if (!surface) return false;
    setCurrent(null);
    surface.close();
    return true;
}

/** Empty the slot WITHOUT closing — the surfaces are unmounting with the map. */
export function withdrawSurface(): void {
    setCurrent(null);
}

/** The surface slot as the Escape channel sees it. */
export const surfaceEscapeClaimant: EscapeClaimant = {
    isActive: () => current !== null,
    dismiss: () => {
        closeSurface();
    },
};

/**
 * The surface slot as the pointer channel sees it: active while a surface is
 * open; its one binding closes the surface on a primary click.
 *
 * On macOS the `contextmenu` that opens the menu fires at mousedown and the
 * right button's click arrives at mouseup — closing on it would flash the menu
 * shut — so only the primary (or an unknown) button closes.
 */
export const surfacePointerOwner: PointerOwner = {
    isActive: () => current !== null,
    identity: () => "surface",
    bind: ({ sources }) => [
        sources.onDeck("onClick", (info) => {
            if (isPrimaryButton(info)) closeSurface();
        }),
    ],
    subscribe: subscribeSurface,
};

/** Where a right-click landed — what the context menu needs to open there. */
export interface ContextMenuInvocation {
    /** Canvas pixels, for a pick at the point. */
    readonly screen: { readonly x: number; readonly y: number };
    /** Viewport pixels, to anchor the menu. */
    readonly client: { readonly x: number; readonly y: number };
    readonly lngLat: { readonly lng: number; readonly lat: number };
}

const logger = Logger.get("interaction");

let contextMenuOpener: ((at: ContextMenuInvocation) => void) | null = null;

/**
 * Register the party that opens the map context menu. The surface slot's open
 * trigger — the right-click ambient hears — calls it; the party then declares
 * the menu open through `openSurface` as any surface does. Returns the
 * disposer. A second registration replaces the first (logged in DEV — two
 * openers is a composition mistake, as it is for every registry here); a
 * stale disposer cannot evict the opener that replaced it.
 */
export function registerContextMenuOpener(open: (at: ContextMenuInvocation) => void): () => void {
    if (import.meta.env.DEV && contextMenuOpener !== null) {
        logger.warn("context-menu opener re-registered; the previous opener is replaced.");
    }
    contextMenuOpener = open;
    return () => {
        if (contextMenuOpener === open) contextMenuOpener = null;
    };
}

/** Open the context menu where the right-click landed. False when no opener is registered — the browser keeps its own menu. */
export function invokeContextMenu(at: ContextMenuInvocation): boolean {
    if (!contextMenuOpener) return false;
    contextMenuOpener(at);
    return true;
}
