// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { CACHED_IMAGE_LIMIT, cachedImage } from "./cached-image";

describe("cachedImage", () => {
    it("returns one image per url, loading that url", () => {
        const first = cachedImage("https://example.test/a.png");

        expect(cachedImage("https://example.test/a.png")).toBe(first);
        expect(first.src).toBe("https://example.test/a.png");
        expect(cachedImage("https://example.test/b.png")).not.toBe(first);
    });

    it("drops the least recently used image past its limit", () => {
        const url = (name: string) => `https://example.test/${name}.png`;
        // Fill the cache so the case below starts from a known order.
        for (let i = 0; i < CACHED_IMAGE_LIMIT; i++) cachedImage(url(`flush-${i}`));
        const kept = cachedImage(url("kept"));
        const dropped = cachedImage(url("dropped"));
        cachedImage(url("kept")); // used again: now the most recent

        // Enough new images to push out everything older than `dropped`, and it.
        for (let i = 0; i < CACHED_IMAGE_LIMIT - 1; i++) cachedImage(url(`new-${i}`));

        expect(cachedImage(url("kept"))).toBe(kept);
        expect(cachedImage(url("dropped"))).not.toBe(dropped);
    });
});
