// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen, waitFor, userEvent, server, http, HttpResponse, API_BASE } from "~/test";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AddTileDialog } from "./AddTileDialog";

// Access scope decides who can see a tile: a GLOBAL tile is published to every user. The dialog is
// admin-only, so the gate that matters is the server's — these cover the control's own behaviour.

// OcTabSelect is a zag.js widget that does not drive in jsdom. The double exposes the value as
// inspectable DOM.
vi.mock("@opencop/ui", async (importOriginal) => {
    const { createElement } = await import("react");
    interface TabSelectProps {
        label?: string;
        value?: string;
        defaultValue: string;
        options: { label: string; value: string }[];
        onChange?: (value: string) => void;
    }
    return {
        ...(await importOriginal<typeof import("@opencop/ui")>()),
        OcTabSelect: (props: TabSelectProps) =>
            createElement(
                "select",
                {
                    "aria-label": `tab-select-${props.value ?? props.defaultValue}`,
                    "data-testid": "tab-select",
                    value: props.value ?? props.defaultValue,
                    onChange: (e: { target: { value: string } }) =>
                        props.onChange?.(e.target.value),
                },
                ...props.options.map((o) =>
                    createElement("option", { key: o.value, value: o.value }, o.label),
                ),
            ),
    };
});

/** The access-scope control is the tab-select whose options are the two scopes. */
function scopeSelect(): HTMLSelectElement {
    const selects = screen.getAllByTestId("tab-select") as HTMLSelectElement[];
    const found = selects.find((el) => Array.from(el.options).some((o) => o.value === "GLOBAL"));
    if (!found) throw new Error("access-scope control not rendered");
    return found;
}

describe("AddTileDialog access scope", () => {
    beforeEach(() => {
        server.use(
            http.get(`${API_BASE}/map/tiles`, () => HttpResponse.json([])),
            http.get(`${API_BASE}/admin/map/tiles`, () => HttpResponse.json([])),
        );
    });

    it("defaults a new tile to PRIVATE", async () => {
        render(<AddTileDialog isOpen onClose={() => {}} />);

        await waitFor(() => expect(scopeSelect().value).toBe("PRIVATE"));
    });

    it("lets an admin choose GLOBAL", async () => {
        const user = userEvent.setup();
        render(<AddTileDialog isOpen onClose={() => {}} />);

        await waitFor(() => expect(scopeSelect()).toBeInTheDocument());
        await user.selectOptions(scopeSelect(), "GLOBAL");

        await waitFor(() => expect(scopeSelect().value).toBe("GLOBAL"));
    });

    it("offers exactly the two scopes", async () => {
        render(<AddTileDialog isOpen onClose={() => {}} />);

        await waitFor(() => expect(scopeSelect()).toBeInTheDocument());
        expect(Array.from(scopeSelect().options).map((o) => o.value)).toEqual([
            "PRIVATE",
            "GLOBAL",
        ]);
    });
});

describe("AddTileDialog attribution", () => {
    const tooLong = "Attribution must be at most 1000 characters.";

    beforeEach(() => {
        server.use(
            http.get(`${API_BASE}/map/tiles`, () => HttpResponse.json([])),
            http.get(`${API_BASE}/admin/map/tiles`, () => HttpResponse.json([])),
        );
    });

    it("checks the length on blur and stays neutral while the value is corrected", async () => {
        const user = userEvent.setup();
        render(<AddTileDialog isOpen onClose={() => {}} />);
        const field = await screen.findByRole("textbox", { name: "Attribution" });

        await user.click(field);
        await user.paste("x".repeat(1001));
        expect(screen.queryByText(tooLong)).not.toBeInTheDocument();

        await user.tab();
        expect(await screen.findByText(tooLong)).toBeInTheDocument();

        await user.click(field);
        await user.keyboard("{End}{Backspace}");
        expect(screen.queryByText(tooLong)).not.toBeInTheDocument();

        await user.tab();
        await waitFor(() => expect(screen.queryByText(tooLong)).not.toBeInTheDocument());
        expect(field).toHaveValue("x".repeat(1000));
    });
});
