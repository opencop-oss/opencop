// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { I18nextProvider } from "react-i18next";
import { i18n } from "~/i18n/config";
import { OcThemeProvider } from "../theme/OcThemeProvider";
import { OcGroupCombobox, UNGROUPED_GROUP_ID, type OcGroupComboboxItem } from "./OcGroupCombobox";

function renderCombobox(props: Partial<Parameters<typeof OcGroupCombobox>[0]> = {}) {
    return render(
        <I18nextProvider i18n={i18n}>
            <OcThemeProvider>
                <OcGroupCombobox
                    items={[]}
                    groups={[]}
                    value=""
                    onValueChange={vi.fn()}
                    portal={false}
                    {...props}
                />
            </OcThemeProvider>
        </I18nextProvider>,
    );
}

async function openDropdown() {
    await userEvent.click(screen.getByRole("combobox"));
}

const GROUPS = [
    { id: "recents", label: "Recent Layers" },
    { id: "favorites", label: "Favorites" },
];

const ITEMS: OcGroupComboboxItem[] = [
    { value: "a", label: "Alpha", group: "recents" },
    { value: "b", label: "Bravo", group: "recents" },
    { value: "c", label: "Charlie", group: "favorites" },
    { value: "d", label: "Delta", group: UNGROUPED_GROUP_ID },
];

describe("OcGroupCombobox — group rendering", () => {
    it("renders named group labels as uppercase section headings", async () => {
        renderCombobox({ items: ITEMS, groups: GROUPS });
        await openDropdown();

        expect(screen.getByText("Recent Layers")).toBeVisible();
        expect(screen.getByText("Favorites")).toBeVisible();
    });

    it("renders items under the correct group", async () => {
        renderCombobox({ items: ITEMS, groups: GROUPS, ungroupedLabel: "Other" });
        await openDropdown();

        const recentsGroup = screen
            .getByText("Recent Layers")
            .closest("[role=group]") as HTMLElement;
        const favoritesGroup = screen.getByText("Favorites").closest("[role=group]") as HTMLElement;
        const otherGroup = screen.getByText("Other").closest("[role=group]") as HTMLElement;

        expect(within(recentsGroup).getByRole("option", { name: "Alpha" })).toBeVisible();
        expect(within(recentsGroup).getByRole("option", { name: "Bravo" })).toBeVisible();
        expect(within(favoritesGroup).getByRole("option", { name: "Charlie" })).toBeVisible();
        expect(within(otherGroup).getByRole("option", { name: "Delta" })).toBeVisible();
    });

    it("hides groups that have no matching items after filtering", async () => {
        const itemsWithoutFavorites = ITEMS.filter((i) => i.group !== "favorites");
        renderCombobox({ items: itemsWithoutFavorites, groups: GROUPS });
        await openDropdown();

        expect(screen.queryByText("Favorites")).toBeNull();
        expect(screen.getByText("Recent Layers")).toBeVisible();
    });

    it("walks the keyboard highlight in rendered order across groups", async () => {
        renderCombobox({ items: ITEMS, groups: GROUPS, ungroupedLabel: "Other" });
        const input = screen.getByRole("combobox");
        await userEvent.click(input);
        const renderedIds = screen.getAllByRole("option").map((o) => o.id);
        for (const expected of renderedIds) {
            await userEvent.keyboard("{ArrowDown}");
            expect(input).toHaveAttribute("aria-activedescendant", expected);
        }
    });

    it("orders items by group definition regardless of the caller's array order", async () => {
        const UNSORTED: OcGroupComboboxItem[] = [
            { value: "d", label: "Delta", group: UNGROUPED_GROUP_ID },
            { value: "c", label: "Charlie", group: "favorites" },
            { value: "a", label: "Alpha", group: "recents" },
        ];
        renderCombobox({ items: UNSORTED, groups: GROUPS, ungroupedLabel: "Other" });
        const input = screen.getByRole("combobox");
        await userEvent.click(input);
        for (const expected of ["Alpha", "Charlie", "Delta"]) {
            await userEvent.keyboard("{ArrowDown}");
            const active = document.getElementById(
                input.getAttribute("aria-activedescendant") ?? "",
            );
            expect(active).toHaveTextContent(expected);
        }
    });

    it("preserves item order within a group as provided by the caller", async () => {
        renderCombobox({ items: ITEMS, groups: GROUPS });
        await openDropdown();

        const recentsGroup = screen
            .getByText("Recent Layers")
            .closest("[role=group]") as HTMLElement;
        const options = within(recentsGroup).getAllByRole("option");
        expect(options[0]).toHaveTextContent("Alpha");
        expect(options[1]).toHaveTextContent("Bravo");
    });
});

describe("OcGroupCombobox — ungrouped items", () => {
    it("renders ungrouped items under the ungroupedLabel when provided", async () => {
        renderCombobox({ items: ITEMS, groups: GROUPS, ungroupedLabel: "All Items" });
        await openDropdown();

        expect(screen.getByText("All Items")).toBeVisible();
        expect(screen.getByRole("option", { name: "Delta" })).toBeVisible();
    });

    it("renders ungrouped items without a visible label when ungroupedLabel is omitted", async () => {
        renderCombobox({ items: ITEMS, groups: GROUPS });
        await openDropdown();

        // The sr-only label must exist (aria-labelledby would dangle otherwise) and be visually hidden.
        const srLabel = screen.getByText("Other");
        expect(srLabel).toHaveStyle({ position: "absolute", width: "1px", clip: "rect(0,0,0,0)" });
        expect(screen.getByRole("option", { name: "Delta" })).toBeVisible();
    });
});
