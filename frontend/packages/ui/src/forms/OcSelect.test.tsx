// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { I18nextProvider } from "react-i18next";
import { i18n } from "~/i18n/config";
import { OcThemeProvider } from "../theme/OcThemeProvider";
import { OcSelect, type SelectGroup, type SelectItem } from "./OcSelect";

const PLACEHOLDER = "Pick one";

// These cases all exercise the single-select grouped path. (A `Partial` of the
// full props can't be spread here — OcSelectProps is a discriminated union on
// `multiple`, so a partial loses the discriminant.)
function renderSelect(
    props: { groups?: SelectGroup<SelectItem>[]; items?: SelectItem[]; value?: string } = {},
) {
    return render(
        <I18nextProvider i18n={i18n}>
            <OcThemeProvider>
                <OcSelect placeholder={PLACEHOLDER} {...props} />
            </OcThemeProvider>
        </I18nextProvider>,
    );
}

/** The visible <button> trigger (a hidden native <select> also has role combobox). */
function trigger() {
    return within(
        screen.getAllByRole("combobox").find((el) => el.tagName === "BUTTON") as HTMLElement,
    );
}

describe("OcSelect grouped items", () => {
    const groups: SelectGroup<SelectItem>[] = [
        { label: "Layers", items: [{ value: "layer-1", label: "Op Atlas" }] },
        { label: "Data Sources", items: [{ value: "source-1", label: "TAK Server" }] },
    ];

    // The grouped path flattens every group's items into the value collection,
    // so a selected value from any group resolves to its label in the trigger
    // (rather than falling back to the placeholder).
    it("resolves a value from the first group", () => {
        renderSelect({ groups, value: "layer-1" });
        expect(trigger().getByText("Op Atlas")).toBeTruthy();
        expect(trigger().queryByText("Pick one")).toBeNull();
    });

    it("resolves a value from a later group", () => {
        renderSelect({ groups, value: "source-1" });
        expect(trigger().getByText("TAK Server")).toBeTruthy();
        expect(trigger().queryByText("Pick one")).toBeNull();
    });

    it("shows the placeholder when nothing is selected", () => {
        renderSelect({ groups, value: "" });
        expect(trigger().getByText("Pick one")).toBeTruthy();
    });
});
