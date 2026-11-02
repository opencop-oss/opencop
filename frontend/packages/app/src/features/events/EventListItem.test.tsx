// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import { act, render, screen, userEvent } from "~/test";
import { i18n } from "~/i18n/config";
import type { Element } from "@opencop/elements";
import { EventListItem } from "./EventListItem";

function makeElement(name: string): Element {
    return {
        key: `test\0${name}`,
        objectId: name,
        source: "test",
        getDisplayName: () => name,
    } as unknown as Element;
}

const LOCALES = ["en", "fr", "tw"] as const;

describe.each(LOCALES)("EventListItem icon buttons in locale %s", (locale) => {
    it("every icon button has a non-empty accessible name", async () => {
        await act(async () => {
            await i18n.changeLanguage(locale);
        });

        render(
            <EventListItem
                element={makeElement("Alpha")}
                index={0}
                onClick={vi.fn()}
                onFocusOnMap={vi.fn()}
            />,
        );

        const buttons = screen.getAllByRole("button");
        expect(buttons.length).toBeGreaterThan(0);
        for (const button of buttons) {
            const name = button.getAttribute("aria-label") ?? button.textContent;
            expect(name?.trim()).toBeTruthy();
        }

        await act(async () => {
            await i18n.changeLanguage("en");
        });
    });
});

describe("EventListItem layout", () => {
    it("puts the leading control before both lines, and the pin last after them", () => {
        render(
            <EventListItem
                element={makeElement("Alpha")}
                index={0}
                onClick={vi.fn()}
                onFocusOnMap={vi.fn()}
                leading={<input type="checkbox" aria-label="Select Alpha" />}
                actions={<span data-testid="row-actions" />}
            />,
        );

        const checkbox = screen.getByRole("checkbox", { name: "Select Alpha" });
        for (const text of [
            screen.getByText("Alpha"),
            screen.getByRole("button", { name: "Copy ID for Alpha" }),
        ]) {
            expect(
                checkbox.compareDocumentPosition(text) & Node.DOCUMENT_POSITION_FOLLOWING,
            ).toBeTruthy();
        }
        const rowActions = screen.getByTestId("row-actions");
        const pin = screen.getByRole("button", { name: "Show on map" });
        expect(rowActions.parentElement).toContainElement(pin);
        expect(rowActions.parentElement).not.toContainElement(checkbox);
        // The actions column sits beside both lines, after the second line rather than on the
        // name line.
        expect(rowActions.parentElement).not.toContainElement(screen.getByText("Alpha"));
        expect(
            screen
                .getByRole("button", { name: "Copy ID for Alpha" })
                .compareDocumentPosition(rowActions) & Node.DOCUMENT_POSITION_FOLLOWING,
        ).toBeTruthy();
        // The pin is the rightmost action, after the caller's own (e.g. the Read-only tag).
        expect(
            rowActions.compareDocumentPosition(pin) & Node.DOCUMENT_POSITION_FOLLOWING,
        ).toBeTruthy();
    });
});

describe("EventListItem second line", () => {
    it("shows a Copy ID button instead of the ID, then the layer name", async () => {
        const user = userEvent.setup();
        const writeText = vi.spyOn(navigator.clipboard, "writeText").mockResolvedValue();
        render(
            <EventListItem
                element={makeElement("Alpha")}
                index={0}
                onClick={vi.fn()}
                container={{ kind: "layer", name: "Ops layer" }}
            />,
        );

        // The display name is the only "Alpha" on the card; the object ID isn't printed.
        expect(screen.getAllByText("Alpha")).toHaveLength(1);
        const copy = screen.getByRole("button", { name: "Copy ID for Alpha" });
        const layer = screen.getByText((_, node) => node?.textContent === "Layer: Ops layer");
        expect(copy.compareDocumentPosition(layer) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();

        await user.click(copy);
        expect(writeText).toHaveBeenCalledWith("Alpha");
        expect(screen.getByRole("button", { name: "Copied ID for Alpha" })).toBeVisible();
    });

    it("labels a data source as one", () => {
        render(
            <EventListItem
                element={makeElement("Alpha")}
                index={0}
                onClick={vi.fn()}
                container={{ kind: "dataSource", name: "Radar feed" }}
            />,
        );

        expect(
            screen.getByText((_, node) => node?.textContent === "Data source: Radar feed"),
        ).toBeVisible();
    });

    it("omits the layer when there is none", () => {
        render(<EventListItem element={makeElement("Alpha")} index={0} onClick={vi.fn()} />);

        expect(screen.queryByText(/^(Layer|Data source):/)).toBeNull();
    });
});

describe("EventListItem row button", () => {
    function renderRow() {
        const onClick = vi.fn();
        const onFocusOnMap = vi.fn();
        render(
            <EventListItem
                element={makeElement("Alpha")}
                index={0}
                onClick={onClick}
                onFocusOnMap={onFocusOnMap}
                leading={<input type="checkbox" aria-label="Select Alpha" />}
                container={{ kind: "layer", name: "Ops layer" }}
            />,
        );
        // The row button's accessible name is the display name; every other control is named.
        const row = screen
            .getAllByRole("button")
            .find((button) => button.getAttribute("aria-label") === "Alpha");
        if (!row) throw new Error("row button not rendered");
        return { row, onClick, onFocusOnMap };
    }

    it("contains none of the row's other controls", () => {
        const { row } = renderRow();

        expect(row).not.toContainElement(screen.getByRole("button", { name: "Show on map" }));
        expect(row).not.toContainElement(screen.getByRole("button", { name: "Copy ID for Alpha" }));
        expect(row).not.toContainElement(screen.getByRole("checkbox", { name: "Select Alpha" }));
    });

    it("activates from the keyboard and from a click anywhere on the card, but not from its controls", async () => {
        const user = userEvent.setup();
        const { row, onClick, onFocusOnMap } = renderRow();

        row.focus();
        await user.keyboard("{Enter}");
        expect(onClick).toHaveBeenCalledTimes(1);

        // The second line sits outside the row button but is still part of the card.
        await user.click(screen.getByText("Ops layer", { exact: false }));
        expect(onClick).toHaveBeenCalledTimes(2);

        await user.click(screen.getByRole("button", { name: "Show on map" }));
        await user.click(screen.getByRole("checkbox", { name: "Select Alpha" }));
        await user.click(screen.getByRole("button", { name: "Copy ID for Alpha" }));
        expect(onFocusOnMap).toHaveBeenCalledOnce();
        expect(onClick).toHaveBeenCalledTimes(2);
    });
});
