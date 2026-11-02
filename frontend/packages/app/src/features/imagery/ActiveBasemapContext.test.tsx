// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, userEvent } from "~/test";
import {
    ActiveBasemapProvider,
    useActiveBasemapTileId,
    useSetActiveBasemap,
} from "./ActiveBasemapContext";

/** Shows the active tile and, on click, does what the picker does after a switch. */
function Probe() {
    const tileId = useActiveBasemapTileId();
    const setActiveBasemap = useSetActiveBasemap();
    return (
        <button type="button" onClick={() => setActiveBasemap("picked")}>
            {tileId}
        </button>
    );
}

function mounted(resolvedTileId: string | null) {
    return (
        <ActiveBasemapProvider resolvedTileId={resolvedTileId}>
            <Probe />
        </ActiveBasemapProvider>
    );
}

describe("ActiveBasemapProvider", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    it("opens on the resolved tile and follows it when the selection re-resolves", () => {
        const { rerender } = render(mounted("first"));
        expect(screen.getByRole("button")).toHaveTextContent("first");

        rerender(mounted("primary"));
        expect(screen.getByRole("button")).toHaveTextContent("primary");

        rerender(mounted(null));
        expect(screen.getByRole("button").textContent).toBe("");
    });

    it("keeps the picker's tile across re-renders that leave the resolved tile unchanged", async () => {
        const { rerender } = render(mounted("first"));
        await userEvent.click(screen.getByRole("button"));
        expect(screen.getByRole("button")).toHaveTextContent("picked");

        rerender(mounted("first"));
        expect(screen.getByRole("button")).toHaveTextContent("picked");
    });

    it("refuses a consumer outside the map tree", () => {
        vi.spyOn(console, "error").mockImplementation(() => {});
        expect(() => render(<Probe />)).toThrow("ActiveBasemapProvider");
    });
});
