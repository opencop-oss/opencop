// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from "~/test";
import { describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import { Timestamps } from "./Timestamps";

// Real Chakra and real i18n come from the harness. `useUserTimezone` and the
// DetailTable renderer stay stubbed: the timezone stub keeps date formatting
// deterministic, and the DetailTable stub exposes stable row test ids so the
// ordering assertions below stay precise (both are narrow internal stubs, not
// framework/API mocks).
vi.mock("~/features/preferences/use-user-timezone.hook", () => ({
    useUserTimezone: () => "UTC",
}));

// Partial mock: keep the rest of @opencop/ui real (the harness's ThemeProvider
// uses its ColorModeProvider) and override only DetailTable to expose stable row
// test ids for the ordering assertions.
vi.mock("@opencop/ui", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@opencop/ui")>()),
    DetailTable: ({ rows }: { rows: Array<{ key: string; label: string; value?: ReactNode }> }) => (
        <div>
            {rows.map((row) => (
                <div key={row.key} data-testid="timestamp-row">
                    {row.label}
                    {row.value}
                </div>
            ))}
        </div>
    ),
}));

describe("Timestamps", () => {
    it("renders time and stale in a stable order", () => {
        render(
            <Timestamps
                timestampz={{
                    time: 2_000,
                    start: 1_000,
                    stale: 3_000,
                }}
            />,
        );

        const rows = screen.getAllByTestId("timestamp-row").map((row) => row.textContent ?? "");
        expect(rows).toHaveLength(2);
        expect(rows[0]).toContain("TIME");
        expect(rows[1]).toContain("STALE");
    });

    it("renders translated fallback for missing timestamps", () => {
        render(
            <Timestamps
                timestampz={{
                    time: null,
                    start: undefined,
                    stale: 3_000,
                }}
            />,
        );

        const rows = screen.getAllByTestId("timestamp-row").map((row) => row.textContent);
        expect(rows).toHaveLength(2);
        expect(rows[0]).toContain("TIME");
        // Real i18n resolves events.notApplicable → "N/A" (was the raw key under the
        // old react-i18next mock).
        expect(rows[0]).toContain("N/A");
        expect(rows[1]).toContain("STALE");
        expect(rows[1]).not.toContain("N/A");
    });
});
