// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it, vi } from "vitest";
import { act, render, screen, userEvent } from "~/test";
import { resetInteractionForTest, setSessionForTest } from "~/test/interaction";
import type { DrawSession } from "~/features/map/interaction-runtime/session/session";
import { useHistoricalStore } from "~/features/historical/data-plane/historical.store";
import { WATCHBOX_TOOL_ID } from "./watchbox-session";
import { WatchboxCreateButton } from "./WatchboxCreateButton";

const { mockActivate, mockExit } = vi.hoisted(() => ({
    mockActivate: vi.fn(),
    mockExit: vi.fn(),
}));

vi.mock("~/features/map/interaction-runtime/session/commands", () => ({
    activate: (...args: unknown[]) => mockActivate(...args),
    exit: (...args: unknown[]) => mockExit(...args),
}));

/** The polygon being drawn — waiting for its first click. */
function drawing(descriptorId: string): DrawSession {
    return {
        kind: "draw",
        descriptorId,
        epoch: 1,
        params: {},
        custody: false,
        locked: 0,
        draft: null,
    };
}

afterEach(() => {
    act(() => resetInteractionForTest());
    act(() => useHistoricalStore.setState({ mode: "live" }));
    vi.clearAllMocks();
});

describe("WatchboxCreateButton", () => {
    it("activates the watchbox tool by id when idle", async () => {
        const user = userEvent.setup();
        render(<WatchboxCreateButton />);

        await user.click(screen.getByRole("button", { name: "Create watchbox" }));

        expect(mockActivate).toHaveBeenCalledWith(WATCHBOX_TOOL_ID);
        expect(mockExit).not.toHaveBeenCalled();
    });

    it("mid-draw relabels to Cancel drawing and exits instead of re-activating", async () => {
        const user = userEvent.setup();
        act(() => setSessionForTest(drawing(WATCHBOX_TOOL_ID)));
        render(<WatchboxCreateButton />);

        const button = screen.getByRole("button", { name: "Cancel drawing" });
        expect(button).toBeEnabled();
        await user.click(button);

        expect(mockExit).toHaveBeenCalledOnce();
        expect(mockActivate).not.toHaveBeenCalled();
    });

    it("stays enabled mid-draw even while another draft would normally disable the toolbar's shared click handler", () => {
        // The toolbar disables a tool's own button once its draft is in flight
        // (switching tools must not abandon one). This is the dedicated cancel
        // affordance for watchbox's own draft, so it is exempt — see the
        // component's doc comment.
        act(() => setSessionForTest({ ...drawing(WATCHBOX_TOOL_ID), locked: 1 }));
        render(<WatchboxCreateButton />);

        expect(screen.getByRole("button", { name: "Cancel drawing" })).toBeEnabled();
    });

    it("is disabled in historical mode and keeps its idle name", () => {
        act(() => useHistoricalStore.setState({ mode: "historical" }));
        render(<WatchboxCreateButton />);

        const button = screen.getByRole("button", { name: "Create watchbox" });
        expect(button).toBeDisabled();
    });

    it("does nothing when clicked while disabled", async () => {
        const user = userEvent.setup();
        act(() => useHistoricalStore.setState({ mode: "historical" }));
        render(<WatchboxCreateButton />);

        await user.click(screen.getByRole("button", { name: "Create watchbox" }));

        expect(mockActivate).not.toHaveBeenCalled();
        expect(mockExit).not.toHaveBeenCalled();
    });
});
