// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import { useState } from "react";
import { render, screen, userEvent } from "../test";
import { TextSizeField } from "./TextSizeField";
import { MAX_TEXT_SIZE_METERS, MIN_TEXT_SIZE_METERS } from "./box-text.util";

describe("TextSizeField", () => {
    const field = () => screen.getByRole("textbox", { name: "Text size (m)" });

    it("shows the current size in readable metres", () => {
        render(<TextSizeField value={2500} onChange={vi.fn()} />);
        expect(field()).toHaveValue("2500");
    });

    it("shows a size the operator typed, even though it is not a preset", () => {
        render(<TextSizeField value={3717} onChange={vi.fn()} />);
        expect(field()).toHaveValue("3717");
    });

    it("accepts a typed size", async () => {
        const onChange = vi.fn();
        render(<TextSizeField value={100} onChange={onChange} />);

        await userEvent.clear(field());
        await userEvent.type(field(), "3000{Enter}");

        expect(onChange).toHaveBeenCalledWith(3000);
    });

    it("holds a typed size inside the allowed range", async () => {
        const onChange = vi.fn();
        render(<TextSizeField value={100} onChange={onChange} />);

        await userEvent.clear(field());
        await userEvent.type(field(), "9999999{Enter}");

        expect(onChange).toHaveBeenCalledWith(MAX_TEXT_SIZE_METERS);
    });

    it("reads a typed unit as its own, not as the field's", async () => {
        const onChange = vi.fn();
        render(<TextSizeField value={100} onChange={onChange} />);

        await userEvent.clear(field());
        await userEvent.type(field(), "500 ft{Enter}");

        // 500 ft is 152.4 m. Dropping the suffix would store 500 m instead.
        expect(onChange).toHaveBeenCalledWith(expect.closeTo(152.4, 1));
    });

    it("reads a metre suffix as metres for an operator in imperial", async () => {
        const onChange = vi.fn();
        render(<TextSizeField value={1000} onChange={onChange} />, {
            preferences: { measurementSystem: "IMPERIAL" },
        });

        const imperial = screen.getByRole("textbox", { name: "Text size (ft)" });
        await userEvent.clear(imperial);
        await userEvent.type(imperial, "500 m{Enter}");

        expect(onChange).toHaveBeenCalledWith(500);
    });

    it("clears rejected text when a preset is picked, even the size already set", async () => {
        const onChange = vi.fn();
        render(<TextSizeField value={100} onChange={onChange} />);

        await userEvent.clear(field());
        await userEvent.type(field(), "abc");
        await userEvent.tab();
        expect(field()).toBeInvalid();

        // 100 m is what is already stored, so nothing resyncs the field for it.
        await userEvent.click(screen.getByRole("button", { name: "Size presets" }));
        await userEvent.click(screen.getByRole("menuitem", { name: "100 m" }));

        expect(field()).toHaveValue("100");
        expect(field()).not.toBeInvalid();
    });

    it("offers sizes across the whole range, not evenly spaced", async () => {
        render(<TextSizeField value={MIN_TEXT_SIZE_METERS} onChange={vi.fn()} />);
        await userEvent.click(screen.getByRole("button", { name: "Size presets" }));

        // Geometric stops: small sizes get as many options as large ones.
        expect(screen.getByRole("menuitem", { name: "2 m" })).toBeInTheDocument();
        expect(screen.getByRole("menuitem", { name: "250 m" })).toBeInTheDocument();
        expect(screen.getByRole("menuitem", { name: "300,000 m" })).toBeInTheDocument();
    });
    it("shows and accepts feet for an operator in imperial", async () => {
        const onChange = vi.fn();
        // 1000 m is 3,280.8 ft.
        render(<TextSizeField value={1000} onChange={onChange} />, {
            preferences: { measurementSystem: "IMPERIAL" },
        });

        const imperial = screen.getByRole("textbox", { name: "Text size (ft)" });
        expect(imperial).toHaveValue("3281");

        await userEvent.clear(imperial);
        await userEvent.type(imperial, "500{Enter}");

        // Stored in metres whatever the operator typed.
        expect(onChange.mock.calls[0][0]).toBeCloseTo(152.4, 3);
    });

    it("offers round numbers in the operator's own units", async () => {
        render(<TextSizeField value={1000} onChange={vi.fn()} />, {
            preferences: { measurementSystem: "IMPERIAL" },
        });
        await userEvent.click(screen.getByRole("button", { name: "Size presets" }));

        // Round feet, not metres converted into awkward ones.
        expect(screen.getByRole("menuitem", { name: "500 ft" })).toBeInTheDocument();
        expect(screen.queryByRole("menuitem", { name: /1,640/ })).not.toBeInTheDocument();
    });

    it("keeps focus in the field after committing with Enter", async () => {
        // A stateful host, so a commit really does change `value` — with a
        // `vi.fn()` nothing changes and the regression is invisible.
        function Host() {
            const [size, setSize] = useState(100);
            return <TextSizeField value={size} onChange={setSize} />;
        }
        render(<Host />);
        const input = screen.getByRole("textbox", { name: "Text size (m)" });

        await userEvent.clear(input);
        await userEvent.type(input, "250{Enter}");

        expect(screen.getByRole("textbox", { name: "Text size (m)" })).toHaveFocus();
        expect(document.body).not.toHaveFocus();
    });

    it("leaves the stored size alone when the field is focused but not edited", async () => {
        const onChange = vi.fn();
        // An auto-sized fraction: the field shows it rounded, so reading the
        // rounded text back must not count as an edit.
        render(<TextSizeField value={133.4340962802395} onChange={onChange} />);

        await userEvent.click(screen.getByRole("textbox", { name: "Text size (m)" }));
        await userEvent.tab();

        expect(onChange).not.toHaveBeenCalled();
    });

    // "2,5" is an `fr` decimal, not 25 m; a comma only groups thousands.
    it.each(["2km", "12abc", "", "m", "2,5"])(
        "refuses %o rather than reading a number out of it",
        async (bad) => {
            const onChange = vi.fn();
            render(<TextSizeField value={100} onChange={onChange} />);
            const input = screen.getByRole("textbox", { name: "Text size (m)" });

            await userEvent.clear(input);
            await userEvent.type(input, `${bad}{Enter}`);

            expect(onChange).not.toHaveBeenCalled();
            // Left as typed, with a message, rather than silently reverted.
            expect(input).toHaveValue(bad);
            expect(input).toHaveAttribute("aria-invalid", "true");
            expect(
                screen.getByText("Text size must be a number, optionally followed by m."),
            ).toBeInTheDocument();
        },
    );

    it("clears the message on the next keystroke", async () => {
        render(<TextSizeField value={100} onChange={vi.fn()} />);
        const input = screen.getByRole("textbox", { name: "Text size (m)" });

        await userEvent.clear(input);
        await userEvent.type(input, "2km{Enter}");
        expect(input).toHaveAttribute("aria-invalid", "true");

        await userEvent.type(input, "{Backspace}");

        expect(input).not.toHaveAttribute("aria-invalid", "true");
    });

    it("accepts a grouped number, and a pasted one with its unit", async () => {
        const onChange = vi.fn();
        render(<TextSizeField value={100} onChange={onChange} />);
        const input = screen.getByRole("textbox", { name: "Text size (m)" });

        await userEvent.clear(input);
        await userEvent.type(input, "2,500 m{Enter}");

        expect(onChange).toHaveBeenCalledWith(2500);
    });
});
