// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { DateTime } from "luxon";
import { parseWmsTimeContent } from "./wms-capabilities.util";

describe("parseWmsTimeContent", () => {
    it("returns null for empty content", () => {
        expect(parseWmsTimeContent("", null)).toBeNull();
    });

    it("returns null when all segments are invalid", () => {
        expect(parseWmsTimeContent("not-a-date,also-bad", null)).toBeNull();
    });

    it("parses a single ISO date", () => {
        const result = parseWmsTimeContent("2024-03-15", null);
        expect(result).not.toBeNull();
        expect(result!.timeMin.toISO()).toBe(result!.timeMax.toISO());
        expect(result!.timeMin.toISODate()).toBe("2024-03-15");
        expect(result!.step).toBeNull();
        expect(result!.timeDefault).toBeNull();
    });

    it("parses a date range with step", () => {
        const result = parseWmsTimeContent("2024-01-01/2024-12-31/P1D", null);
        expect(result).not.toBeNull();
        expect(result!.timeMin.toISODate()).toBe("2024-01-01");
        expect(result!.timeMax.toISODate()).toBe("2024-12-31");
        expect(result!.step).toBe("P1D");
    });

    it("parses a range without step", () => {
        const result = parseWmsTimeContent("2020-01-01/2023-06-30", null);
        expect(result).not.toBeNull();
        expect(result!.timeMin.toISODate()).toBe("2020-01-01");
        expect(result!.timeMax.toISODate()).toBe("2023-06-30");
        expect(result!.step).toBeNull();
    });

    it("parses multiple explicit dates and returns correct min/max", () => {
        const result = parseWmsTimeContent("2023-03-01,2022-01-15,2024-06-30", null);
        expect(result).not.toBeNull();
        expect(result!.timeMin.toISODate()).toBe("2022-01-15");
        expect(result!.timeMax.toISODate()).toBe("2024-06-30");
    });

    it("parses mixed ranges and single dates", () => {
        const result = parseWmsTimeContent("2020-01-01/2021-01-01/P1M,2024-01-01", null);
        expect(result).not.toBeNull();
        expect(result!.timeMin.toISODate()).toBe("2020-01-01");
        expect(result!.timeMax.toISODate()).toBe("2024-01-01");
        expect(result!.step).toBe("P1M");
    });

    it("sets timeDefault when provided", () => {
        const result = parseWmsTimeContent("2024-01-01/2024-12-31/P1D", "2024-06-15");
        expect(result).not.toBeNull();
        expect(result!.timeDefault).toBeInstanceOf(DateTime);
        expect(result!.timeDefault!.toISODate()).toBe("2024-06-15");
    });

    it("sets timeDefault to null when defaultVal is invalid", () => {
        const result = parseWmsTimeContent("2024-01-01/2024-12-31/P1D", "not-a-date");
        expect(result).not.toBeNull();
        expect(result!.timeDefault).toBeNull();
    });

    it("returns DateTime in UTC zone", () => {
        const result = parseWmsTimeContent("2024-03-15T12:00:00Z", null);
        expect(result).not.toBeNull();
        expect(result!.timeMin.zoneName).toBe("UTC");
    });
});
