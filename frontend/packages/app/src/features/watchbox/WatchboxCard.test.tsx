// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import { act, render, screen, userEvent } from "~/test";
import { i18n } from "~/i18n/config";
import type { Watchbox, WatchboxAlertingStatus } from "./watchbox.model";
import { WatchboxCard } from "./WatchboxCard";

const POLYGON = {
    type: "Polygon" as const,
    coordinates: [
        [
            [0, 0],
            [1, 0],
            [1, 1],
            [0, 0],
        ],
    ],
};

function makeWatchbox(overrides: Partial<Watchbox> = {}): Watchbox {
    return {
        id: "wb-1",
        name: "My Watchbox",
        description: null,
        enabled: true,
        direction: "BOTH",
        polygon: POLYGON,
        filter: { combine: "ALL", predicates: [] },
        layerIds: ["layer-1"],
        configEpoch: 1,
        alertingStatus: "ACTIVE",
        alertingReasons: [],
        sourceStatuses: [],
        capabilities: { canDelete: true, canPatch: true },
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:00Z",
        ...overrides,
    };
}

function renderCard(overrides: Partial<Watchbox> = {}) {
    render(
        <WatchboxCard
            watchbox={makeWatchbox(overrides)}
            onDeleteClick={vi.fn()}
            onToggleEnabled={vi.fn()}
            isMuted={false}
            onToggleMuted={vi.fn()}
            open={vi.fn()}
        />,
    );
}

function statusText(status: WatchboxAlertingStatus) {
    return { ACTIVE: "Active", DEGRADED: "Degraded", FAILED: "Failed", DISABLED: "Disabled" }[
        status
    ];
}

describe("WatchboxCard — toggle focus", () => {
    it("restores switch focus when its pending toggle settles", () => {
        const props = {
            watchbox: makeWatchbox(),
            onDeleteClick: vi.fn(),
            onToggleEnabled: vi.fn(),
            isMuted: false,
            onToggleMuted: vi.fn(),
            open: vi.fn(),
        };
        const { rerender } = render(<WatchboxCard {...props} />);
        const toggle = screen.getByRole("switch");
        act(() => toggle.focus());

        // jsdom does not reproduce Chromium's automatic blur on disable, so model that blur
        // immediately before applying the pending state.
        act(() => toggle.blur());
        expect(document.body).toHaveFocus();
        rerender(<WatchboxCard {...props} togglePending />);

        rerender(<WatchboxCard {...props} togglePending={false} />);

        expect(toggle).toHaveFocus();
    });
});

describe("WatchboxCard — status label", () => {
    it("shows the switch and no status label when active and canPatch", () => {
        renderCard({
            alertingStatus: "ACTIVE",
            enabled: true,
            capabilities: { canDelete: false, canPatch: true },
        });

        expect(screen.getByRole("switch")).toBeInTheDocument();
        expect(screen.queryByText(statusText("ACTIVE"))).not.toBeInTheDocument();
    });

    it("shows an 'Active' label when active and not canPatch", () => {
        renderCard({
            alertingStatus: "ACTIVE",
            capabilities: { canDelete: false, canPatch: false },
        });

        expect(screen.queryByRole("switch")).not.toBeInTheDocument();
        expect(screen.getByText(statusText("ACTIVE"))).toBeInTheDocument();
    });

    it("shows the switch (off) and no status label when disabled and canPatch", () => {
        renderCard({
            alertingStatus: "DISABLED",
            enabled: false,
            capabilities: { canDelete: false, canPatch: true },
        });

        expect(screen.getByRole("switch")).not.toBeChecked();
        expect(screen.queryByText(statusText("DISABLED"))).not.toBeInTheDocument();
    });

    it("shows a muted 'Disabled' label when disabled and not canPatch (nothing else conveys it)", () => {
        renderCard({
            alertingStatus: "DISABLED",
            enabled: false,
            capabilities: { canDelete: false, canPatch: false },
        });

        expect(screen.queryByRole("switch")).not.toBeInTheDocument();
        const label = screen.getByText(statusText("DISABLED"));
        expect(label).toBeInTheDocument();
    });

    it("shows a 'Degraded' label alongside the switch, since the switch can't express health state", () => {
        renderCard({
            alertingStatus: "DEGRADED",
            enabled: true,
            capabilities: { canDelete: false, canPatch: true },
        });

        expect(screen.getByRole("switch")).toBeInTheDocument();
        expect(screen.getByText(statusText("DEGRADED"))).toBeInTheDocument();
    });

    it("shows a 'Failed' label when not canPatch", () => {
        renderCard({
            alertingStatus: "FAILED",
            enabled: true,
            capabilities: { canDelete: false, canPatch: false },
        });

        expect(screen.queryByRole("switch")).not.toBeInTheDocument();
        expect(screen.getByText(statusText("FAILED"))).toBeInTheDocument();
    });
});

describe("WatchboxCard — muted indicator", () => {
    it("shows a 'Muted' indicator when isMuted", () => {
        render(
            <WatchboxCard
                watchbox={makeWatchbox()}
                onDeleteClick={vi.fn()}
                onToggleEnabled={vi.fn()}
                isMuted
                onToggleMuted={vi.fn()}
                open={vi.fn()}
            />,
        );

        expect(screen.getByText("Muted")).toBeInTheDocument();
    });

    it("shows no 'Muted' indicator when not muted", () => {
        renderCard();

        expect(screen.queryByText("Muted")).not.toBeInTheDocument();
    });

    it("leads the description rather than replacing it", () => {
        render(
            <WatchboxCard
                watchbox={makeWatchbox({ description: "Watching the north gate" })}
                onDeleteClick={vi.fn()}
                onToggleEnabled={vi.fn()}
                isMuted
                onToggleMuted={vi.fn()}
                open={vi.fn()}
            />,
        );

        expect(screen.getByText(/Muted/)).toBeInTheDocument();
        expect(screen.getByText("Watching the north gate")).toBeInTheDocument();
    });

    it("shows 'Muted' alongside the 'Degraded' status label — independent of alertingStatus", () => {
        render(
            <WatchboxCard
                watchbox={makeWatchbox({ alertingStatus: "DEGRADED" })}
                onDeleteClick={vi.fn()}
                onToggleEnabled={vi.fn()}
                isMuted
                onToggleMuted={vi.fn()}
                open={vi.fn()}
            />,
        );

        expect(screen.getByText(statusText("DEGRADED"))).toBeInTheDocument();
        expect(screen.getByText("Muted")).toBeInTheDocument();
    });
});

describe("WatchboxCard — actions menu", () => {
    function menuTrigger() {
        return screen.getByRole("button", {
            name: i18n.t("watchbox.moreActions", { name: "My Watchbox" }),
        });
    }

    async function openMenu() {
        await userEvent.click(menuTrigger());
    }

    function muteMenuItem() {
        return screen.findByRole("menuitem", { name: i18n.t("watchbox.mute") });
    }

    function unmuteMenuItem() {
        return screen.findByRole("menuitem", { name: i18n.t("watchbox.unmute") });
    }

    it("offers 'Mute alerts' when not muted", async () => {
        renderCard();
        await openMenu();

        expect(await muteMenuItem()).toBeInTheDocument();
    });

    it("offers 'Unmute alerts' when muted", async () => {
        render(
            <WatchboxCard
                watchbox={makeWatchbox()}
                onDeleteClick={vi.fn()}
                onToggleEnabled={vi.fn()}
                isMuted
                onToggleMuted={vi.fn()}
                open={vi.fn()}
            />,
        );
        await openMenu();

        expect(await unmuteMenuItem()).toBeInTheDocument();
    });

    it("calls onToggleMuted(true) when 'Mute alerts' is chosen, without opening the card", async () => {
        const onToggleMuted = vi.fn();
        const open = vi.fn();
        render(
            <WatchboxCard
                watchbox={makeWatchbox()}
                onDeleteClick={vi.fn()}
                onToggleEnabled={vi.fn()}
                isMuted={false}
                onToggleMuted={onToggleMuted}
                open={open}
            />,
        );
        await openMenu();
        await userEvent.click(await muteMenuItem());

        expect(onToggleMuted).toHaveBeenCalledWith(true);
        expect(open).not.toHaveBeenCalled();
    });

    it("calls onToggleMuted(false) when 'Unmute alerts' is chosen", async () => {
        const onToggleMuted = vi.fn();
        render(
            <WatchboxCard
                watchbox={makeWatchbox()}
                onDeleteClick={vi.fn()}
                onToggleEnabled={vi.fn()}
                isMuted
                onToggleMuted={onToggleMuted}
                open={vi.fn()}
            />,
        );
        await openMenu();
        await userEvent.click(await unmuteMenuItem());

        expect(onToggleMuted).toHaveBeenCalledWith(false);
    });

    it("calls onDeleteClick when 'Delete watchbox' is chosen, without opening the card", async () => {
        const onDeleteClick = vi.fn();
        const open = vi.fn();
        render(
            <WatchboxCard
                watchbox={makeWatchbox()}
                onDeleteClick={onDeleteClick}
                onToggleEnabled={vi.fn()}
                isMuted={false}
                onToggleMuted={vi.fn()}
                open={open}
            />,
        );
        await openMenu();
        await userEvent.click(
            await screen.findByRole("menuitem", { name: i18n.t("watchbox.delete") }),
        );

        expect(onDeleteClick).toHaveBeenCalled();
        expect(open).not.toHaveBeenCalled();
    });

    it("hides the Delete item but keeps Mute when canDelete is false", async () => {
        renderCard({ capabilities: { canDelete: false, canPatch: true } });
        await openMenu();

        expect(await muteMenuItem()).toBeInTheDocument();
        expect(
            screen.queryByRole("menuitem", { name: i18n.t("watchbox.delete") }),
        ).not.toBeInTheDocument();
    });

    it("renders the menu regardless of capabilities, unlike the switch", async () => {
        renderCard({ capabilities: { canDelete: false, canPatch: false } });

        expect(menuTrigger()).toBeInTheDocument();
        expect(screen.queryByRole("switch")).not.toBeInTheDocument();

        // The fully read-only case: neither canPatch nor canDelete, which is
        // exactly when Mute must still work — it's the only self-service
        // control such a viewer has.
        await openMenu();
        expect(await muteMenuItem()).toBeInTheDocument();
    });
});
