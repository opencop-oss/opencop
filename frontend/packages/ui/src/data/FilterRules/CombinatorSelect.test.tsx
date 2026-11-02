// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import { render, screen, userEvent } from "~/test";
import { CombinatorSelect } from "./CombinatorSelect";

describe("CombinatorSelect", () => {
    it("uses radio semantics and marks the current value as selected", () => {
        render(<CombinatorSelect value="ALL" />);

        expect(screen.getByRole("radiogroup", { name: "Select combinator" })).toBeInTheDocument();
        const selected = screen.getByRole("radio", { name: "All" });
        expect(selected).toBeChecked();
        expect(selected).not.toHaveAttribute("aria-controls");
        expect(screen.getByRole("radio", { name: "Any" })).not.toBeChecked();
    });

    it("calls onChange with the newly selected combinator", async () => {
        const user = userEvent.setup();
        const onChange = vi.fn();

        render(<CombinatorSelect value="ALL" onChange={onChange} />);

        await user.click(screen.getByRole("radio", { name: "Any" }));

        expect(onChange).toHaveBeenCalledWith("ANY");
    });
});
