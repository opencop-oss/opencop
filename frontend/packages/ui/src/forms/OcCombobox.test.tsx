// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { I18nextProvider } from "react-i18next";
import { i18n } from "~/i18n/config";
import { OcThemeProvider } from "../theme/OcThemeProvider";
import { OcCombobox } from "./OcCombobox";

const ITEMS = [
    { label: "pineapple", value: "p" },
    { label: "kiwi", value: "k" },
    { label: "mango", value: "m" },
];

function renderCombobox(props: Partial<Parameters<typeof OcCombobox>[0]> = {}) {
    return render(
        <I18nextProvider i18n={i18n}>
            <OcThemeProvider>
                <OcCombobox
                    multiple
                    items={ITEMS}
                    value={[]}
                    onValueChange={vi.fn()}
                    portal={false}
                    {...props}
                />
            </OcThemeProvider>
        </I18nextProvider>,
    );
}

/** The listbox only renders once opened. */
async function openList(user: ReturnType<typeof userEvent.setup>) {
    await user.click(screen.getByRole("combobox"));
}

describe("OcCombobox — hideSelectedOptions", () => {
    it("drops a selected option from the list but keeps its tag", async () => {
        const user = userEvent.setup();
        renderCombobox({ hideSelectedOptions: true, value: ["p"] });

        await openList(user);

        expect(screen.queryByRole("option", { name: "pineapple" })).not.toBeInTheDocument();
        expect(screen.getByRole("option", { name: "kiwi" })).toBeInTheDocument();
        // Still reachable to deselect, which is what makes hiding it safe.
        expect(screen.getByRole("button", { name: /pineapple/i })).toBeInTheDocument();
    });

    // Existing callers must be untouched by the new prop.
    it("keeps a selected option listed by default", async () => {
        const user = userEvent.setup();
        renderCombobox({ value: ["p"] });

        await openList(user);

        expect(screen.getByRole("option", { name: "pineapple" })).toBeInTheDocument();
    });

    it("composes with the search filter", async () => {
        const user = userEvent.setup();
        renderCombobox({ hideSelectedOptions: true, value: ["k"] });

        await openList(user);
        await user.type(screen.getByRole("combobox"), "k");

        // "kiwi" matches the search but is selected; "pineapple" is unselected
        // but does not match.
        expect(screen.queryByRole("option", { name: "kiwi" })).not.toBeInTheDocument();
        expect(screen.queryByRole("option", { name: "pineapple" })).not.toBeInTheDocument();
    });

    it("shows the caller's empty message once everything is selected", async () => {
        const user = userEvent.setup();
        renderCombobox({
            hideSelectedOptions: true,
            value: ["p", "k", "m"],
            emptyMessage: "All eligible layers are selected.",
        });

        await openList(user);

        expect(screen.getByText("All eligible layers are selected.")).toBeInTheDocument();
    });
});
