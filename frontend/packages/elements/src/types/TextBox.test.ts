// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { TextBox } from "./TextBox";

describe("TextBox.uprightPositions", () => {
    it("starts a ring whose first edge runs west at the opposite edge", () => {
        expect(TextBox.uprightPositions([0.02, 0.01, 0, 0.01, 0, 0, 0.02, 0])).toEqual([
            0, 0, 0.02, 0, 0.02, 0.01, 0, 0.01,
        ]);
    });

    it("drops a closing vertex rather than leaving it at the old start", () => {
        expect(
            TextBox.uprightPositions([0.02, 0.01, 0, 0.01, 0, 0, 0.02, 0, 0.02, 0.01]),
        ).toHaveLength(8);
    });

    it("leaves a ring whose first edge runs east alone", () => {
        const ring = [0, 0, 0.02, 0, 0.02, 0.01, 0, 0.01];
        expect(TextBox.uprightPositions(ring)).toEqual(ring);
    });
});
