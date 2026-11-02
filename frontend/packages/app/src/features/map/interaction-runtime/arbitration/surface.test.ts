// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it, vi } from "vitest";
import { noop } from "lodash";
import type { InteractionHost } from "../contract";
import {
    closeSurface,
    currentSurface,
    invokeContextMenu,
    openSurface,
    registerContextMenuOpener,
    subscribeSurface,
    surfaceEscapeClaimant,
    surfacePointerOwner,
    withdrawSurface,
    type ContextMenuInvocation,
    type MapSurfaceId,
} from "./surface";

/**
 * The open map surface — arbitration's one slot for a menu anchored over the
 * canvas. One is open at a time, opening replaces or closes, and the disposer
 * withdraws by identity. As the Escape channel's `surface` claimant it is
 * active while a surface is open and dismisses by closing it; as the pointer
 * channel's `surface` owner it binds one thing, a primary click that closes.
 */

describe("the open map surface", () => {
    const openFor = (id: MapSurfaceId) => {
        const close = vi.fn();
        const release = openSurface({ id, close });
        return { close, release };
    };

    afterEach(() => {
        withdrawSurface();
    });

    it("declares itself open, and closing clears the slot and tells the surface", () => {
        const { close } = openFor("map-context-menu");
        expect(currentSurface()?.id).toBe("map-context-menu");

        expect(closeSurface()).toBe(true);
        expect(close).toHaveBeenCalledTimes(1);
        expect(currentSurface()).toBeNull();
    });

    it("closing with nothing open is a refusal, not a throw", () => {
        expect(closeSurface()).toBe(false);
    });

    it("opening a second surface closes the first rather than hiding it", () => {
        const first = openFor("map-context-menu");
        openFor("stacked-object-selection");

        expect(first.close).toHaveBeenCalledTimes(1);
        expect(currentSurface()?.id).toBe("stacked-object-selection");
    });

    it("re-opening under the same id replaces the entry without closing it", () => {
        const { close } = openFor("map-context-menu");
        openFor("map-context-menu");

        expect(close).not.toHaveBeenCalled();
        expect(currentSurface()?.id).toBe("map-context-menu");
    });

    it("the disposer withdraws a surface that closed itself, without calling close", () => {
        const { close, release } = openFor("map-context-menu");

        release();

        expect(close).not.toHaveBeenCalled();
        expect(currentSurface()).toBeNull();
    });

    it("a stale disposer cannot evict the surface that replaced it", () => {
        const first = openFor("map-context-menu");
        openFor("stacked-object-selection");

        first.release();

        expect(currentSurface()?.id).toBe("stacked-object-selection");
    });

    it("a stale disposer cannot evict its replacement under the SAME id", () => {
        // The replaced instance is a different object with an equal id, so the
        // disposer has to withdraw by identity — an id match would clear the
        // live surface and leave the popover open with nothing registered.
        const first = openFor("map-context-menu");
        const second = openFor("map-context-menu");

        first.release();

        expect(currentSurface()).not.toBeNull();
        expect(second.close).not.toHaveBeenCalled();
    });

    it("a surface whose close runs its own disposer does not recurse", () => {
        let release = noop;
        const close = vi.fn(() => release());
        release = openSurface({ id: "map-context-menu", close });

        expect(closeSurface()).toBe(true);
        expect(close).toHaveBeenCalledTimes(1);
        expect(currentSurface()).toBeNull();
    });

    it("withdrawing clears the slot without closing — the surfaces unmount with the map", () => {
        const { close } = openFor("map-context-menu");

        withdrawSurface();

        expect(close).not.toHaveBeenCalled();
        expect(currentSurface()).toBeNull();
    });
});

describe("the surface as an Escape claimant", () => {
    afterEach(() => {
        withdrawSurface();
    });

    it("is active while a surface is open, and dismissing closes it", () => {
        expect(surfaceEscapeClaimant.isActive()).toBe(false);
        const close = vi.fn();
        openSurface({ id: "map-context-menu", close });

        expect(surfaceEscapeClaimant.isActive()).toBe(true);
        surfaceEscapeClaimant.dismiss();

        expect(close).toHaveBeenCalledTimes(1);
        expect(currentSurface()).toBeNull();
        expect(surfaceEscapeClaimant.isActive()).toBe(false);
    });
});

describe("the surface as a pointer owner", () => {
    afterEach(() => {
        withdrawSurface();
    });

    /** A host whose deck listeners a test can fire. */
    function hostWithDeck() {
        const listeners: Array<(info: unknown) => void> = [];
        const host = {
            sources: {
                onDeck: vi.fn((_event: string, listener: (info: unknown) => void) => {
                    listeners.push(listener);
                    return { event: "onClick", unsub: vi.fn() };
                }),
            },
        } as unknown as InteractionHost;
        return { host, click: (button?: number) => listeners.forEach((l) => l({ button })) };
    }

    it("is active while a surface is open, and hears the slot change", () => {
        const heard = vi.fn();
        const unsubscribe = subscribeSurface(heard);
        expect(surfacePointerOwner.isActive()).toBe(false);

        const release = openSurface({ id: "map-context-menu", close: vi.fn() });
        expect(surfacePointerOwner.isActive()).toBe(true);
        expect(heard).toHaveBeenCalledTimes(1);

        release();
        expect(surfacePointerOwner.isActive()).toBe(false);
        expect(heard).toHaveBeenCalledTimes(2);
        unsubscribe();
    });

    it("its set closes the surface on a primary or unknown button, never on the right button", () => {
        const close = vi.fn();
        openSurface({ id: "map-context-menu", close });
        const { host, click } = hostWithDeck();
        const bound = surfacePointerOwner.bind(host);
        expect(bound).toHaveLength(1);

        click(2);
        expect(close).not.toHaveBeenCalled();
        click(undefined);
        expect(close).toHaveBeenCalledTimes(1);
        expect(currentSurface()).toBeNull();
    });

    it("re-opening under the same id is not a change the runner needs to hear", () => {
        const heard = vi.fn();
        openSurface({ id: "map-context-menu", close: vi.fn() });
        const unsubscribe = subscribeSurface(heard);

        openSurface({ id: "map-context-menu", close: vi.fn() });

        // The instance changed, the slot's occupancy did not — and the owner's
        // identity is constant, so a notification would rebind an identical set.
        expect(heard).toHaveBeenCalledTimes(1);
        unsubscribe();
    });
});

describe("the context menu's opener", () => {
    const at: ContextMenuInvocation = {
        screen: { x: 1, y: 2 },
        client: { x: 3, y: 4 },
        lngLat: { lng: 5, lat: 6 },
    };

    it("with none registered, invoking opens nothing and says so", () => {
        expect(invokeContextMenu(at)).toBe(false);
    });

    it("invoking reaches the registered opener with where the click landed", () => {
        const open = vi.fn();
        const unregister = registerContextMenuOpener(open);

        expect(invokeContextMenu(at)).toBe(true);
        expect(open).toHaveBeenCalledWith(at);

        unregister();
        expect(invokeContextMenu(at)).toBe(false);
    });

    it("a stale disposer cannot evict the opener that replaced it", () => {
        const first = vi.fn();
        const second = vi.fn();
        const unregisterFirst = registerContextMenuOpener(first);
        const unregisterSecond = registerContextMenuOpener(second);

        unregisterFirst();
        invokeContextMenu(at);

        expect(second).toHaveBeenCalledTimes(1);
        expect(first).not.toHaveBeenCalled();
        unregisterSecond();
    });
});
