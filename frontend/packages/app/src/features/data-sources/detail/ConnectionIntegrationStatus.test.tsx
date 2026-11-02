// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { render, screen } from "~/test";
import { ConnectionIntegrationValue } from "./ConnectionIntegrationStatus";
import type { ConnectionIntegration } from "./use-connection-integration.hook";

const TAK: Omit<ConnectionIntegration, "availability"> = {
    integrationId: "tak-id",
    schemaVersion: 1,
    name: "TAK",
};

/** The marker's drawing, without the colour that differs anyway. */
function markerShape(label: string): string {
    const marker = screen.getByText(label).parentElement;
    return marker?.querySelector("svg")?.innerHTML ?? "";
}

describe("ConnectionIntegrationValue", () => {
    it("draws available and unavailable with different shapes, not only colours", () => {
        const { unmount } = render(
            <ConnectionIntegrationValue integration={{ ...TAK, availability: "available" }} />,
        );
        const available = markerShape("Available");
        unmount();

        render(
            <ConnectionIntegrationValue integration={{ ...TAK, availability: "unavailable" }} />,
        );
        const unavailable = markerShape("Unavailable");

        expect(available).not.toBe("");
        expect(unavailable).not.toBe("");
        expect(available).not.toBe(unavailable);
    });

    it("shows no marker while availability is unknown", () => {
        render(<ConnectionIntegrationValue integration={{ ...TAK, availability: "unknown" }} />);

        expect(screen.getByText("TAK")).toBeVisible();
        expect(screen.queryByText("Available")).toBeNull();
        expect(screen.queryByText("Unavailable")).toBeNull();
    });
});
