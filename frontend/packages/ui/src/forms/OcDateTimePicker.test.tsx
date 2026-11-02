// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { fireEvent, render, screen } from "@testing-library/react";
import { DateTime } from "luxon";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { I18nextProvider } from "react-i18next";
import { i18n } from "~/i18n/config";
import { OcThemeProvider } from "../theme/OcThemeProvider";
import { OcDateTimePicker, type OcDateTimePickerProps } from "./OcDateTimePicker";

// Fixed bounds so assertions against the picker's own min/max validation
// are deterministic regardless of when the test runs. UTC zone keeps the
// `yyyy-MM-dd HH:mm` formatted output stable across CI machines.
const MIN = DateTime.fromISO("2026-05-01T00:00:00.000Z", { zone: "utc" });
const MAX = DateTime.fromISO("2026-05-31T23:59:00.000Z", { zone: "utc" });
const START = DateTime.fromISO("2026-05-15T12:00:00.000Z", { zone: "utc" });

function renderPicker(props: Partial<OcDateTimePickerProps> = {}) {
    return render(
        <I18nextProvider i18n={i18n}>
            <OcThemeProvider>
                <OcDateTimePicker
                    typeable
                    timezone="UTC"
                    min={MIN}
                    max={MAX}
                    startTime={START}
                    inputProps={{ "aria-label": "Picker" }}
                    {...props}
                />
            </OcThemeProvider>
        </I18nextProvider>,
    );
}

function getInput(): HTMLInputElement {
    return screen.getByRole("textbox", { name: "Picker" }) as HTMLInputElement;
}

function setBufferTo(input: HTMLInputElement, value: string) {
    // The slot-aware overtype branch only fires when buffer.length === 16
    // AND the change is a single-character delta. To set arbitrary values
    // for a test, first clear (matches "select-all-and-delete" — falls
    // through to the mask), then set the target value as a single change.
    fireEvent.change(input, { target: { value: "" } });
    fireEvent.change(input, { target: { value } });
}

describe("OcDateTimePicker (typeable)", () => {
    beforeEach(async () => {
        await i18n.changeLanguage("en");
    });

    describe("bufferError resolution", () => {
        it("shows the format error for a syntactically invalid date on blur", () => {
            renderPicker();
            const input = getInput();
            setBufferTo(input, "2026-13-32 25:99");
            fireEvent.blur(input);

            expect(screen.getByText("Must be a valid date/time.")).toBeDefined();
        });

        it("shows the beforeMin default for a value before min on blur", () => {
            renderPicker();
            const input = getInput();
            setBufferTo(input, "2026-04-15 10:00"); // before MIN (2026-05-01)
            fireEvent.blur(input);

            expect(
                screen.getByText("Date must not be earlier than the allowed range."),
            ).toBeDefined();
        });

        it("shows the afterMax default for a value after max on blur", () => {
            renderPicker();
            const input = getInput();
            setBufferTo(input, "2026-06-15 10:00"); // after MAX (2026-05-31)
            fireEvent.blur(input);

            expect(
                screen.getByText("Date must not be later than the allowed range."),
            ).toBeDefined();
        });

        it("uses the errorMessages.format override in place of the default", () => {
            renderPicker({ errorMessages: { format: "Custom format error." } });
            const input = getInput();
            setBufferTo(input, "2026-13-32 25:99");
            fireEvent.blur(input);

            expect(screen.getByText("Custom format error.")).toBeDefined();
            expect(screen.queryByText("Must be a valid date/time.")).toBeNull();
        });

        it("uses the errorMessages.afterMax override in place of the default", () => {
            renderPicker({
                errorMessages: { afterMax: "Start must be before end time." },
            });
            const input = getInput();
            setBufferTo(input, "2026-06-15 10:00"); // after MAX
            fireEvent.blur(input);

            expect(screen.getByText("Start must be before end time.")).toBeDefined();
            expect(screen.queryByText("Date must not be later than the allowed range.")).toBeNull();
        });
    });

    describe("validation gating (blur-only)", () => {
        it("does not show the error while the user is still typing", () => {
            renderPicker();
            const input = getInput();
            setBufferTo(input, "2026-13-32 25:99"); // no blur

            expect(screen.queryByText("Must be a valid date/time.")).toBeNull();
            expect(input.getAttribute("aria-invalid")).toBeNull();
        });

        it("shows the error after blur", () => {
            renderPicker();
            const input = getInput();
            setBufferTo(input, "2026-13-32 25:99");
            fireEvent.blur(input);

            expect(screen.getByText("Must be a valid date/time.")).toBeDefined();
            expect(input.getAttribute("aria-invalid")).toBe("true");
        });

        it("clears the error on the next keystroke after a failed blur", () => {
            renderPicker();
            const input = getInput();
            setBufferTo(input, "2026-13-32 25:99");
            fireEvent.blur(input);
            expect(input.getAttribute("aria-invalid")).toBe("true");

            // Single mutation simulating another keystroke (buffer still
            // invalid, but the next blur will re-evaluate; for now the
            // visible-error state clears).
            fireEvent.change(input, { target: { value: "2026-13-32 25:9" } });

            expect(screen.queryByText("Must be a valid date/time.")).toBeNull();
            expect(input.getAttribute("aria-invalid")).toBeNull();
        });
    });

    describe("customError prop", () => {
        it("shows the customError immediately with a valid buffer (no blur required)", () => {
            renderPicker({ customError: "Date range must not exceed 30 days." });
            const input = getInput();

            expect(screen.getByText("Date range must not exceed 30 days.")).toBeDefined();
            expect(input.getAttribute("aria-invalid")).toBe("true");
        });

        it("takes precedence over the picker's own bufferError when both apply", () => {
            renderPicker({ customError: "Date range must not exceed 30 days." });
            const input = getInput();
            setBufferTo(input, "2026-13-32 25:99"); // would trigger format error
            fireEvent.blur(input);

            // customError wins.
            expect(screen.getByText("Date range must not exceed 30 days.")).toBeDefined();
            expect(screen.queryByText("Must be a valid date/time.")).toBeNull();
        });

        it("clears the error UI when customError transitions from set to undefined", () => {
            const { rerender } = renderPicker({
                customError: "Date range must not exceed 30 days.",
            });
            expect(screen.getByText("Date range must not exceed 30 days.")).toBeDefined();

            rerender(
                <I18nextProvider i18n={i18n}>
                    <OcThemeProvider>
                        <OcDateTimePicker
                            typeable
                            timezone="UTC"
                            min={MIN}
                            max={MAX}
                            startTime={START}
                            inputProps={{ "aria-label": "Picker" }}
                        />
                    </OcThemeProvider>
                </I18nextProvider>,
            );

            expect(screen.queryByText("Date range must not exceed 30 days.")).toBeNull();
            expect(getInput().getAttribute("aria-invalid")).toBeNull();
        });
    });

    describe("ARIA wiring", () => {
        it("links input aria-describedby to the helper element's id", () => {
            renderPicker({ customError: "External validation message." });
            const input = getInput();
            const helper = screen.getByText("External validation message.");

            expect(helper.id).toBeTruthy();
            expect(input.getAttribute("aria-describedby")).toBe(helper.id);
        });

        it("renders the helper with aria-live=polite + aria-atomic=true, and not role=alert", () => {
            renderPicker({ customError: "External validation message." });
            const helper = screen.getByText("External validation message.");

            expect(helper.getAttribute("aria-live")).toBe("polite");
            expect(helper.getAttribute("aria-atomic")).toBe("true");
            expect(helper.getAttribute("role")).toBeNull();
        });

        it("omits aria-describedby when no error is visible", () => {
            renderPicker();
            expect(getInput().getAttribute("aria-describedby")).toBeNull();
        });
    });

    describe("auto-pad on blur via commit()", () => {
        it("pads a single-digit minute slot and commits via onChange", () => {
            const onChange = vi.fn();
            renderPicker({ onChange });
            const input = getInput();

            setBufferTo(input, "2026-05-21 11:");
            fireEvent.blur(input);

            expect(input.value).toBe("2026-05-21 11:00");
            expect(onChange).toHaveBeenCalledTimes(1);
            const committed = onChange.mock.calls[0][0] as DateTime;
            expect(committed.toISO()).toBe("2026-05-21T11:00:00.000Z");
        });

        it("reverts an empty buffer to canonical on blur without firing onChange", () => {
            const onChange = vi.fn();
            renderPicker({ onChange });
            const input = getInput();

            setBufferTo(input, "");
            fireEvent.blur(input);

            // canonical for START in UTC = "2026-05-15 12:00"
            expect(input.value).toBe("2026-05-15 12:00");
            expect(onChange).not.toHaveBeenCalled();
        });
    });

    describe("external startTime change", () => {
        it("replaces a dirty buffer when startTime updates by ms only (same minute)", () => {
            const startA = DateTime.fromISO("2026-05-15T12:00:00.000Z", { zone: "utc" });
            const startB = DateTime.fromISO("2026-05-15T12:00:30.000Z", { zone: "utc" }); // same minute, +30s
            const { rerender } = renderPicker({ startTime: startA });
            const input = getInput();

            // Dirty the buffer with an invalid value (no blur — buffer just sits).
            setBufferTo(input, "2026-13-32 25:99");
            expect(input.value).toBe("2026-13-32 25:99");

            // Rerender with the new startTime — formats identically to A in
            // "yyyy-MM-dd HH:mm" but differs by 30s in ms. The sync key is
            // `date.toMillis()`, so the buffer should reset to canonical.
            rerender(
                <I18nextProvider i18n={i18n}>
                    <OcThemeProvider>
                        <OcDateTimePicker
                            typeable
                            timezone="UTC"
                            min={MIN}
                            max={MAX}
                            startTime={startB}
                            inputProps={{ "aria-label": "Picker" }}
                        />
                    </OcThemeProvider>
                </I18nextProvider>,
            );

            expect(getInput().value).toBe("2026-05-15 12:00");
        });
    });
});
