// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * EXEMPLAR — component test. Copy this shape for a component that renders
 * through theme + i18n. One import from `~/test`; no `vi.mock` of Chakra,
 * i18next, or the API client; query the real ARIA tree and real English text.
 */
import { render, screen } from "~/test";
import { describe, expect, it } from "vitest";
import { Timestamps } from "~/features/events/detail/Timestamps";

describe("Timestamps (harness exemplar)", () => {
    it("renders time and stale rows through real Chakra + i18n", () => {
        render(<Timestamps timestampz={{ time: 2_000, start: 1_000, stale: 3_000 }} />);

        // Real translations resolve, so we assert on user-visible labels — not keys.
        expect(screen.getByText("TIME")).toBeInTheDocument();
        expect(screen.getByText("STALE")).toBeInTheDocument();
    });
});
