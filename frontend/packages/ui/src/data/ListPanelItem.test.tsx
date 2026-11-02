// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import { I18nextProvider } from "react-i18next";
import { i18n } from "~/i18n/config";
import { OcThemeProvider } from "../theme/OcThemeProvider";
import { ListPanelItem } from "./ListPanelItem";

const TITLE = "Alpha";

function renderItem(props: { onClick?: () => void; disabled?: boolean } = {}) {
    const onClick = props.onClick ?? vi.fn();
    // Plain controls with no stopPropagation of their own, so only ListPanelItem's click rule
    // can keep them from activating the row.
    const control = (label: string): ReactNode => (
        <button type="button" onClick={vi.fn()}>
            {label}
        </button>
    );
    render(
        <I18nextProvider i18n={i18n}>
            <OcThemeProvider>
                <ListPanelItem
                    index={0}
                    isSelected={false}
                    disabled={props.disabled}
                    onClick={onClick}
                    ariaLabel={TITLE}
                    title={TITLE}
                    leftSlot={control("Left")}
                    rightSlot={control("Right")}
                    bottomSlot={
                        <>
                            <span>Second line</span>
                            {control("Bottom")}
                        </>
                    }
                    actions={control("Hover action")}
                    actionsVisible
                />
            </OcThemeProvider>
        </I18nextProvider>,
    );
    return { onClick, row: screen.getByRole("button", { name: TITLE }) };
}

const SLOT_CONTROLS = ["Left", "Right", "Bottom", "Hover action"];

describe("ListPanelItem", () => {
    it("keeps every slot's controls outside the row button", () => {
        const { row } = renderItem();

        for (const name of SLOT_CONTROLS) {
            expect(row).not.toContainElement(screen.getByRole("button", { name }));
        }
    });

    it("renders the row as a native button, activating once per click on the title", async () => {
        const user = userEvent.setup();
        const { onClick, row } = renderItem();

        expect(row.tagName).toBe("BUTTON");
        await user.click(row);

        expect(onClick).toHaveBeenCalledTimes(1);
    });

    it("activates from the keyboard and from a click elsewhere on the card", async () => {
        const user = userEvent.setup();
        const { onClick, row } = renderItem();

        row.focus();
        await user.keyboard("{Enter}");
        await user.keyboard(" ");
        await user.click(screen.getByText("Second line"));

        expect(onClick).toHaveBeenCalledTimes(3);
    });

    it("does not activate when a slot's own control is clicked", async () => {
        const user = userEvent.setup();
        const { onClick } = renderItem();

        for (const name of SLOT_CONTROLS) {
            await user.click(screen.getByRole("button", { name }));
        }

        expect(onClick).not.toHaveBeenCalled();
    });

    it("tabs to the row button before the hover actions", async () => {
        const user = userEvent.setup();
        renderItem();

        const stops: string[] = [];
        for (let i = 0; i < SLOT_CONTROLS.length + 1; i++) {
            await user.tab();
            stops.push(document.activeElement?.textContent ?? "");
        }

        expect(stops.indexOf(TITLE)).toBeGreaterThanOrEqual(0);
        expect(stops.indexOf(TITLE)).toBeLessThan(stops.indexOf("Hover action"));
    });

    it("ignores clicks and keys while disabled, and leaves the tab order", async () => {
        const user = userEvent.setup();
        const { onClick, row } = renderItem({ disabled: true });

        expect(row).toHaveAttribute("aria-disabled", "true");
        expect(row).toHaveAttribute("tabindex", "-1");
        row.focus();
        await user.keyboard("{Enter}");
        await user.click(screen.getByText("Second line"));

        expect(onClick).not.toHaveBeenCalled();
    });
});
