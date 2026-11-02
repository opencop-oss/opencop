// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import { SIDC } from "@opencop/symbology";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createTestHost } from "@opencop/plugin-sdk/testing";
import symbologyPlugin from "../../plugin";
import { SidcProvider } from "../SidcContext";
import { Entity } from "./Entity";

/**
 * Entity search spans every symbol set the editor can reach, not just the selected
 * one. Before this, an operator looking for a rocket launcher from the default Air
 * set got an empty tree and had to guess which of the 19 sets to switch to first.
 *
 * "Multiple Rocket Launcher" is a `Land Equipment` (15) entity type; the default
 * SIDC sits in `Land Units` (10), so every assertion below is a genuine cross-set reach.
 * Queries are scoped to the browser because that label is also an `Air` sector
 * modifier, and the modifier select renders its options into the same tree.
 */

const LAND_EQUIPMENT = "15";
const WEAPON_SYSTEM_ENTITY = "11";
const MULTIPLE_ROCKET_LAUNCHER_TYPE = "16";

/**
 * Match a row by its whole label. The search highlight wraps the matched substring in
 * its own span, so the label is split across text nodes and plain `getByText` misses it.
 */
const rowLabelled = (label: string) => (_content: string, element: Element | null) =>
    element?.textContent === label &&
    !Array.from(element.children).some((child) => child.textContent === label);

function renderEntity(onChange?: (sidc: SIDC) => void) {
    createTestHost({ plugin: symbologyPlugin }).render(
        <SidcProvider onChange={onChange}>
            <Entity />
        </SidcProvider>,
    );
    return {
        browser: () => within(screen.getByTestId("entity-browser")),
        search: () => screen.getByRole("textbox"),
    };
}

describe("Entity", () => {
    it("stays scoped to the selected symbol set when there is no search term", () => {
        const { browser } = renderEntity();

        expect(browser().queryByText(rowLabelled("Multiple Rocket Launcher"))).toBeNull();
    });

    it("finds entities in other symbol sets and says which set they came from", async () => {
        const user = userEvent.setup();
        const { browser, search } = renderEntity();

        await user.type(search(), "rocket");

        await waitFor(() => {
            expect(browser().getByText(rowLabelled("Multiple Rocket Launcher"))).not.toBeNull();
        });
        expect(browser().getByText(rowLabelled("Land Equipment"))).not.toBeNull();
    });

    it("selects the result's symbol set along with the entity path", async () => {
        const user = userEvent.setup();
        const onChange = vi.fn<(sidc: SIDC) => void>();
        const { browser, search } = renderEntity(onChange);

        await user.type(search(), "rocket");
        await user.click(await browser().findByText(rowLabelled("Multiple Rocket Launcher")));

        const sidc = onChange.mock.lastCall?.[0];
        expect(sidc?.symbolSet).toBe(LAND_EQUIPMENT);
        expect(sidc?.entity).toBe(WEAPON_SYSTEM_ENTITY);
        expect(sidc?.entityType).toBe(MULTIPLE_ROCKET_LAUNCHER_TYPE);
        expect(sidc?.entitySubType).toBe("00");
    });

    it("does not reorder the result groups when a result is picked", async () => {
        const user = userEvent.setup();
        const { browser, search } = renderEntity();

        await user.type(search(), "rocket");
        const order = () =>
            browser()
                .getAllByTestId("symbol-set-header")
                .map((header) => header.textContent);

        await waitFor(() => expect(order()).toEqual(["Land Equipment15", "Activities40"]));

        // Picking from the *trailing* group is the case that used to yank it to the top.
        await user.click(await browser().findByText(rowLabelled("Rocket Explosion")));

        expect(order()).toEqual(["Land Equipment15", "Activities40"]);
    });

    it("re-anchors the result order to the set picked since the last search", async () => {
        const user = userEvent.setup();
        const { browser, search } = renderEntity();
        const order = () =>
            browser()
                .getAllByTestId("symbol-set-header")
                .map((header) => header.textContent);

        // Picking from Activities moves the selected set off the mount-time default.
        await user.type(search(), "rocket");
        await user.click(await browser().findByText(rowLabelled("Rocket Explosion")));
        await user.clear(search());
        await user.type(search(), "rocket");

        // Activities follows Land Equipment in catalog order, so leading with it can
        // only come from the search re-anchoring on the newly selected set.
        await waitFor(() => expect(order()).toEqual(["Activities40", "Land Equipment15"]));
    });

    it("keeps the picked entity visible after the search is cleared", async () => {
        const user = userEvent.setup();
        const { browser, search } = renderEntity();

        await user.type(search(), "rocket");
        await user.click(await browser().findByText(rowLabelled("Multiple Rocket Launcher")));
        await user.clear(search());

        // The browser is now scoped to Land Equipment with the path expanded to the pick.
        await waitFor(() => {
            expect(browser().getByText(rowLabelled("Weapon/Weapon System"))).not.toBeNull();
        });
        expect(browser().getByText(rowLabelled("Multiple Rocket Launcher"))).not.toBeNull();
    });

    it("shows a no-results message when nothing matches in any symbol set", async () => {
        const user = userEvent.setup();
        const { browser, search } = renderEntity();

        await user.type(search(), "zzznomatch");

        await waitFor(() => {
            expect(browser().getAllByText("common.noResults").length).toBeGreaterThan(0);
        });
    });
});
