// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi, afterEach } from "vitest";
import { I18nextProvider } from "react-i18next";
import { i18n } from "~/i18n/config";
import { OcThemeProvider } from "../theme/OcThemeProvider";
import { OcRecentsComboBox, type OcRecentsComboboxItem } from "./OcRecentsCombobox";

function renderCombobox(
    props: Partial<Parameters<typeof OcRecentsComboBox>[0]> & { storageKey?: string } = {},
) {
    return render(
        <I18nextProvider i18n={i18n}>
            <OcThemeProvider>
                <OcRecentsComboBox
                    items={[]}
                    value=""
                    onValueChange={vi.fn()}
                    storageKey={props.storageKey ?? "test:recents"}
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

const ITEMS: OcRecentsComboboxItem[] = [
    { value: "alpha", label: "Alpha" },
    { value: "bravo", label: "Bravo" },
    { value: "charlie", label: "Charlie" },
];

const SIX_ITEMS: OcRecentsComboboxItem[] = [
    ...ITEMS,
    { value: "delta", label: "Delta" },
    { value: "echo", label: "Echo" },
    { value: "foxtrot", label: "Foxtrot" },
];

describe("OcRecentsComboBox — initial render", () => {
    it("renders all items with no recents group when nothing has been selected", async () => {
        renderCombobox({ items: ITEMS, ungroupedLabel: "All Items" });
        await openDropdown();

        expect(screen.queryByText("Recent")).toBeNull();
        const options = screen.getAllByRole("option");
        expect(options.map((o) => o.textContent?.trim())).toEqual(["Alpha", "Bravo", "Charlie"]);
    });

    it("uses a custom recentsLabel when provided", async () => {
        // Pre-seed localStorage so the recents group appears
        localStorage.setItem(
            "test:custom-label",
            JSON.stringify({ state: { recentItems: [{ label: "Alpha", value: "alpha" }] } }),
        );

        renderCombobox({
            items: ITEMS,
            storageKey: "test:custom-label",
            recentsLabel: "Favourites",
        });
        await openDropdown();

        expect(screen.getByText("Favourites")).toBeVisible();
        localStorage.removeItem("test:custom-label");
    });
});

describe("OcRecentsComboBox — recency ordering", () => {
    it("shows recently selected items at the top in most-recent-first order", async () => {
        const { unmount } = renderCombobox({ items: ITEMS, storageKey: "test:order" });
        await openDropdown();
        await userEvent.click(screen.getByRole("option", { name: "Bravo" }));
        unmount();

        const { unmount: unmount2 } = renderCombobox({ items: ITEMS, storageKey: "test:order" });
        await openDropdown();
        await userEvent.click(screen.getByRole("option", { name: "Charlie" }));
        unmount2();

        renderCombobox({ items: ITEMS, storageKey: "test:order", ungroupedLabel: "All" });
        await openDropdown();

        const labels = screen.getAllByRole("option").map((o) => o.textContent?.trim());
        // Charlie was most recent, then Bravo, then Alpha (still in ungrouped)
        expect(labels).toEqual(["Charlie", "Bravo", "Alpha"]);
    });

    it("removes an item from ungrouped when it becomes a recent", async () => {
        const { unmount } = renderCombobox({
            items: ITEMS,
            storageKey: "test:move",
            ungroupedLabel: "All",
        });
        await openDropdown();
        await userEvent.click(screen.getByRole("option", { name: "Bravo" }));
        unmount();

        renderCombobox({ items: ITEMS, storageKey: "test:move", ungroupedLabel: "All" });
        await openDropdown();

        const recentsGroup = screen.getByText("Recent").closest("[role=group]") as HTMLElement;
        const allGroup = screen.getByText("All").closest("[role=group]") as HTMLElement;

        expect(within(recentsGroup).getByRole("option", { name: "Bravo" })).toBeVisible();
        expect(within(allGroup).queryByRole("option", { name: "Bravo" })).toBeNull();
    });
});

describe("OcRecentsComboBox — stale recents", () => {
    it("silently drops recents whose value is no longer in the items list", async () => {
        localStorage.setItem(
            "test:stale",
            JSON.stringify({
                state: {
                    recentItems: [
                        { label: "Removed Item", value: "removed" },
                        { label: "Alpha", value: "alpha" },
                    ],
                },
            }),
        );

        renderCombobox({ items: ITEMS, storageKey: "test:stale" });
        await openDropdown();

        const options = screen.getAllByRole("option").map((o) => o.textContent?.trim());
        expect(options).not.toContain("Removed Item");
        expect(options[0]).toBe("Alpha");
        localStorage.removeItem("test:stale");
    });
});

describe("OcRecentsComboBox — dead recents against the display cap", () => {
    it("keeps 5 live recents visible even when a stale entry occupies a history slot ahead of them", async () => {
        // Simulates a layer that was recently picked and has since been deleted: it still occupies
        // a slot in history (ahead of 5 still-valid recents) but is no longer in `items`.
        localStorage.setItem(
            "test:dead-recent",
            JSON.stringify({
                state: {
                    recentItems: [
                        { label: "Deleted Layer", value: "deleted" },
                        { label: "Alpha", value: "alpha" },
                        { label: "Bravo", value: "bravo" },
                        { label: "Charlie", value: "charlie" },
                        { label: "Delta", value: "delta" },
                        { label: "Echo", value: "echo" },
                    ],
                },
            }),
        );

        renderCombobox({
            items: SIX_ITEMS,
            storageKey: "test:dead-recent",
            ungroupedLabel: "All",
        });
        await openDropdown();

        const recentsGroup = screen.getByText("Recent").closest("[role=group]") as HTMLElement;
        const recentLabels = within(recentsGroup)
            .getAllByRole("option")
            .map((o) => o.textContent?.trim());
        expect(recentLabels).toEqual(["Alpha", "Bravo", "Charlie", "Delta", "Echo"]);
        localStorage.removeItem("test:dead-recent");
    });
});

describe("OcRecentsComboBox — key isolation", () => {
    it("two instances with different storageKeys maintain independent histories", async () => {
        const { unmount } = renderCombobox({ items: ITEMS, storageKey: "test:key-x" });
        await openDropdown();
        await userEvent.click(screen.getByRole("option", { name: "Alpha" }));
        unmount();

        // key-y has its own localStorage entry — no recents
        renderCombobox({ items: ITEMS, storageKey: "test:key-y", ungroupedLabel: "All" });
        await openDropdown();

        expect(screen.queryByText("Recent")).toBeNull();
        const options = screen.getAllByRole("option").map((o) => o.textContent?.trim());
        expect(options).toEqual(["Alpha", "Bravo", "Charlie"]);
    });
});

describe("OcRecentsComboBox — onValueChange", () => {
    it("calls onValueChange with the selected item value", async () => {
        const onValueChange = vi.fn();
        renderCombobox({ items: ITEMS, onValueChange, storageKey: "test:callback" });
        await openDropdown();
        await userEvent.click(screen.getByRole("option", { name: "Charlie" }));

        expect(onValueChange).toHaveBeenCalledWith("charlie");
    });

    it("keeps the primary selection callback even if localStorage.setItem throws", async () => {
        const onValueChange = vi.fn();
        // happy-dom's localStorage doesn't delegate through Storage.prototype, so the spy has to
        // target the instance itself or the mock never intercepts the real call.
        const setItemSpy = vi.spyOn(localStorage, "setItem").mockImplementation(() => {
            throw new DOMException("The quota has been exceeded.", "QuotaExceededError");
        });
        renderCombobox({ items: ITEMS, onValueChange, storageKey: "test:quota" });
        await openDropdown();
        await userEvent.click(screen.getByRole("option", { name: "Charlie" }));
        expect(setItemSpy).toHaveBeenCalled();
        expect(onValueChange).toHaveBeenCalledWith("charlie");
        setItemSpy.mockRestore();
    });
});

describe("OcRecentsComboBox — cap and dedup", () => {
    afterEach(() => localStorage.removeItem("test:cap"));

    it("caps recents at 5 and evicts the oldest", async () => {
        for (const name of ["Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot"]) {
            const { unmount } = renderCombobox({
                items: SIX_ITEMS,
                storageKey: "test:cap",
                ungroupedLabel: "All",
            });
            await openDropdown();
            await userEvent.click(screen.getByRole("option", { name }));
            unmount();
        }

        renderCombobox({ items: SIX_ITEMS, storageKey: "test:cap", ungroupedLabel: "All" });
        await openDropdown();

        const recentsGroup = screen.getByText("Recent").closest("[role=group]") as HTMLElement;
        expect(within(recentsGroup).getAllByRole("option")).toHaveLength(5);
        expect(within(recentsGroup).queryByRole("option", { name: "Alpha" })).toBeNull();
    });

    it("caps recents at 5 within a single mount", async () => {
        renderCombobox({ items: SIX_ITEMS, storageKey: "test:cap-live", ungroupedLabel: "All" });
        const input = screen.getByRole("combobox");
        for (const name of ["Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot"]) {
            await userEvent.clear(input);
            await userEvent.click(input);
            await userEvent.click(screen.getByRole("option", { name }));
        }
        await userEvent.clear(input);
        await userEvent.click(input);
        const recents = screen.getByText("Recent").closest("[role=group]") as HTMLElement;
        expect(within(recents).getAllByRole("option")).toHaveLength(5);
        expect(within(recents).queryByRole("option", { name: "Alpha" })).toBeNull();

        // A recent that's stored but pushed past the display cap must still be reachable — it
        // belongs in the ungrouped list, not stranded in neither group.
        const all = screen.getByText("All").closest("[role=group]") as HTMLElement;
        expect(within(all).getByRole("option", { name: "Alpha" })).toBeInTheDocument();

        localStorage.removeItem("test:cap-live");
    });

    it("re-selecting an existing recent moves it to the front without duplicating it", async () => {
        const { unmount: u1 } = renderCombobox({ items: ITEMS, storageKey: "test:dedup" });
        await openDropdown();
        await userEvent.click(screen.getByRole("option", { name: "Bravo" }));
        u1();

        const { unmount: u2 } = renderCombobox({ items: ITEMS, storageKey: "test:dedup" });
        await openDropdown();
        await userEvent.click(screen.getByRole("option", { name: "Charlie" }));
        u2();

        // Re-select Bravo — should move to front, not duplicate
        const { unmount: u3 } = renderCombobox({ items: ITEMS, storageKey: "test:dedup" });
        await openDropdown();
        await userEvent.click(screen.getByRole("option", { name: "Bravo" }));
        u3();

        renderCombobox({ items: ITEMS, storageKey: "test:dedup", ungroupedLabel: "All" });
        await openDropdown();

        const recentsGroup = screen.getByText("Recent").closest("[role=group]") as HTMLElement;
        const recentOptions = within(recentsGroup).getAllByRole("option");
        expect(recentOptions).toHaveLength(2);
        expect(recentOptions[0]).toHaveTextContent("Bravo");
    });
});
