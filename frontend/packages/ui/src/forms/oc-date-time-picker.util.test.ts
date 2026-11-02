// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { DateTime } from "luxon";
import { describe, expect, it } from "vitest";
import {
    TYPEABLE_FORMAT,
    incrementTypeableSlot,
    maskTypeableValue,
    padTypeableValue,
    parseTypeablePaste,
    slotFromCursor,
} from "./oc-date-time-picker.util";

describe("maskTypeableValue", () => {
    it("returns empty for empty input", () => {
        expect(maskTypeableValue("")).toBe("");
    });

    it("returns raw digits below 5 characters (year-in-progress)", () => {
        expect(maskTypeableValue("2")).toBe("2");
        expect(maskTypeableValue("2026")).toBe("2026");
    });

    it("inserts year-month separator at 5 digits", () => {
        expect(maskTypeableValue("20260")).toBe("2026-0");
        expect(maskTypeableValue("202605")).toBe("2026-05");
    });

    it("inserts month-day separator at 7 digits", () => {
        expect(maskTypeableValue("2026052")).toBe("2026-05-2");
        expect(maskTypeableValue("20260520")).toBe("2026-05-20");
    });

    it("inserts date-time space at 9 digits", () => {
        expect(maskTypeableValue("202605201")).toBe("2026-05-20 1");
        expect(maskTypeableValue("2026052014")).toBe("2026-05-20 14");
    });

    it("inserts hour-minute colon at 11 digits and completes at 12", () => {
        expect(maskTypeableValue("20260520143")).toBe("2026-05-20 14:3");
        expect(maskTypeableValue("202605201430")).toBe("2026-05-20 14:30");
    });

    it("strips non-digit characters before applying mask", () => {
        expect(maskTypeableValue("2026/05/20 14:30")).toBe("2026-05-20 14:30");
        expect(maskTypeableValue("2026 05 20 14 30")).toBe("2026-05-20 14:30");
        expect(maskTypeableValue("abc2026def")).toBe("2026");
    });

    it("truncates beyond 12 digits", () => {
        expect(maskTypeableValue("20260520143000999")).toBe("2026-05-20 14:30");
    });
});

describe("padTypeableValue", () => {
    it("returns null when fewer than 4 digits are present", () => {
        expect(padTypeableValue("")).toBeNull();
        expect(padTypeableValue("202")).toBeNull();
        expect(padTypeableValue("---")).toBeNull();
    });

    it("pads month, day, hour, minute defaults from a year-only buffer", () => {
        expect(padTypeableValue("2026")).toBe("2026-01-01 00:00");
    });

    it("pads day, hour, minute when only year-month present", () => {
        expect(padTypeableValue("2026-05")).toBe("2026-05-01 00:00");
    });

    it("pads hour and minute when date present without time", () => {
        expect(padTypeableValue("2026-05-21")).toBe("2026-05-21 00:00");
    });

    it("pads minute when only hour is present after the space", () => {
        expect(padTypeableValue("2026-05-21 11:")).toBe("2026-05-21 11:00");
        expect(padTypeableValue("2026-05-21 11")).toBe("2026-05-21 11:00");
    });

    it("left-pads a single-digit slice from an odd-length digit run", () => {
        // 5 digits → year + 1-char month slice → padded to "05"
        expect(padTypeableValue("20265")).toBe("2026-05-01 00:00");
        // 7 digits → year + month + 1-char day slice → padded to "02"
        expect(padTypeableValue("2026052")).toBe("2026-05-02 00:00");
    });

    it("leaves a fully-populated buffer unchanged", () => {
        expect(padTypeableValue("2026-05-21 11:30")).toBe("2026-05-21 11:30");
    });
});

describe("slotFromCursor", () => {
    // yyyy-MM-dd HH:mm
    // 0123456789012345
    it.each([
        [0, "years"],
        [4, "years"],
        [5, "months"],
        [7, "months"],
        [8, "days"],
        [10, "days"],
        [11, "hours"],
        [13, "hours"],
        [14, "minutes"],
        [16, "minutes"],
    ])("cursor=%i → %s", (cursor, expected) => {
        expect(slotFromCursor(cursor)).toBe(expected);
    });
});

describe("parseTypeablePaste", () => {
    const zone = "UTC";

    it("parses the canonical TYPEABLE_FORMAT exactly", () => {
        expect(parseTypeablePaste("2026-05-20 14:30", zone)).toBe("2026-05-20 14:30");
    });

    it("parses ISO-T form", () => {
        expect(parseTypeablePaste("2026-05-20T14:30", zone)).toBe("2026-05-20 14:30");
    });

    it("parses ISO-T with seconds and truncates", () => {
        expect(parseTypeablePaste("2026-05-20T14:30:45", zone)).toBe("2026-05-20 14:30");
    });

    it("parses US 12-hour clock with AM/PM", () => {
        expect(parseTypeablePaste("5/20/2026 2:30 PM", zone)).toBe("2026-05-20 14:30");
        expect(parseTypeablePaste("5/20/2026 2:30 AM", zone)).toBe("2026-05-20 02:30");
    });

    it("parses US 24-hour clock", () => {
        expect(parseTypeablePaste("5/20/2026 14:30", zone)).toBe("2026-05-20 14:30");
    });

    it("trims surrounding whitespace", () => {
        expect(parseTypeablePaste("  2026-05-20 14:30  ", zone)).toBe("2026-05-20 14:30");
    });

    it("returns null when none of the candidate formats match", () => {
        expect(parseTypeablePaste("20260520143000", zone)).toBeNull(); // digit blob — falls through to mask
        expect(parseTypeablePaste("definitely not a date", zone)).toBeNull();
        expect(parseTypeablePaste("", zone)).toBeNull();
    });
});

describe("incrementTypeableSlot", () => {
    const zone = "UTC";
    const buffer = "2026-05-20 14:30";
    const min = DateTime.fromFormat("2000-01-01 00:00", TYPEABLE_FORMAT, { zone });
    const max = DateTime.fromFormat("2099-12-31 23:59", TYPEABLE_FORMAT, { zone });

    it("returns null when the buffer is unparseable", () => {
        expect(incrementTypeableSlot("incomplete", 0, 1, min, max, zone)).toBeNull();
        expect(incrementTypeableSlot("", 0, 1, min, max, zone)).toBeNull();
    });

    it("increments the year when cursor is in the year slot", () => {
        expect(incrementTypeableSlot(buffer, 2, 1, min, max, zone)).toBe("2027-05-20 14:30");
        expect(incrementTypeableSlot(buffer, 2, -1, min, max, zone)).toBe("2025-05-20 14:30");
    });

    it("increments the month when cursor is in the month slot", () => {
        expect(incrementTypeableSlot(buffer, 6, 1, min, max, zone)).toBe("2026-06-20 14:30");
        expect(incrementTypeableSlot(buffer, 6, -1, min, max, zone)).toBe("2026-04-20 14:30");
    });

    it("increments the day when cursor is in the day slot", () => {
        expect(incrementTypeableSlot(buffer, 9, 1, min, max, zone)).toBe("2026-05-21 14:30");
    });

    it("increments the hour when cursor is in the hour slot", () => {
        expect(incrementTypeableSlot(buffer, 12, 1, min, max, zone)).toBe("2026-05-20 15:30");
    });

    it("increments the minute when cursor is in the minute slot", () => {
        expect(incrementTypeableSlot(buffer, 15, 1, min, max, zone)).toBe("2026-05-20 14:31");
    });

    it("clamps to min on under-shoot", () => {
        const tightMin = DateTime.fromFormat("2026-05-20 14:30", TYPEABLE_FORMAT, { zone });
        expect(incrementTypeableSlot(buffer, 15, -1, tightMin, max, zone)).toBe("2026-05-20 14:30");
    });

    it("clamps to max on over-shoot", () => {
        const tightMax = DateTime.fromFormat("2026-05-20 14:30", TYPEABLE_FORMAT, { zone });
        expect(incrementTypeableSlot(buffer, 15, 1, min, tightMax, zone)).toBe("2026-05-20 14:30");
    });
});
