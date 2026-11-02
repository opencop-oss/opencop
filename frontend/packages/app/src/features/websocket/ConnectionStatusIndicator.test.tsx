// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { render, screen, userEvent } from "~/test";
import { ConnectionStatusIndicator } from "./ConnectionStatusIndicator";

describe("ConnectionStatusIndicator", () => {
    it("explains a FAILED state by its reported reason", async () => {
        const user = userEvent.setup();
        render(<ConnectionStatusIndicator state="FAILED" reason="INTEGRATION_UNAVAILABLE" />);

        await user.hover(screen.getByText("FAILED").parentElement as HTMLElement);

        // The shared tooltip uppercases its content, as for every other state.
        expect(await screen.findByText("INTEGRATION UNAVAILABLE")).toBeInTheDocument();
    });

    it("keeps the generic tooltip without a known reason", async () => {
        const user = userEvent.setup();
        render(<ConnectionStatusIndicator state="FAILED" reason="SOMETHING_NEWER" />);

        await user.hover(screen.getByText("FAILED").parentElement as HTMLElement);

        expect(await screen.findByText("CONNECTION FAILED")).toBeInTheDocument();
    });

    it("ignores a reason that only matches an inherited object property", async () => {
        const user = userEvent.setup();
        render(<ConnectionStatusIndicator state="FAILED" reason="toString" />);

        await user.hover(screen.getByText("FAILED").parentElement as HTMLElement);

        expect(await screen.findByText("CONNECTION FAILED")).toBeInTheDocument();
    });

    it("ignores a reason left over beside a state other than FAILED", async () => {
        const user = userEvent.setup();
        render(<ConnectionStatusIndicator state="CONNECTED" reason="INTEGRATION_UNAVAILABLE" />);

        await user.hover(screen.getByText("CONNECTED").parentElement as HTMLElement);

        // The CONNECTED tooltip repeats the state label, so it shows as a second match.
        expect(await screen.findAllByText("CONNECTED")).toHaveLength(2);
        expect(screen.queryByText("INTEGRATION UNAVAILABLE")).toBeNull();
    });
});
