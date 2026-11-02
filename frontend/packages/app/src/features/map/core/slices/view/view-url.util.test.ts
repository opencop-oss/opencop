// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { decodeViewUrl, encodeViewUrl } from "./view-url.util";

describe("encodeViewUrl", () => {
    it("rounds lat/lng to 6 decimals", () => {
        expect(encodeViewUrl({ latitude: 12.3456789, longitude: -98.7654321 })).toEqual({
            lat: "12.345679",
            lng: "-98.765432",
        });
    });

    it("rounds zoom to 2 decimals and angles to 1", () => {
        expect(encodeViewUrl({ zoom: 4.645083, pitch: 45.678, bearing: 12.34 })).toEqual({
            z: "4.65",
            p: "45.7",
            b: "12.3",
        });
    });

    it("strips trailing zeros", () => {
        expect(encodeViewUrl({ zoom: 4, pitch: 0, bearing: 0 })).toEqual({
            z: "4",
            p: "0",
            b: "0",
        });
    });

    it("clamps out-of-range latitude and longitude", () => {
        expect(encodeViewUrl({ latitude: 100, longitude: 200 })).toEqual({
            lat: "90",
            lng: "180",
        });
        expect(encodeViewUrl({ latitude: -100, longitude: -200 })).toEqual({
            lat: "-90",
            lng: "-180",
        });
    });

    it("clamps zoom and pitch, wraps bearing to (-180, 180]", () => {
        expect(encodeViewUrl({ zoom: 99, pitch: 100, bearing: 450 })).toEqual({
            z: "22",
            p: "85",
            b: "90",
        });
        expect(encodeViewUrl({ bearing: -45 })).toEqual({ b: "-45" });
        expect(encodeViewUrl({ bearing: 200 })).toEqual({ b: "-160" });
        expect(encodeViewUrl({ bearing: 180 })).toEqual({ b: "180" });
        expect(encodeViewUrl({ bearing: -180 })).toEqual({ b: "180" });
    });

    it("omits Infinity / NaN values", () => {
        expect(encodeViewUrl({ latitude: Infinity, longitude: NaN, zoom: 5 })).toEqual({ z: "5" });
    });

    it("returns only the view fields present in the input", () => {
        expect(encodeViewUrl({})).toEqual({});
        expect(encodeViewUrl({ zoom: 5 })).toEqual({ z: "5" });
    });
});

describe("decodeViewUrl", () => {
    const params = (s: string) => new URLSearchParams(s);

    it("parses all view fields", () => {
        expect(decodeViewUrl(params("lat=12.345679&lng=-98.765432&z=4.65&p=0&b=0"))).toEqual({
            latitude: 12.345679,
            longitude: -98.765432,
            zoom: 4.65,
            pitch: 0,
            bearing: 0,
        });
    });

    it("ignores unknown params", () => {
        expect(decodeViewUrl(params("lat=40&utm_source=email&selected=ns:id"))).toEqual({
            latitude: 40,
        });
    });

    it("rounds on decode so URL and store agree", () => {
        expect(decodeViewUrl(params("lat=12.3456789"))).toEqual({ latitude: 12.345679 });
    });

    it("clamps out-of-range values", () => {
        expect(decodeViewUrl(params("lat=100&lng=200&z=99&p=100&b=450"))).toEqual({
            latitude: 90,
            longitude: 180,
            zoom: 22,
            pitch: 85,
            bearing: 90,
        });
        expect(decodeViewUrl(params("b=200"))).toEqual({ bearing: -160 });
    });

    it("drops non-numeric values silently", () => {
        expect(decodeViewUrl(params("lat=abc&lng=-74"))).toEqual({ longitude: -74 });
    });

    it("returns empty object when no view keys present", () => {
        expect(decodeViewUrl(params("selected=ns:id&utm_source=email"))).toEqual({});
    });
});
