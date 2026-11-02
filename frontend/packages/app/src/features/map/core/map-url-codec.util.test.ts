// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { MAP_URL_KEYS, decodeMapUrl, encodeMapUrl } from "./map-url-codec.util";

// Per-slice encoder/decoder behavior is covered by:
//   slices/view/view-url.util.test.ts
//   slices/selection/selection-url.util.test.ts
// This file covers the composition: view + selection are spread together
// correctly, the full key set is exported, and round-trips are idempotent.

describe("MAP_URL_KEYS", () => {
    it("composes view and selection keys", () => {
        expect(MAP_URL_KEYS).toEqual(["lat", "lng", "z", "p", "b", "selected"]);
    });
});

describe("encodeMapUrl composition", () => {
    it("spreads view and selection encoders", () => {
        expect(
            encodeMapUrl({
                latitude: 40,
                longitude: -74,
                zoom: 5,
                pitch: 0,
                bearing: 0,
                selected: "ns:id",
            }),
        ).toEqual({
            lat: "40",
            lng: "-74",
            z: "5",
            p: "0",
            b: "0",
            selected: "ns:id",
        });
    });

    it("returns only fields present in the input from each slice", () => {
        expect(encodeMapUrl({ latitude: 40 })).toEqual({ lat: "40" });
        expect(encodeMapUrl({ selected: "ns:id" })).toEqual({ selected: "ns:id" });
        expect(encodeMapUrl({})).toEqual({});
    });
});

describe("decodeMapUrl composition", () => {
    const params = (s: string) => new URLSearchParams(s);

    it("merges view and selection decoders", () => {
        expect(
            decodeMapUrl(params("lat=12.345679&lng=-98.765432&z=4.65&p=0&b=0&selected=ns:id")),
        ).toEqual({
            latitude: 12.345679,
            longitude: -98.765432,
            zoom: 4.65,
            pitch: 0,
            bearing: 0,
            selected: "ns:id",
        });
    });

    it("returns an empty object when no codec keys are present", () => {
        expect(decodeMapUrl(params("utm_source=email&campaign=test"))).toEqual({});
    });
});

describe("encode/decode round-trip", () => {
    it("is stable", () => {
        const initial = {
            latitude: 12.345679,
            longitude: -98.765432,
            zoom: 4.65,
            pitch: 0,
            bearing: 0,
            selected: "ns:id",
        };
        const encoded = encodeMapUrl(initial);
        const search = new URLSearchParams();
        for (const [k, v] of Object.entries(encoded)) {
            if (v !== null && v !== undefined) search.set(k, v);
        }
        expect(decodeMapUrl(search)).toEqual(initial);
    });
});
