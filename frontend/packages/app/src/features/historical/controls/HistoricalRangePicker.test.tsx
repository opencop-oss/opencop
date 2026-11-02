// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { beforeEach, describe, expect, it, vi } from "vitest";
import { DateTime } from "luxon";
import { fireEvent, render, screen, waitFor } from "~/test";
import { i18n } from "~/i18n/config";
import { preferencesQueries } from "~/features/preferences/preferences.queries";
import { MAX_HISTORICAL_RANGE_DAYS, useHistoricalStore } from "../data-plane/historical.store";
import { HistoricalRangePicker } from "./HistoricalRangePicker";

// The picker formats in the user's local TZ (via `useUserTimezone()`).
// We can't pass a `timezone` to HistoricalRangePicker, so derive expected
// strings from the store ISO at the same TZ — this keeps assertions
// stable across CI runners regardless of the host TZ.
const STORE_FROM_ISO = "2026-05-15T12:00:00.000Z";
const STORE_TO_ISO = "2026-05-16T12:00:00.000Z";
const TYPEABLE_FORMAT = "yyyy-MM-dd HH:mm";
const EXPECTED_START_CANONICAL = DateTime.fromISO(STORE_FROM_ISO, {
    zone: "UTC",
}).toFormat(TYPEABLE_FORMAT);

// Known seed: range well within the 30-day cap. Tests opt in to the
// over-cap state by typing values that push the span past
// MAX_HISTORICAL_RANGE_DAYS.
function resetStore() {
    useHistoricalStore.setState({
        mode: "historical",
        sourceIds: ["src-A"],
        from: STORE_FROM_ISO,
        to: STORE_TO_ISO,
        rangeMode: "24h",
        intervalMode: "auto",
        activated: true,
        zoomStack: [],
        playheadPosition: null,
        panelHeight: 100,
        retryNonce: 0,
        dismissedSignature: null,
        showActiveEvents: true,
        showActiveEventUpdates: false,
        settingsOpen: false,
    });
}

function renderRangePicker() {
    // Seed a timezone into the query cache so `useUserTimezone()` resolves
    // synchronously and never depends on network behavior.
    return render(<HistoricalRangePicker />, {
        queryData: {
            [JSON.stringify(preferencesQueries.current().queryKey)]: {
                timezone: "UTC",
            },
        },
    });
}

function setBufferTo(input: HTMLInputElement, value: string) {
    fireEvent.change(input, { target: { value: "" } });
    fireEvent.change(input, { target: { value } });
}

async function openPopover() {
    const trigger = screen.getByLabelText("Select date range");
    fireEvent.click(trigger);
    // Zag/Ark's popover state machine updates async; wait for the
    // typeable inputs to mount inside Popover.Content.
    await screen.findByRole("textbox", { name: "Start" });
}

async function closePopoverByEscape() {
    // Popover.Root closeOnEscape default = true. Dispatch on document so
    // the popover's global key handler fires regardless of focus target.
    fireEvent.keyDown(document, { key: "Escape" });
    // unmountOnExit removes the inputs — wait for that to settle.
    await waitFor(() => {
        expect(screen.queryByRole("textbox", { name: "Start" })).toBeNull();
    });
}

function getStartInput(): HTMLInputElement {
    return screen.getByRole("textbox", { name: "Start" }) as HTMLInputElement;
}

function getEndInput(): HTMLInputElement {
    return screen.getByRole("textbox", { name: "End" }) as HTMLInputElement;
}

// Each test mounts the full OcThemeProvider and drives the Zag/Ark popover state
// machine (async open + findByRole polling), so these run ~4-5s and tip past the
// 5s default under parallel CI load. Bump the per-test timeout for this file.
vi.setConfig({ testTimeout: 15_000 });

describe("HistoricalRangePicker", () => {
    beforeEach(async () => {
        await i18n.changeLanguage("en");
        resetStore();
    });

    describe("range-cap routing follows lastEdited", () => {
        it("shows no rangeMaxDays helper on initial open with a good range", async () => {
            renderRangePicker();
            await openPopover();

            expect(getStartInput().getAttribute("aria-invalid")).toBeNull();
            expect(getEndInput().getAttribute("aria-invalid")).toBeNull();
            expect(screen.queryByText(/Date range must not exceed/i)).toBeNull();
        });

        it("routes the error to Start when Start is the last-edited input", async () => {
            renderRangePicker();
            await openPopover();

            // Push Start back 60 days from End (well past the 30-day cap).
            setBufferTo(getStartInput(), "2026-03-16 12:00");
            fireEvent.blur(getStartInput());

            expect(getStartInput().getAttribute("aria-invalid")).toBe("true");
            expect(getEndInput().getAttribute("aria-invalid")).toBeNull();

            // Exactly one rangeMaxDays helper text in the popover — under Start.
            const helpers = screen.getAllByText(/Date range must not exceed/i);
            // Status bar also carries the headline ("Range exceeds N days") but
            // the helper-text body string only matches the picker-level message.
            expect(helpers.length).toBe(1);
        });

        it("moves the error from Start to End when End becomes the last-edited input", async () => {
            renderRangePicker();
            await openPopover();

            // First: edit Start to violate the cap.
            setBufferTo(getStartInput(), "2026-03-16 12:00");
            fireEvent.blur(getStartInput());
            expect(getStartInput().getAttribute("aria-invalid")).toBe("true");

            // Then: edit End to a value that keeps the range over-cap. This
            // makes End the most-recently-edited, so the error moves there.
            setBufferTo(getEndInput(), "2026-05-15 14:00");
            fireEvent.blur(getEndInput());

            expect(getStartInput().getAttribute("aria-invalid")).toBeNull();
            expect(getEndInput().getAttribute("aria-invalid")).toBe("true");
        });
    });

    describe("unmountOnExit resets the buffer on reopen", () => {
        it("discards an invalid in-progress buffer when the popover closes", async () => {
            renderRangePicker();
            await openPopover();

            // Dirty the buffer with an invalid value, do NOT blur.
            setBufferTo(getStartInput(), "2026-00-14 17:05");
            expect(getStartInput().value).toBe("2026-00-14 17:05");

            await closePopoverByEscape();
            // The popover's `unmountOnExit` removes the inputs from the DOM.
            expect(screen.queryByRole("textbox", { name: "Start" })).toBeNull();

            // Reopen — fresh mount, buffer initialized from canonical (store).
            await openPopover();
            expect(getStartInput().value).toBe(EXPECTED_START_CANONICAL);
            expect(getStartInput().getAttribute("aria-invalid")).toBeNull();
            expect(screen.queryByText("Must be a valid date/time.")).toBeNull();
        });
    });

    describe("rangeMaxDaysMessage interpolates the constant", () => {
        it("contains MAX_HISTORICAL_RANGE_DAYS in the helper text", async () => {
            renderRangePicker();
            await openPopover();

            setBufferTo(getStartInput(), "2026-03-16 12:00");
            fireEvent.blur(getStartInput());

            const helper = screen.getByText(/Date range must not exceed/i);
            expect(helper.textContent).toBe(
                `Date range must not exceed ${MAX_HISTORICAL_RANGE_DAYS} days.`,
            );
        });
    });
});
