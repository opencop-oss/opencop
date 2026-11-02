// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import { render, screen } from "~/test";
import { AudienceModeSelect } from "./AudienceModeSelect";

/**
 * Rendered against the real `OcSelect` rather than the drawer suite's native-select double,
 * because the prop path to the trigger is exactly what is under test: a double that manufactures
 * the attribute would pass whether or not the production control has a name.
 */
describe("AudienceModeSelect", () => {
    /**
     * The Audience label lives in the drawer's detail table, which is not associated with this
     * control, so the trigger carries the only programmatic name it has.
     */
    it("gives the combobox an accessible name", async () => {
        render(<AudienceModeSelect value="NONE" onChange={vi.fn()} />);

        expect(await screen.findByRole("combobox", { name: "Audience" })).toBeInTheDocument();
    });

    it("shows the stored mode and goes read-only while a save is in flight", async () => {
        render(<AudienceModeSelect value="ALL" onChange={vi.fn()} disabled />);

        const combobox = await screen.findByRole("combobox", { name: "Audience" });
        expect(combobox).toHaveTextContent("Everyone");
        expect(combobox).toBeDisabled();
    });
});
