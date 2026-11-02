// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { defaultStyle } from "./map.default";

describe("defaultStyle", () => {
    it("is a project-owned, network-free dark background style", () => {
        expect(defaultStyle).toEqual({
            version: 8,
            sources: {},
            layers: [
                {
                    id: "opencop-background",
                    type: "background",
                    paint: { "background-color": "#1f2530" },
                },
            ],
        });
        expect(JSON.stringify(defaultStyle)).not.toMatch(/https?:|carto|glyph|sprite|import/i);
    });
});
