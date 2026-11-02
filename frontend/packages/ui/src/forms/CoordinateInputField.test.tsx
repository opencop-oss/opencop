// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import { renderHook } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { render, screen } from "~/test";
import {
    DisplayPreferencesProvider,
    useDisplayPreferences,
    type DisplayPreferences,
} from "../preferences/DisplayPreferencesContext.tsx";
import { CoordinateInputField } from "./CoordinateInputField.tsx";

const LABEL = "Position";

const preferences = (overrides: Partial<DisplayPreferences> = {}): DisplayPreferences => ({
    measurementSystem: "METRIC",
    coordFormat: "DD",
    setCoordFormat: vi.fn(),
    angleUnit: "degrees",
    setAngleUnit: vi.fn(),
    angleOrientation: "ccw",
    ...overrides,
});

describe("CoordinateInputField", () => {
    it("shows the operator's format and changes it through the display preferences", async () => {
        const setCoordFormat = vi.fn();
        render(
            <DisplayPreferencesProvider value={preferences({ coordFormat: "DMS", setCoordFormat })}>
                <CoordinateInputField label={LABEL} showFormatSelector />
            </DisplayPreferencesProvider>,
        );

        await userEvent.click(screen.getByRole("button", { name: /DMS/ }));
        await userEvent.click(await screen.findByRole("menuitem", { name: "MGRS" }));

        expect(setCoordFormat).toHaveBeenCalledWith("MGRS");
    });
});

describe("useDisplayPreferences", () => {
    it("throws outside a provider", () => {
        // React logs the render error before rethrowing it.
        const consoleError = vi.spyOn(console, "error").mockImplementation(() => undefined);
        expect(() => renderHook(() => useDisplayPreferences())).toThrow(
            "useDisplayPreferences must be used within a DisplayPreferencesProvider",
        );
        consoleError.mockRestore();
    });
});
