// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { fireEvent, render, screen, userEvent } from "~/test";
import type { ReactElement } from "react";
import { describe, expect, it, vi } from "vitest";
import { FaPlay } from "react-icons/fa";
import { DrawerToolbarButton } from "./DrawerToolbarButton";

const START_LABEL = "Start";
const EDIT_LABEL = "Edit";
const DISABLED_REASON = "Owner only";

function renderButton(ui: ReactElement) {
    return render(ui);
}

describe("DrawerToolbarButton", () => {
    it("fires onClick when clicked", () => {
        const onClick = vi.fn();
        renderButton(
            <DrawerToolbarButton label={START_LABEL} icon={<FaPlay />} onClick={onClick} />,
        );

        fireEvent.click(screen.getByRole("button", { name: START_LABEL }));

        expect(onClick).toHaveBeenCalledOnce();
    });

    it("does not fire onClick when disabled", () => {
        const onClick = vi.fn();
        renderButton(
            <DrawerToolbarButton
                label={START_LABEL}
                icon={<FaPlay />}
                onClick={onClick}
                disabled
            />,
        );

        fireEvent.click(screen.getByRole("button", { name: START_LABEL }));

        expect(onClick).not.toHaveBeenCalled();
    });

    it("renders as a link to `to` when interactive", () => {
        renderButton(<DrawerToolbarButton label={EDIT_LABEL} icon={<FaPlay />} to="/admin/edit" />);

        expect(screen.getByRole("link").getAttribute("href")).toBe("/admin/edit");
        expect(screen.queryByRole("button")).toBeNull();
    });

    it("renders a disabled button, not a link, when `to` is combined with disabled", () => {
        renderButton(
            <DrawerToolbarButton label={EDIT_LABEL} icon={<FaPlay />} to="/admin/edit" disabled />,
        );

        expect(screen.queryByRole("link")).toBeNull();
        expect(
            (screen.getByRole("button", { name: EDIT_LABEL }) as HTMLButtonElement).disabled,
        ).toBe(true);
    });

    it("renders a button, not a link, when `to` is combined with loading", () => {
        // Regression: `loading` + `to` used to leave the button as a clickable link with no
        // spinner. Folding `loading` into the link condition makes it render as a real <button>.
        renderButton(
            <DrawerToolbarButton label={EDIT_LABEL} icon={<FaPlay />} to="/admin/edit" loading />,
        );

        expect(screen.queryByRole("link")).toBeNull();
        expect(screen.getByRole("button")).toBeTruthy();
    });

    it("renders a loading spinner when loading", () => {
        const { container } = renderButton(
            <DrawerToolbarButton label={START_LABEL} icon={<FaPlay />} loading />,
        );

        expect(screen.getByRole("button").hasAttribute("data-loading")).toBe(true);
        expect(container.querySelector(".chakra-spinner")).not.toBeNull();
    });

    it("applies distinct styling when destructive", () => {
        // Destructive only changes the hover color, which jsdom can't compute — but Chakra
        // compiles it to a different atomic class, so the button className must differ.
        const { container: plain } = renderButton(
            <DrawerToolbarButton label={START_LABEL} icon={<FaPlay />} />,
        );
        const { container: destructive } = renderButton(
            <DrawerToolbarButton label={START_LABEL} icon={<FaPlay />} destructive />,
        );

        const plainClass = plain.querySelector("button")?.getAttribute("class");
        const destructiveClass = destructive.querySelector("button")?.getAttribute("class");
        expect(destructiveClass).toBeTruthy();
        expect(destructiveClass).not.toBe(plainClass);
    });

    it("shows the disabledReason (not the label) in the tooltip when disabled with a reason", async () => {
        const user = userEvent.setup();
        renderButton(
            <DrawerToolbarButton
                label={START_LABEL}
                icon={<FaPlay />}
                disabled
                disabledReason={DISABLED_REASON}
            />,
        );

        // Trigger is the focusable <span> wrapping the disabled button.
        await user.hover(screen.getByRole("button").parentElement as HTMLElement);

        // A reason is prose, so it keeps its sentence case rather than being shouted like a label.
        expect(await screen.findByText(DISABLED_REASON)).toBeTruthy();
        expect(screen.queryByText(DISABLED_REASON.toUpperCase())).toBeNull();
        expect(screen.queryByText(START_LABEL.toUpperCase())).toBeNull();
    });

    it("shows the label in the tooltip when enabled (no disabledReason swap)", async () => {
        const user = userEvent.setup();
        renderButton(
            <DrawerToolbarButton label={START_LABEL} icon={<FaPlay />} onClick={vi.fn()} />,
        );

        await user.hover(screen.getByRole("button"));

        expect(await screen.findByText(START_LABEL.toUpperCase())).toBeTruthy();
    });
});
