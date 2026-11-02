// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen, userEvent, within } from "~/test";
import { MapAttribution } from "./MapAttribution";

// Raw credits as the map supplies them, parsed the way the real hook does.
let mockCredits: string[] = [];
vi.mock("./use-map-attributions.hook", async () => {
    const { dedupeAttributions } = await import("./map-attribution.util");
    return { useMapAttributions: () => dedupeAttributions(mockCredits) };
});

describe("MapAttribution", () => {
    beforeEach(() => {
        mockCredits = [
            '<a href="https://tiles.example.org">Example Tiles</a> Data from <a href="https://data.example.org/copyright">Example Data</a>',
            "© Example imagery",
        ];
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it("renders nothing when no source carries attribution", () => {
        mockCredits = [];
        render(<MapAttribution />);
        expect(screen.queryByTestId("map-attribution-text")).not.toBeInTheDocument();
        expect(screen.queryByRole("button")).not.toBeInTheDocument();
    });

    it("shows every credit with its links opening safely in a new tab", () => {
        render(<MapAttribution />);

        expect(screen.getByTestId("map-attribution-text")).toHaveTextContent(
            "Example Tiles Data from Example Data | © Example imagery",
        );
        const link = screen.getByRole("link", { name: "Example Data" });
        expect(link).toHaveAttribute("href", "https://data.example.org/copyright");
        expect(link).toHaveAttribute("target", "_blank");
        expect(link).toHaveAttribute("rel", "noopener noreferrer");
    });

    it("keeps the inline links out of the tab order and the popover's links in it", async () => {
        const user = userEvent.setup();
        render(<MapAttribution />);

        const inline = within(screen.getByTestId("map-attribution-text")).getAllByRole("link");
        expect(inline).toHaveLength(2);
        for (const link of inline) expect(link).toHaveAttribute("tabindex", "-1");

        await user.click(screen.getByRole("button", { name: "Show map credits" }));
        const popover = await screen.findByRole("dialog", { name: "Map credits" });
        const full = within(popover).getAllByRole("link");
        expect(full).toHaveLength(2);
        for (const link of full) expect(link).not.toHaveAttribute("tabindex");
    });

    it("renders a hostile credit as text and links only, keeping none of its markup", () => {
        mockCredits = [
            '<details open ontoggle="alert(1)">© Hostile</details><img src="x" onerror="alert(1)">' +
                '<a href="https://example.com/" onclick="alert(1)" target="_self" style="position:fixed" ping="https://example.org/">Safe</a>' +
                '<a href="javascript:alert(1)">Unsafe</a>',
        ];
        render(<MapAttribution />);

        const inline = screen.getByTestId("map-attribution-text");
        expect(inline).toHaveTextContent("© Hostile SafeUnsafe");
        expect(inline.querySelectorAll("details, img, script, iframe, svg, style")).toHaveLength(0);
        const [link, ...others] = within(inline).getAllByRole("link");
        expect(others).toHaveLength(0);
        expect(link).toHaveAccessibleName("Safe");
        expect(link).toHaveAttribute("href", "https://example.com/");
        expect(link).toHaveAttribute("target", "_blank");
        expect(link).toHaveAttribute("rel", "noopener noreferrer");
        for (const name of ["onclick", "style", "ping"]) expect(link).not.toHaveAttribute(name);
    });

    it("keeps the credits inline over time, and the info button opens every credit in full", async () => {
        vi.useFakeTimers({ shouldAdvanceTime: true });
        const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
        render(<MapAttribution />);

        act(() => vi.advanceTimersByTime(60_000));
        expect(screen.getByTestId("map-attribution-text")).toHaveTextContent("© Example imagery");

        await user.click(screen.getByRole("button", { name: "Show map credits" }));
        const popover = await screen.findByRole("dialog", { name: "Map credits" });
        // Named explicitly, not through the title the popover may not have detected.
        expect(popover).toHaveAttribute("aria-label", "Map credits");
        // Bounded and scrollable, so a long credit list stays reachable.
        expect(getComputedStyle(popover).overflowY).toBe("auto");
        expect(popover).toHaveTextContent("Example Tiles Data from Example Data");
        expect(popover).toHaveTextContent("© Example imagery");
        expect(within(popover).getByRole("link", { name: "Example Tiles" })).toHaveAttribute(
            "href",
            "https://tiles.example.org/",
        );
    });

    it("keeps an open credits popover open, with the new credits, when the credits change", async () => {
        const user = userEvent.setup();
        const { rerender } = render(<MapAttribution />);

        await user.click(screen.getByRole("button", { name: "Show map credits" }));
        expect(await screen.findByRole("dialog", { name: "Map credits" })).toHaveTextContent(
            "© Example imagery",
        );

        mockCredits = ["© Terrain source"];
        rerender(<MapAttribution />);

        const dialog = screen.getByRole("dialog", { name: "Map credits" });
        expect(dialog).toHaveTextContent("© Terrain source");
        expect(dialog).not.toHaveTextContent("© Example imagery");
        expect(screen.getByTestId("map-attribution-text")).toHaveTextContent("© Terrain source");
    });
});
