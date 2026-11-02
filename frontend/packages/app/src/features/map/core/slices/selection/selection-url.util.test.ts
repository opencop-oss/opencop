// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { decodeSelectionUrl, encodeSelectionUrl } from "./selection-url.util";

describe("encodeSelectionUrl", () => {
    it("includes selected when non-empty", () => {
        expect(encodeSelectionUrl({ selected: "ns:id" })).toEqual({ selected: "ns:id" });
    });

    it("returns null for empty or null selected (storage adapter deletes the URL param)", () => {
        expect(encodeSelectionUrl({ selected: null })).toEqual({ selected: null });
        expect(encodeSelectionUrl({ selected: "" })).toEqual({ selected: null });
    });

    it("returns empty object when selected is absent (storage adapter leaves URL alone)", () => {
        expect(encodeSelectionUrl({})).toEqual({});
    });
});

describe("decodeSelectionUrl", () => {
    const params = (s: string) => new URLSearchParams(s);

    it("parses selected when present", () => {
        expect(decodeSelectionUrl(params("selected=ns:id"))).toEqual({ selected: "ns:id" });
    });

    it("treats empty selected as absent", () => {
        expect(decodeSelectionUrl(params("selected="))).toEqual({});
    });

    it("ignores unknown params", () => {
        expect(decodeSelectionUrl(params("lat=40&selected=ns:id"))).toEqual({ selected: "ns:id" });
    });

    it("returns empty object when selected is absent", () => {
        expect(decodeSelectionUrl(params("lat=40"))).toEqual({});
    });
});
