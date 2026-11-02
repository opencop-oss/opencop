// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/** The hover-policy applier: suppression follows pointer ownership, and its release is automatic. See `~/test/interaction-runtime-harness` for the fixtures. */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useMapStore } from "~/features/map/core/map.store";
import { useStackedObjectSelectionStore } from "~/features/map/stacked-object-selection.store";
import { attachStackedObjectSelectionSurface } from "~/features/map/stacked-object-selection.store";
import { useWatchboxPanelStore } from "~/features/watchbox/watchbox-panel.store";
import { Point } from "@opencop/elements";
import { openSurface, withdrawSurface } from "./surface";
import {
    attachRuntime,
    fakeDeps,
    installInteractionRuntimeHarness,
} from "~/test/interaction-runtime-harness";

installInteractionRuntimeHarness();

beforeEach(() => {
    useMapStore.getState().resetHover();
    useWatchboxPanelStore.setState(useWatchboxPanelStore.getInitialState(), true);
});

afterEach(() => {
    withdrawSurface();
    useStackedObjectSelectionStore.getState().dismiss();
});

describe("cursor and gesture locks follow the kind of hovered object", () => {
    it("uses a pointer over a saved watchbox without changing double-click zoom", () => {
        const deps = fakeDeps();
        const detach = attachRuntime(deps);
        vi.mocked(deps.mapManager.setCursor).mockClear();

        useWatchboxPanelStore.getState().hover("wb-1");

        expect(deps.mapManager.setCursor).toHaveBeenLastCalledWith("pointer");
        expect(deps.mapManager.disableDoubleClickZoom).not.toHaveBeenCalled();

        useWatchboxPanelStore.getState().hover(null);

        expect(deps.mapManager.setCursor).toHaveBeenLastCalledWith("default");
        expect(deps.mapManager.enableDoubleClickZoom).not.toHaveBeenCalled();

        detach();
    });

    it("still disables double-click zoom while an element is hovered", () => {
        const deps = fakeDeps();
        const detach = attachRuntime(deps);

        useMapStore.getState().hover("source:element");

        expect(deps.mapManager.setCursor).toHaveBeenLastCalledWith("pointer");
        expect(deps.mapManager.disableDoubleClickZoom).toHaveBeenCalledOnce();

        useMapStore.getState().resetHover();

        expect(deps.mapManager.setCursor).toHaveBeenLastCalledWith("default");
        expect(deps.mapManager.enableDoubleClickZoom).toHaveBeenCalledOnce();

        detach();
    });
});

describe("hover clearing follows who owns the pointer", () => {
    it("a surface owning the pointer withholds hover clears; ownership moving on releases them", () => {
        const deps = fakeDeps();
        const detach = attachRuntime(deps);
        expect(deps.mapManager.suppressHoverClear).not.toHaveBeenCalled();

        const release = openSurface({ id: "map-context-menu", close: vi.fn() });
        expect(deps.mapManager.suppressHoverClear).toHaveBeenLastCalledWith(true);

        release();
        expect(deps.mapManager.suppressHoverClear).toHaveBeenLastCalledWith(false);
        expect(deps.mapManager.suppressHoverClear).toHaveBeenCalledTimes(2);

        detach();
    });

    it("closing the popover releases the hover suppression it imposed — by no code of its own", () => {
        const deps = fakeDeps();
        const detach = attachRuntime(deps);
        const detachSync = attachStackedObjectSelectionSurface();

        useStackedObjectSelectionStore
            .getState()
            .open([Point.create({ position: [0, 0] })], { x: 1, y: 2 }, false);
        expect(deps.mapManager.suppressHoverClear).toHaveBeenLastCalledWith(true);

        useStackedObjectSelectionStore.getState().dismiss();
        expect(deps.mapManager.suppressHoverClear).toHaveBeenLastCalledWith(false);

        detachSync();
        detach();
    });

    it("detaching while a surface is open leaves hover clearing free", () => {
        const deps = fakeDeps();
        const detach = attachRuntime(deps);
        openSurface({ id: "map-context-menu", close: vi.fn() });
        expect(deps.mapManager.suppressHoverClear).toHaveBeenLastCalledWith(true);

        detach();
        expect(deps.mapManager.suppressHoverClear).toHaveBeenLastCalledWith(false);
    });
});
