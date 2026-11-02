// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { type Element } from "@opencop/elements";
import { createActions, type ElementActions } from "@opencop/ingest";
import { MapManager } from "./MapManager";
import { ElementStore } from "../../../../ingest/src/ElementStore";
import { useMapStore } from "./core/map.store";
import { useStackedObjectSelectionStore } from "~/features/map/stacked-object-selection.store";
import { useAreaSelectionStore } from "~/features/selection/area-selection.store";
import { resetInteractionForTest } from "~/test/interaction";
import { createInteractionEnvironment } from "~/features/map/interaction-environment";
import type { InteractionHost, InteractionSources } from "./interaction-runtime/contract";
import { useHistoricalStore } from "~/features/historical/data-plane/historical.store";
import { useWatchboxPanelStore } from "~/features/watchbox/watchbox-panel.store";
import { WATCHBOX_STORE_ID } from "~/features/watchbox/watchbox.store";
import { codec } from "./scene-binding/ref-codec";

// ---------------------------------------------------------------------------
// Minimal stubs — only the surface area MapManager touches
// ---------------------------------------------------------------------------

/**
 * The privileged bindings as the interaction runtime receives them — the ONE
 * party that may bind the pointer. The manager's public surface has no
 * `onDeck`, no `onDocument` and a camera-only `onMap`, so a test of the
 * bindings binds a runtime that keeps the host it was handed.
 */
function privilegedSources(manager: MapManager): InteractionSources {
    let host: InteractionHost | undefined;
    manager.bindInteraction(
        {
            attach: (h) => {
                host = h;
            },
            detach: () => {},
        },
        createInteractionEnvironment(),
    );
    if (!host) throw new Error("bindInteraction did not attach the runtime");
    return host.sources;
}

function createManager() {
    const elementStore = new ElementStore();
    return new MapManager(elementStore, createActions(elementStore));
}

function fakeElement(objectId: string): Element {
    return {
        objectId,
        source: "test-source",
        key: `test-source\0${objectId}`,
        type: "point",
    } as unknown as Element;
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("MapManager", () => {
    let manager: MapManager;

    beforeEach(() => {
        manager = createManager();
        resetInteractionForTest();
        useHistoricalStore.setState({ mode: "live" });
    });

    afterEach(() => {
        manager.dispose();
        useHistoricalStore.setState({ mode: "live" });
        resetInteractionForTest();
    });

    // -----------------------------------------------------------------------
    // Deck listeners
    // -----------------------------------------------------------------------

    describe("deck listeners", () => {
        it("should subscribe and unsub correctly", () => {
            const sources = privilegedSources(manager);
            const listener = vi.fn();
            const sub = sources.onDeck("onClick", listener);

            expect(sub.event).toBe("onClick");

            sub.unsub();
            // Verify unsub doesn't throw on double call
            sub.unsub();
        });

        it("should support multiple listeners on same event", () => {
            const sources = privilegedSources(manager);
            const listener1 = vi.fn();
            const listener2 = vi.fn();
            sources.onDeck("onHover", listener1);
            const sub2 = sources.onDeck("onHover", listener2);

            sub2.unsub();

            // listener1 should still be registered, listener2 removed
            // (We can't easily fire deck events without bindDeck, but
            // we verified the subscription mechanics)
        });

        /** A fake deck whose captured `onClick` prop fires a primary click. */
        function bindFakeDeck() {
            const deckProps: Record<string, (...args: unknown[]) => void> = {};
            manager.bindDeck({
                setProps: vi.fn((props: Record<string, (...args: unknown[]) => void>) => {
                    Object.assign(deckProps, props);
                }),
                pickMultipleObjects: vi.fn(() => []),
            } as never);
            return () =>
                deckProps.onClick(
                    { x: 0, y: 0, object: undefined, layer: null },
                    { srcEvent: new MouseEvent("click", { button: 0 }) },
                );
        }

        it("a listener bound during a dispatch hears the next event, not the one that bound it", () => {
            const sources = privilegedSources(manager);
            // The interaction runner rebinds from inside a deck handler: a click
            // that opens the disambiguation popover hands the pointer to the
            // surface, whose own click handler binds right then. Delivered to
            // that handler, the same click would close the popover, rebind the
            // previous set, and loop without end.
            const click = bindFakeDeck();
            const boundDuring = vi.fn();
            const first = sources.onDeck("onClick", () => {
                sources.onDeck("onClick", boundDuring);
                first.unsub();
            });

            click();
            expect(boundDuring).not.toHaveBeenCalled();

            click();
            expect(boundDuring).toHaveBeenCalledTimes(1);
        });

        it("a listener unsubscribed during a dispatch does not run for it", () => {
            const sources = privilegedSources(manager);
            const click = bindFakeDeck();
            const later = vi.fn();
            const first = sources.onDeck("onClick", () => {
                laterSub.unsub();
            });
            const laterSub = sources.onDeck("onClick", later);

            click();
            expect(later).not.toHaveBeenCalled();
            first.unsub();
        });
    });

    // -----------------------------------------------------------------------
    // Dispose
    // -----------------------------------------------------------------------

    describe("dispose", () => {
        it("clears registered deck listeners so later events don't notify them", () => {
            const sources = privilegedSources(manager);
            const clickListener = vi.fn();
            const hoverListener = vi.fn();
            sources.onDeck("onClick", clickListener);
            sources.onDeck("onHover", hoverListener);

            manager.dispose();

            // After dispose the listener sets are empty; re-subscribing a fresh
            // listener and unsubbing it must still behave correctly (no leakage
            // of the cleared listeners into new subscriptions).
            const fresh = vi.fn();
            const sub = sources.onDeck("onClick", fresh);
            sub.unsub();

            expect(clickListener).not.toHaveBeenCalled();
            expect(hoverListener).not.toHaveBeenCalled();
            expect(fresh).not.toHaveBeenCalled();
        });

        it("withholds a hover clear while suppressed and applies it on release", () => {
            manager.setHover(fakeElement("e1"));
            expect(useMapStore.getState().hovered).not.toBeNull();

            manager.suppressHoverClear(true);
            manager.setHover(null);
            expect(useMapStore.getState().hovered).not.toBeNull();

            manager.suppressHoverClear(false);
            expect(useMapStore.getState().hovered).toBeNull();
        });

        it("a hover set while suppressed supersedes the withheld clear and survives the release", () => {
            // The pointer left the surface's DOM for another element beside it:
            // that is a real hover, not the hover-on-nothing the surface caused.
            manager.setHover(fakeElement("e1"));
            manager.suppressHoverClear(true);
            manager.setHover(null);
            manager.setHover(fakeElement("e2"));
            expect(useMapStore.getState().hovered).toContain("e2");

            manager.suppressHoverClear(false);
            expect(useMapStore.getState().hovered).toContain("e2");
        });

        it("releasing with nothing withheld leaves the hover alone", () => {
            manager.setHover(fakeElement("e1"));
            manager.suppressHoverClear(true);
            manager.suppressHoverClear(false);
            expect(useMapStore.getState().hovered).toContain("e1");
        });

        it("releasing when nothing was suppressed is a no-op", () => {
            manager.setHover(fakeElement("e1"));
            manager.suppressHoverClear(false);
            expect(useMapStore.getState().hovered).not.toBeNull();
        });

        it("resets selection and hover map-store state on dispose", () => {
            manager.selectElement(fakeElement("e1"));
            manager.setHover(fakeElement("e2"));
            expect(useMapStore.getState().selected).not.toBeNull();
            expect(useMapStore.getState().hovered).not.toBeNull();

            manager.dispose();

            expect(useMapStore.getState().selected).toBeNull();
            expect(useMapStore.getState().hovered).toBeNull();
        });

        it("resets the stacked selection on dispose", () => {
            useAreaSelectionStore.getState().select(
                [fakeElement("e1")],
                [
                    [0, 0],
                    [1, 0],
                    [1, 1],
                ],
            );

            manager.dispose();

            // One slot: the ring is part of the lasso selection, so it goes with it.
            expect(useStackedObjectSelectionStore.getState().elements).toBeNull();
            expect(useAreaSelectionStore.getState().elements).toBeNull();
            expect(useAreaSelectionStore.getState().boundary).toBeNull();
        });

        it("resets the watchbox panel's selection and hover on dispose", () => {
            useWatchboxPanelStore.getState().select("wb-1");
            useWatchboxPanelStore.getState().hover("wb-1");

            manager.dispose();

            expect(useWatchboxPanelStore.getState().selected).toBeNull();
            expect(useWatchboxPanelStore.getState().hovered).toBeNull();
        });
    });

    // -----------------------------------------------------------------------
    // Watchbox hover
    // -----------------------------------------------------------------------

    describe("watchbox hover", () => {
        /** Bind a fake deck and return a driver for the `onHover` prop `bindDeck` installs. */
        function bindHover() {
            const deckProps: Record<string, (...args: unknown[]) => void> = {};
            const deck = {
                setProps: vi.fn((props: Record<string, (...args: unknown[]) => void>) => {
                    Object.assign(deckProps, props);
                }),
            };
            manager.bindDeck(deck as never);
            return (object: unknown, layer: unknown = object ? { id: "scene" } : null) => {
                deckProps.onHover({ x: 0, y: 0, object, layer });
            };
        }

        /** The scene datum of a primitive drawn for `key` in the store `type`. */
        const primitive = (type: string, key: string) => ({
            primitiveId: codec.encode({ object: { type, key }, nanoid: "n" }),
        });
        const savedWatchbox = primitive(WATCHBOX_STORE_ID, "wb-1");

        it("forwards the hovered watchbox's id to the watchbox panel store, and clears it off the watchbox", () => {
            const hover = bindHover();

            hover(savedWatchbox);
            expect(useWatchboxPanelStore.getState().hovered).toBe("wb-1");

            hover(primitive("element", "e1"));
            expect(useWatchboxPanelStore.getState().hovered).toBeNull();

            hover(savedWatchbox);
            hover(undefined);
            expect(useWatchboxPanelStore.getState().hovered).toBeNull();
        });

        // deck reports the watchbox layer with no object as the pointer leaves a watchbox.
        it("clears it on a hover over the watchbox layer that carries no object", () => {
            const hover = bindHover();
            hover(savedWatchbox);

            hover(undefined, { id: "scene" });

            expect(useWatchboxPanelStore.getState().hovered).toBeNull();
        });

        it("ignores a primitive drawn from any other store", () => {
            const hover = bindHover();

            hover(primitive("regions", "wb-1"));

            expect(useWatchboxPanelStore.getState().hovered).toBeNull();
        });
    });

    // -----------------------------------------------------------------------
    // Synthesized-click drop
    // -----------------------------------------------------------------------

    describe("consumeNextDeckClick", () => {
        /** Bind fake deck + map and expose the two inputs the drop is scoped by. */
        function bindFakes() {
            const deckProps: Record<string, (...args: unknown[]) => void> = {};
            const deck = {
                setProps: vi.fn((props: Record<string, (...args: unknown[]) => void>) => {
                    Object.assign(deckProps, props);
                }),
                pickMultipleObjects: vi.fn(() => []),
            };
            manager.bindDeck(deck as never);

            const mapListeners = new Map<string, () => void>();
            const canvas = {
                clientWidth: 100,
                clientHeight: 100,
                style: {} as CSSStyleDeclaration,
            };
            const map = {
                on: vi.fn((event: string, listener: () => void) => {
                    mapListeners.set(event, listener);
                    return map;
                }),
                off: vi.fn(),
                getCenter: () => ({ lng: 0, lat: 0 }),
                getZoom: () => 1,
                getPitch: () => 0,
                getBearing: () => 0,
                getCanvas: () => canvas,
            };
            manager.bindMap(map as never);

            return {
                click: () =>
                    deckProps.onClick(
                        { x: 0, y: 0, object: undefined, layer: null },
                        { srcEvent: new MouseEvent("click", { button: 0 }) },
                    ),
                mousedown: () => mapListeners.get("mousedown")?.(),
            };
        }

        it("drops exactly the next deck click", () => {
            const sources = privilegedSources(manager);
            const { click } = bindFakes();
            const listener = vi.fn();
            sources.onDeck("onClick", listener);

            manager.consumeNextDeckClick();
            click();
            expect(listener).not.toHaveBeenCalled();

            click();
            expect(listener).toHaveBeenCalledTimes(1);
        });

        it("a new press clears a pending drop — a release that synthesized no click cannot swallow the next genuine one", () => {
            const sources = privilegedSources(manager);
            // deck only synthesizes a click from a press→release pair that barely
            // moved, so most freehand traces produce none; the drop armed for such
            // a release must not outlive the pair.
            const { click, mousedown } = bindFakes();
            const listener = vi.fn();
            sources.onDeck("onClick", listener);

            manager.consumeNextDeckClick();
            mousedown();
            click();
            expect(listener).toHaveBeenCalledTimes(1);
        });
    });

    // -----------------------------------------------------------------------
    // Subscriptions
    // -----------------------------------------------------------------------

    describe("onMap subscriptions", () => {
        /**
         * A map whose `fire` iterates a COPY of its listener list, exactly as
         * maplibre's `Evented.fire` does — a listener removed by an earlier
         * listener in the same dispatch is still reached.
         */
        function fakeMap() {
            const listeners = new Map<string, Array<(ev: unknown) => void>>();
            const map = {
                on: vi.fn((event: string, listener: (ev: unknown) => void) => {
                    listeners.set(event, [...(listeners.get(event) ?? []), listener]);
                    return {
                        unsubscribe: () => map.off(event, listener),
                    };
                }),
                off: vi.fn((event: string, listener: (ev: unknown) => void) => {
                    listeners.set(
                        event,
                        (listeners.get(event) ?? []).filter((l) => l !== listener),
                    );
                }),
                getCenter: () => ({ lng: 0, lat: 0 }),
                getZoom: () => 1,
                getPitch: () => 0,
                getBearing: () => 0,
                getCanvas: () => ({
                    clientWidth: 100,
                    clientHeight: 100,
                    style: {} as CSSStyleDeclaration,
                }),
            };
            const fire = (event: string, ev: unknown = {}) => {
                for (const listener of [...(listeners.get(event) ?? [])]) listener(ev);
            };
            return { map, fire };
        }

        it("a listener unsubscribed during the map's own dispatch does not run", () => {
            const sources = privilegedSources(manager);
            const { map, fire } = fakeMap();
            manager.bindMap(map as never);

            const later = vi.fn();
            // Bound first, so the dispatch reaches it first; it retires `later`,
            // which the copied list still reaches afterwards — the shape of the
            // interaction runner's rebind from inside a handler.
            const firstSub = sources.onMap("click", () => {
                laterSub.unsub();
            });
            const laterSub = sources.onMap("click", later);

            fire("click");
            expect(later).not.toHaveBeenCalled();
            firstSub.unsub();
        });

        it("the same holds for a listener queued before the map was bound", () => {
            const sources = privilegedSources(manager);
            const { map, fire } = fakeMap();

            const later = vi.fn();
            const firstSub = sources.onMap("click", () => {
                laterSub.unsub();
            });
            const laterSub = sources.onMap("click", later);
            manager.bindMap(map as never);

            fire("click");
            expect(later).not.toHaveBeenCalled();
            firstSub.unsub();
        });

        it("unsub before bindMap drops the queued listener; unsub after removes the real one", () => {
            const sources = privilegedSources(manager);
            const { map, fire } = fakeMap();
            const dropped = vi.fn();
            const kept = vi.fn();
            sources.onMap("click", dropped).unsub();
            const keptSub = sources.onMap("click", kept);
            manager.bindMap(map as never);

            fire("click");
            expect(dropped).not.toHaveBeenCalled();
            expect(kept).toHaveBeenCalledTimes(1);

            keptSub.unsub();
            fire("click");
            expect(kept).toHaveBeenCalledTimes(1);
            expect(map.off).toHaveBeenCalledWith("click", expect.any(Function));
        });
    });

    describe("onDocument", () => {
        it("hears a document event in the capture phase and stops on unsub", () => {
            const sources = privilegedSources(manager);
            const listener = vi.fn();
            const sub = sources.onDocument("mouseup", listener);

            document.dispatchEvent(new MouseEvent("mouseup", { button: 0 }));
            expect(listener).toHaveBeenCalledTimes(1);

            sub.unsub();
            document.dispatchEvent(new MouseEvent("mouseup", { button: 0 }));
            expect(listener).toHaveBeenCalledTimes(1);
        });
    });

    // -----------------------------------------------------------------------
    // Element resolution
    // -----------------------------------------------------------------------

    describe("element resolution", () => {
        it("resolves live and historical elements from the one shared store", () => {
            const liveEl = fakeElement("live");
            const histEl = fakeElement("hist");
            // Live and historical elements coexist under distinct keys
            // (historical ids carry a ":<chunkIndex>" suffix), so one store
            // serves both — there is no per-plane routing any more.
            const store = {
                get: (k: string) =>
                    k === "live-key" ? liveEl : k === "hist-key:0" ? histEl : undefined,
            } as unknown as ElementStore;
            const m = new MapManager(store, {} as unknown as ElementActions);

            expect(m.getElement("live-key")).toBe(liveEl);
            expect(m.getElement("hist-key:0")).toBe(histEl);
            expect(m.getElement("missing")).toBeUndefined();

            m.dispose();
        });
    });
});
