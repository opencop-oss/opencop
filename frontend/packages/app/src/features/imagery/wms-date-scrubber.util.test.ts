// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { DateTime } from "luxon";
import { describe, expect, it } from "vitest";
import { buildScrubberDates, findSliderValue } from "./wms-date-scrubber.util";

// Use noon local time so startOf("day") never shifts across midnight in non-UTC zones.
function d(isoDate: string): DateTime {
    return DateTime.fromISO(`${isoDate}T12:00:00`);
}

describe("buildScrubberDates", () => {
    it("returns empty array for an invalid step string", () => {
        expect(buildScrubberDates(d("2024-01-01"), d("2024-12-31"), "not-a-duration")).toEqual([]);
    });

    it("steps daily within the range and includes both endpoints", () => {
        const dates = buildScrubberDates(d("2024-01-01"), d("2024-01-05"), "P1D");
        expect(dates).toEqual([
            "2024-01-01",
            "2024-01-02",
            "2024-01-03",
            "2024-01-04",
            "2024-01-05",
        ]);
    });

    it("steps monthly and truncates to start of day", () => {
        const dates = buildScrubberDates(d("2024-01-15"), d("2024-04-15"), "P1M");
        expect(dates).toEqual(["2024-01-15", "2024-02-15", "2024-03-15", "2024-04-15"]);
    });

    it("returns all natural steps when they fit within maxSteps", () => {
        // 3 days of P1D = 3 natural steps, cap 5 → range exhausted, no sampling needed
        const dates = buildScrubberDates(d("2024-01-01"), d("2024-01-03"), "P1D", 5);
        expect(dates).toEqual(["2024-01-01", "2024-01-02", "2024-01-03"]);
    });

    it("samples evenly when natural steps exceed maxSteps, spanning the full range", () => {
        // 10 days of P1D with cap 5: natural fill stops at Jan 5 but more steps remain,
        // so the function samples 5 dates across the full Jan 1–10 span.
        const dates = buildScrubberDates(d("2024-01-01"), d("2024-01-10"), "P1D", 5);
        expect(dates).toHaveLength(5);
        expect(dates[0]).toBe("2024-01-01");
        expect(dates[4]).toBe("2024-01-10");
    });

    it("samples evenly when natural steps exceed maxSteps", () => {
        // 365 daily steps, cap at 5 — falls back to even sampling
        const dates = buildScrubberDates(d("2024-01-01"), d("2024-12-31"), "P1D", 5);
        expect(dates).toHaveLength(5);
        expect(dates[0]).toBe("2024-01-01");
        expect(dates[4]).toBe("2024-12-31");
    });

    it("deduplicates dates when even sampling lands on the same calendar day", () => {
        // Very short range with many max steps — fractional samples collapse to the same ISO date
        const dates = buildScrubberDates(d("2024-01-01"), d("2024-01-02"), "P1D", 10);
        expect(dates).toEqual([...new Set(dates)]);
    });

    it("returns a single-element array when min equals max", () => {
        const dates = buildScrubberDates(d("2024-06-15"), d("2024-06-15"), "P1D");
        expect(dates).toEqual(["2024-06-15"]);
    });

    it("normalizes timeMin and timeMax to start of day", () => {
        const min = DateTime.fromISO("2024-03-01T12:30:00");
        const max = DateTime.fromISO("2024-03-03T23:59:59");
        const dates = buildScrubberDates(min, max, "P1D");
        expect(dates[0]).toBe("2024-03-01");
        expect(dates[dates.length - 1]).toBe("2024-03-03");
    });
});

describe("findSliderValue", () => {
    it("returns 0 for an empty dates array", () => {
        expect(findSliderValue([], null)).toBe(0);
        expect(findSliderValue([], "2024-01-01")).toBe(0);
    });

    it("returns the last index when selectedDate is null", () => {
        const dates = ["2024-01-01", "2024-01-02", "2024-01-03"];
        expect(findSliderValue(dates, null)).toBe(2);
    });

    it("returns the exact index when the date is in the array", () => {
        const dates = ["2024-01-01", "2024-01-02", "2024-01-03"];
        expect(findSliderValue(dates, "2024-01-02")).toBe(1);
    });

    it("returns the insertion point when the date falls between steps", () => {
        const dates = ["2024-01-01", "2024-01-05", "2024-01-10"];
        // "2024-01-03" inserts at index 1 (between Jan 1 and Jan 5)
        expect(findSliderValue(dates, "2024-01-03")).toBe(1);
    });

    it("clamps to the last index when the date is after all steps", () => {
        const dates = ["2024-01-01", "2024-01-05", "2024-01-10"];
        expect(findSliderValue(dates, "2024-12-31")).toBe(2);
    });

    it("returns 0 when the date is before all steps", () => {
        const dates = ["2024-06-01", "2024-07-01"];
        expect(findSliderValue(dates, "2024-01-01")).toBe(0);
    });
});
