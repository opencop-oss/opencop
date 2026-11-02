// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it } from "vitest";
import { withPlugins } from "~/test/plugins";
import { act, render, screen, userEvent } from "~/test";
import { MapFilterPanel } from "./MapFilterPanel";
import { useMapFilterStore } from "./map-filter.store";

// The SIDC fields come from the symbology plugin, installed the way the app's loader does.
withPlugins(["opencop.symbology"]);

const SYMBOL_PATH = "properties.opencop.element.symbol";

// Pins what the Map Filters panel offers and saves, through what the operator sees.
describe("MapFilterPanel", () => {
    afterEach(() => {
        act(() => useMapFilterStore.setState({ filters: [], activeNames: [], active: [] }));
    });

    it("offers only the fields the map can evaluate, and no way to add one", async () => {
        const user = userEvent.setup();
        render(<MapFilterPanel />);

        await user.click(screen.getByRole("button", { name: "Create filter" }));
        await user.click(screen.getByPlaceholderText("Select a field"));

        const options = await screen.findAllByRole("option");
        expect(options.map((option) => option.textContent)).toEqual([
            "Display Name",
            "SIDC",
            "SIDC: Standard Identity",
            "SIDC: Symbol Set",
        ]);

        await user.clear(screen.getByPlaceholderText("Select a field"));
        await user.paste("callsign");
        expect(await screen.findByText("No matching fields")).toBeInTheDocument();
        expect(screen.queryByRole("option", { name: /callsign/ })).not.toBeInTheDocument();
    });

    it("saves a symbol condition as its 2525D predicate and 2525C companion", async () => {
        const user = userEvent.setup();
        render(<MapFilterPanel />);

        await user.click(screen.getByRole("button", { name: "Create filter" }));
        await user.click(screen.getByPlaceholderText("Select a field"));
        await user.click(await screen.findByRole("option", { name: "SIDC: Standard Identity" }));
        await user.click(screen.getByPlaceholderText("Select a value"));
        await user.click(await screen.findByRole("option", { name: /Hostile\/Faker/ }));
        await user.type(screen.getByRole("textbox", { name: "Name" }), "Hostiles");
        await user.click(screen.getByRole("button", { name: "Save" }));

        expect(useMapFilterStore.getState().filters).toEqual([
            {
                name: "Hostiles",
                // Picking a symbol field switches the combinator to Any.
                combine: "ANY",
                predicates: [
                    {
                        path: SYMBOL_PATH,
                        operation: "SUBSTRING_IN",
                        values: ["6"],
                        offset: 3,
                        length: 1,
                        negate: false,
                        caseInsensitive: false,
                    },
                    {
                        path: SYMBOL_PATH,
                        operation: "SUBSTRING_IN",
                        values: ["H", "K"],
                        offset: 1,
                        length: 1,
                        negate: false,
                        caseInsensitive: false,
                    },
                ],
            },
        ]);
        expect(await screen.findByText("Hostiles")).toBeInTheDocument();
    });
});
