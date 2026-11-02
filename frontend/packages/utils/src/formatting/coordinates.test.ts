// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { formatCoord, parseCoordinate, toUTM } from "./coordinates";

// Published UTM reference coordinates (verified against online converters).
// lat/lng are WGS84; utm is the expected toUTM() output.
const REFERENCE_POINTS = [
    {
        name: "New York City",
        lat: 40.7128,
        lng: -74.006,
        utm: "18T 583959E 4507351N",
    },
    {
        name: "London",
        lat: 51.5074,
        lng: -0.1278,
        utm: "30U 699316E 5710164N",
    },
    {
        name: "Sydney",
        lat: -33.8688,
        lng: 151.2093,
        utm: "56H 334369E 6250948N",
    },
    {
        name: "Rio de Janeiro",
        lat: -22.9068,
        lng: -43.1729,
        utm: "23K 687395E 7465634N",
    },
    {
        name: "Cape Town",
        lat: -33.9249,
        lng: 18.4241,
        utm: "34H 261882E 6243182N",
    },
];

describe("toUTM", () => {
    it.each(REFERENCE_POINTS)("formats $name correctly", ({ lat, lng, utm }) => {
        expect(toUTM(lat, lng)).toBe(utm);
    });

    it("returns — for latitudes above 84°N (polar region)", () => {
        expect(toUTM(85, 0)).toBe("—");
    });

    it("returns — for latitudes below 80°S (polar region)", () => {
        expect(toUTM(-81, 0)).toBe("—");
    });

    it("handles longitude 180 without producing zone 61", () => {
        const result = toUTM(0, 180);
        expect(result).not.toMatch(/^61/);
        expect(result).toMatch(/^\d{1,2}[A-Z]/);
    });

    it("handles antimeridian crossing (lng = -180 and lng = 180 are the same zone)", () => {
        const west = toUTM(0, -180);
        const east = toUTM(0, 180);
        // Both should land in the same zone (zone 1)
        expect(west.split(" ")[0]).toBe(east.split(" ")[0]);
    });
});

describe("parseUTM", () => {
    it.each(REFERENCE_POINTS)("round-trips $name within 1e-4°", ({ lat, lng }) => {
        const utmStr = toUTM(lat, lng);
        const result = parseCoordinate(utmStr);
        expect(result).not.toBeNull();
        expect(Math.abs(result!.lat - lat)).toBeLessThan(1e-4);
        expect(Math.abs(result!.lng - lng)).toBeLessThan(1e-4);
    });

    it("accepts lowercase band letter", () => {
        // "18t" instead of "18T"
        const result = parseCoordinate("18t 583961E 4507521N");
        expect(result).not.toBeNull();
    });

    it("accepts UTM without E/N suffixes", () => {
        const withSuffix = parseCoordinate("18T 583961E 4507521N");
        const withoutSuffix = parseCoordinate("18T 583961 4507521");
        expect(withSuffix).not.toBeNull();
        expect(withoutSuffix).not.toBeNull();
        expect(withSuffix!.lat).toBeCloseTo(withoutSuffix!.lat, 4);
        expect(withSuffix!.lng).toBeCloseTo(withoutSuffix!.lng, 4);
    });

    it("accepts UTM with no spaces", () => {
        const result = parseCoordinate("18T583961E4507521N");
        expect(result).not.toBeNull();
    });

    it("returns null for out-of-range zone", () => {
        expect(parseCoordinate("61T 583961E 4507521N")).toBeNull();
        expect(parseCoordinate("0T 583961E 4507521N")).toBeNull();
    });

    it("returns null for easting out of soft bounds", () => {
        expect(parseCoordinate("18T 99999E 4507521N")).toBeNull();
        expect(parseCoordinate("18T 900001E 4507521N")).toBeNull();
    });

    it("returns null for invalid band letter (I and O are excluded)", () => {
        expect(parseCoordinate("18I 583961E 4507521N")).toBeNull();
        expect(parseCoordinate("18O 583961E 4507521N")).toBeNull();
    });

    it("does not collide with MGRS parsing", () => {
        // MGRS strings include two extra grid-square letters after the band — parseUTM should not match
        const mgrsResult = parseCoordinate("18SUJ2337106519");
        expect(mgrsResult).not.toBeNull();
    });
});

describe("parseDMS — extended variants", () => {
    it("parses curly single/double quote (U+2019/U+201D)", () => {
        // Smart quotes pasted from word processors or messaging apps.
        // ’ = RIGHT SINGLE QUOTATION MARK, ” = RIGHT DOUBLE QUOTATION MARK
        const result = parseCoordinate("25°03’02”N 121°31’07”E");
        expect(result).not.toBeNull();
        expect(result!.lat).toBeCloseTo(25.0506, 3);
        expect(result!.lng).toBeCloseTo(121.519, 3);
    });

    it("parses condensed military DMS with space separator", () => {
        const result = parseCoordinate("250302N 1213107E");
        expect(result).not.toBeNull();
        expect(result!.lat).toBeCloseTo(25.0506, 3);
        expect(result!.lng).toBeCloseTo(121.519, 3);
    });

    it("parses condensed military DMS with no separator", () => {
        const result = parseCoordinate("250302N1213107E");
        expect(result).not.toBeNull();
        expect(result!.lat).toBeCloseTo(25.0506, 3);
        expect(result!.lng).toBeCloseTo(121.519, 3);
    });

    it("rejects condensed DMS with invalid minutes (>=60)", () => {
        expect(parseCoordinate("256102N 1213107E")).toBeNull();
    });

    it("parses space between seconds symbol and cardinal direction", () => {
        expect(parseCoordinate("25°03′02″ N 121°31′07″ E")).not.toBeNull();
    });

    it("parses lowercase cardinal directions", () => {
        expect(parseCoordinate("25°03'02\"n, 121°31'07\"e")).not.toBeNull();
    });

    it("parses fully space-delimited DMS", () => {
        expect(parseCoordinate("25 03 02 N 121 31 07 E")).not.toBeNull();
    });

    it("returns null for missing cardinal direction", () => {
        expect(parseCoordinate("25°03'02\", 121°31'07\"")).toBeNull();
    });

    it("returns null for partial coordinate (latitude only)", () => {
        expect(parseCoordinate("25°03'02\"N")).toBeNull();
    });
});

describe("parseDMS with Unicode symbols", () => {
    it("parses Unicode prime/double-prime DMS (issue example)", () => {
        const result = parseCoordinate("25°03′02″N 121°31′07″E");
        expect(result).not.toBeNull();
        expect(result!.lat).toBeCloseTo(25.0506, 3);
        expect(result!.lng).toBeCloseTo(121.519, 3);
    });

    it("parses ASCII apostrophe/quote DMS (existing working example)", () => {
        const result = parseCoordinate("25°2'51.94\"N, 121°31'54.66\"E");
        expect(result).not.toBeNull();
    });

    it("parses mixed Unicode/ASCII separators", () => {
        const result = parseCoordinate('25°03′02"N 121°31′07″E');
        expect(result).not.toBeNull();
    });

    it("rejects DMS with invalid minutes", () => {
        expect(parseCoordinate("25°61′02″N 121°31′07″E")).toBeNull();
    });

    it("rejects DMS with invalid seconds", () => {
        expect(parseCoordinate("25°03'61\"N 121°31'07\"E")).toBeNull();
    });
});

describe("formatCoord with UTM format", () => {
    it("routes through toUTM when format is UTM", () => {
        expect(formatCoord(40.7128, -74.006, "UTM")).toBe(toUTM(40.7128, -74.006));
    });
});
