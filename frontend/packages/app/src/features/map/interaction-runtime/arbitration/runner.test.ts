// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/** The runner: the owner's set is what is bound, and nothing else is. See `~/test/interaction-runtime-harness` for the fixtures. */
import { afterEach, describe, expect, it, vi } from "vitest";
import { Line, Point, Polygon } from "@opencop/elements";
import { registerTool } from "~/features/contributions/tool-registry";
import { useStackedObjectSelectionStore } from "~/features/map/stacked-object-selection.store";
import { useAreaSelectionStore } from "~/features/selection/area-selection.store";
import { getInteractionState } from "../session/interaction.store";
import { activate, exit } from "../session/commands";
import { currentPointerOwner } from "./pointer-owners";
import { registerPickResponse } from "./pick-responses";
import { openSurface, registerContextMenuOpener, withdrawSurface } from "./surface";
import {
    attachRuntime,
    deckClick,
    fakeDeps,
    lineFixture,
    mapMouse,
    installInteractionRuntimeHarness,
} from "~/test/interaction-runtime-harness";

installInteractionRuntimeHarness();

afterEach(() => {
    withdrawSurface();
});

describe("binding identity", () => {
    it("waiting and in flight share one binding; completing into an edit rebinds", async () => {
        const { bindingKey } = await import("../session/session");
        const waiting = {
            kind: "draw" as const,
            descriptorId: "x",
            epoch: 7,
            params: {},
            custody: false,
            draft: null,
            locked: 0,
        };
        const armed = bindingKey(waiting);
        const engaged = bindingKey({
            ...waiting,
            draft: { object: Polygon.create({ positions: [0, 0] }), placed: true },
            locked: 1,
        });
        // A trace must not rebind mid-press: the mousedown that places the
        // ring would otherwise dispose the very binder carrying it.
        expect(engaged).toBe(armed);
        // The finished product is a different session kind, so the edit
        // interpreter binds in place of the draw grammar.
        const element = Line.create({ positions: [0, 0, 1, 1] });
        const finished = bindingKey({
            kind: "edit",
            descriptorId: "x",
            epoch: 8,
            product: element,
            custody: false,
            params: {},
            isNew: true,
            sub: { hotspot: null, dragging: null },
        });
        expect(finished).not.toBe(armed);
    });
});

describe("ambient owns the pointer when nothing else does", () => {
    it("a primary click selects the picked element, or clears when nothing is under it", () => {
        const deps = fakeDeps();
        const detach = attachRuntime(deps);
        expect(currentPointerOwner()).toBe("ambient");

        const el = Point.create({ position: [0, 0] });
        deps.fireDeck("onClick", deckClick([el]), el);
        expect(deps.mapManager.selectElement).toHaveBeenCalledWith(el);

        deps.fireDeck("onClick", deckClick([]), undefined);
        expect(deps.mapManager.handleEmptyClick).toHaveBeenCalledTimes(1);

        detach();
    });

    it("a right-button click never clears the selection", () => {
        const deps = fakeDeps();
        const detach = attachRuntime(deps);

        deps.fireDeck("onClick", deckClick([], 2), undefined);
        expect(deps.mapManager.handleEmptyClick).not.toHaveBeenCalled();

        detach();
    });

    it("several distinct picks under one click open the point-sourced stack", () => {
        const deps = fakeDeps();
        const detach = attachRuntime(deps);

        const a = Point.create({ position: [0, 0] });
        const b = Point.create({ position: [1, 1] });
        deps.fireDeck("onClick", deckClick([a, b]), a);

        expect(useStackedObjectSelectionStore.getState()).toMatchObject({
            elements: [a, b],
            position: { x: 10, y: 20 },
            hitDepthLimit: false,
            currentDepth: 20,
        });
        expect(deps.mapManager.selectElement).not.toHaveBeenCalled();

        useStackedObjectSelectionStore.getState().dismiss();
        useAreaSelectionStore.getState().clear();
        detach();
    });

    it("a session owns the pointer: the ambient binding is gone while it runs and back after", () => {
        const deps = fakeDeps();
        const detach = attachRuntime(deps);
        registerTool(lineFixture);

        expect(activate("fixtureLine")).toEqual({ ok: true });
        expect(currentPointerOwner()).toBe("session");
        const el = Point.create({ position: [0, 0] });
        deps.fireDeck("onClick", deckClick([el]), el);
        expect(deps.mapManager.selectElement).not.toHaveBeenCalled();

        expect(exit()).toEqual({ ok: true });
        expect(getInteractionState().session.kind).toBe("idle");
        expect(currentPointerOwner()).toBe("ambient");
        deps.fireDeck("onClick", deckClick([el]), el);
        expect(deps.mapManager.selectElement).toHaveBeenCalledWith(el);

        detach();
    });
});

describe("ambient dispatches a click by what was under the pointer", () => {
    const regions = () => ({ onClick: vi.fn() });
    const region = { key: "r1", type: "region" };

    it("a click on a store's object whose store has a response, with nothing beneath, is that response's, and nobody else acts on it", () => {
        const deps = fakeDeps();
        const detach = attachRuntime(deps);
        const response = regions();
        const unregister = registerPickResponse("regions", response);

        deps.fireDeck(
            "onClick",
            deckClick([], 0, {
                layer: "scene",
                object: {},
                ref: { type: "regions", key: region.key },
            }),
            undefined,
        );

        expect(response.onClick).toHaveBeenCalledTimes(1);
        expect(response.onClick.mock.calls[0][0]).toMatchObject({
            hit: { responseId: "regions", ref: { type: "regions", key: region.key } },
            elements: [],
        });
        expect(deps.mapManager.handleEmptyClick).not.toHaveBeenCalled();
        expect(deps.mapManager.selectElement).not.toHaveBeenCalled();

        unregister();
        detach();
    });

    it("elements first: an element under the pointer is selected even with a store's object drawn over it", () => {
        const deps = fakeDeps();
        const detach = attachRuntime(deps);
        const response = regions();
        const unregister = registerPickResponse("regions", response);
        const el = Point.create({ position: [0, 0] });

        // The nearest hit is the region; the element is beneath it in the stack.
        deps.fireDeck(
            "onClick",
            deckClick([el], 0, {
                layer: "scene",
                object: {},
                ref: { type: "regions", key: region.key },
            }),
            undefined,
        );

        expect(response.onClick).not.toHaveBeenCalled();
        expect(deps.mapManager.selectElement).toHaveBeenCalledWith(el);

        unregister();
        detach();
    });

    it("a hit outside any store is empty ground: the selection clears as before", () => {
        const deps = fakeDeps();
        const detach = attachRuntime(deps);

        deps.fireDeck(
            "onClick",
            deckClick([], 0, { layer: "undeclared", object: { id: "u1" } }),
            undefined,
        );

        expect(deps.mapManager.handleEmptyClick).toHaveBeenCalledTimes(1);
        detach();
    });
});

describe("an open surface owns the pointer", () => {
    it("a primary click closes it and picks nothing; a right-button click leaves it open", () => {
        const deps = fakeDeps();
        const detach = attachRuntime(deps);
        const close = vi.fn();
        openSurface({ id: "map-context-menu", close });
        expect(currentPointerOwner()).toBe("surface");

        // On macOS the right button's click arrives after the contextmenu that
        // opened the menu; it must not flash the menu shut.
        deps.fireDeck("onClick", deckClick([], 2), undefined);
        expect(close).not.toHaveBeenCalled();
        expect(currentPointerOwner()).toBe("surface");

        const el = Point.create({ position: [0, 0] });
        deps.fireDeck("onClick", deckClick([el]), el);
        expect(close).toHaveBeenCalledTimes(1);
        // The surface's set owned that click: ambient did not also act on it.
        expect(deps.mapManager.selectElement).not.toHaveBeenCalled();
        expect(currentPointerOwner()).toBe("ambient");

        detach();
    });

    it("a session outranks it: activating over an open surface binds the session's set", () => {
        const deps = fakeDeps();
        const detach = attachRuntime(deps);
        registerTool(lineFixture);
        openSurface({ id: "stacked-object-selection", close: vi.fn() });

        expect(activate("fixtureLine")).toEqual({ ok: true });
        // The composition point closes the surface on activation, so the
        // session wins on both counts.
        expect(currentPointerOwner()).toBe("session");

        detach();
    });
});

describe("the context menu's trigger is ambient's", () => {
    it("a right-click when idle reaches the registered opener and suppresses the browser's menu", () => {
        const deps = fakeDeps();
        const detach = attachRuntime(deps);
        const open = vi.fn();
        const unregister = registerContextMenuOpener(open);

        const ev = mapMouse([10, 20], { x: 30, y: 40 }, 2);
        deps.fire("contextmenu", ev);

        expect(open).toHaveBeenCalledWith({
            screen: { x: 30, y: 40 },
            client: { x: 30, y: 40 },
            lngLat: { lng: 10, lat: 20 },
        });
        expect(ev.preventDefault).toHaveBeenCalled();

        unregister();
        detach();
    });

    it("with no opener registered the browser keeps its own menu", () => {
        const deps = fakeDeps();
        const detach = attachRuntime(deps);

        const ev = mapMouse([10, 20], { x: 30, y: 40 }, 2);
        deps.fire("contextmenu", ev);

        expect(ev.preventDefault).not.toHaveBeenCalled();
        detach();
    });

    it("a right-click during a draw is the draw's — the trigger is not bound, so no gate is needed", () => {
        const deps = fakeDeps();
        const detach = attachRuntime(deps);
        const open = vi.fn();
        const unregister = registerContextMenuOpener(open);
        registerTool(lineFixture);
        expect(activate("fixtureLine")).toEqual({ ok: true });

        deps.fire("contextmenu", mapMouse([10, 20], { x: 30, y: 40 }, 2));

        expect(open).not.toHaveBeenCalled();
        expect(getInteractionState().session.kind).toBe("draw");

        unregister();
        detach();
    });
});
