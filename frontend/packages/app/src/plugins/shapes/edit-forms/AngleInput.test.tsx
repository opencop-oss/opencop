// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import userEvent from "@testing-library/user-event";
import * as turf from "@turf/turf";
import { Angle, type Position2D } from "@opencop/elements";
import { act, screen } from "@testing-library/react";
import { AngleInput } from "./AngleInput";
import type { ReactElement } from "react";
import { createTestHost } from "@opencop/plugin-sdk/testing";
import { HOST_TRANSLATIONS } from "~/test/host-translations";
import { installShapes } from "../install-shapes";

/** Shapes installed in a test host; `ui` rendered under METRIC display preferences. */
function renderInHost(ui: ReactElement) {
    const host = createTestHost({
        id: "opencop.shapes",
        install: installShapes,
        translations: [HOST_TRANSLATIONS],
    });
    return host.render(ui, {
        capabilities: { displayPreferences: { measurementSystem: "METRIC" } },
    });
}

const VERTEX: Position2D = [10, 45];

/** Position for arm 1 at `meters` from the vertex, on its existing bearing. */
const dragArmTo = (meters: number) =>
    turf.destination(VERTEX, meters, 90, { units: "meters" }).geometry.coordinates as Position2D;

/** Arms placed at exact metre distances so the segment fields render predictable whole numbers. */
function makeAngle(leg0Meters: number, leg1Meters: number) {
    const arm = (meters: number, bearing: number) =>
        turf.destination(VERTEX, meters, bearing, { units: "meters" }).geometry
            .coordinates as Position2D;
    return Angle.create({
        first: arm(leg0Meters, 0),
        second: VERTEX,
        third: arm(leg1Meters, 90),
    });
}

function renderAngle(leg0Meters = 500, leg1Meters = 300, readOnly = false) {
    const element = makeAngle(leg0Meters, leg1Meters);
    // METRIC keeps the default length unit at `m` for these distances, so the segment fields read
    // back the same whole numbers the fixture set rather than a rounded km value.
    // The test host provides display preferences as the map view does, with a coordinate format
    // whose setter takes effect, because the vertex field's format selector is part of the render.
    renderInHost(<AngleInput element={element} readOnly={readOnly} />);
    return { element, user: userEvent.setup() };
}

const segment1 = () => screen.getByLabelText("Segment 1");
const segment2 = () => screen.getByLabelText("Segment 2");
const matchLengths = () => screen.getByRole("checkbox", { name: "Match segment lengths" });

/** Replace a numeric field's contents, leaving focus in the field as a real edit would. */
async function retype(user: ReturnType<typeof userEvent.setup>, field: HTMLElement, value: string) {
    await user.clear(field);
    await user.type(field, value);
}

describe("AngleInput match-lengths link", () => {
    it("starts unlinked with both segment fields editable", () => {
        renderAngle();

        expect(segment1()).toHaveValue("500");
        expect(segment2()).toHaveValue("300");
        expect(matchLengths()).not.toBeChecked();
        expect(segment2()).not.toHaveAttribute("readonly");
    });

    it("linking copies segment 1 onto segment 2 and marks it read-only", async () => {
        const { user } = renderAngle();

        await user.click(matchLengths());

        expect(matchLengths()).toBeChecked();
        expect(segment2()).toHaveValue("500");
        expect(segment2()).toHaveAttribute("readonly");
        expect(segment2()).toHaveAccessibleDescription(
            "Read-only: matches segment 1 while lengths are linked.",
        );
    });

    // The regression this file exists for: `matched` was derived purely from geometry, so an
    // unchecking click had nothing to write and the controlled `checked` snapped straight back.
    it("releases the link when unchecked", async () => {
        const { user } = renderAngle();

        await user.click(matchLengths());
        expect(matchLengths()).toBeChecked();

        await user.click(matchLengths());

        expect(matchLengths()).not.toBeChecked();
        expect(segment2()).not.toHaveAttribute("readonly");
    });

    // Reported symptom: after checking, three further clicks left the control `data-state="checked"`
    // with segment 2 still locked. Repeated because one click landing correctly is not the same as
    // the control being toggleable.
    it("stays toggleable across repeated clicks", async () => {
        const { user } = renderAngle();

        for (const expected of [true, false, true, false]) {
            await user.click(matchLengths());
            expect(matchLengths()).toHaveProperty("checked", expected);
            expect(segment2().hasAttribute("readonly")).toBe(expected);
        }
    });

    // Reported symptom: the failed uncheck desynced the hidden native input (`checked = false`)
    // from the visual parts, which stayed `data-state="checked"` — so a screen reader was told
    // "unchecked" while the field behaved as checked.
    it("keeps the native input and the visual control in agreement", async () => {
        const { user } = renderAngle();
        const dataStates = () =>
            [...document.querySelectorAll("[data-scope='checkbox'][data-state]")].map((n) =>
                n.getAttribute("data-state"),
            );

        await user.click(matchLengths());
        expect(matchLengths()).toBeChecked();
        expect(dataStates().every((s) => s === "checked")).toBe(true);

        await user.click(matchLengths());
        expect(matchLengths()).not.toBeChecked();
        expect(dataStates().every((s) => s === "unchecked")).toBe(true);
    });

    // A map drag auto-unchecks the box by breaking `matched`, but that leaves the *intent* set.
    // Retyping equality then re-latched the field mid-edit and ate the keystroke that got there.
    it("does not re-latch after a map drag releases the link", async () => {
        const { element, user } = renderAngle();

        await user.click(matchLengths());
        expect(segment2()).toHaveAttribute("readonly");

        // Stand-in for dragging arm 1 on the canvas: move it to a different distance.
        await act(async () => {
            element.geometry.setPos(dragArmTo(900), 2);
        });
        expect(matchLengths()).not.toBeChecked();
        expect(segment2()).not.toHaveAttribute("readonly");

        await retype(user, segment2(), "500");

        expect(segment2()).toHaveValue("500");
        expect(segment2()).not.toHaveAttribute("readonly");
        expect(segment2()).toHaveFocus();
    });

    // The other half of the same regression: equal lengths alone used to disable segment 2, so
    // typing segment 1's value into it locked the field mid-edit.
    it("does not latch when a typed segment 2 value happens to equal segment 1", async () => {
        const { user } = renderAngle();

        await retype(user, segment2(), "500");

        expect(segment2()).not.toHaveAttribute("readonly");
        expect(segment2()).toHaveFocus();
        expect(matchLengths()).not.toBeChecked();
    });

    it("drives segment 2 from segment 1 while linked", async () => {
        const { user } = renderAngle();

        await user.click(matchLengths());
        await retype(user, segment1(), "800");

        expect(segment2()).toHaveValue("800");
    });

    it("leaves segment 2 independently editable after the link is released", async () => {
        const { user } = renderAngle();

        await user.click(matchLengths());
        await user.click(matchLengths());
        await retype(user, segment2(), "250");
        await user.tab();

        expect(segment2()).toHaveValue("250");
        expect(segment1()).toHaveValue("500");
    });

    // `readOnly` rather than `disabled` so the value stays reachable by keyboard.
    it("a read-only form keeps the match checkbox focusable and refuses a toggle", async () => {
        const { user } = renderAngle(500, 300, true);

        expect(matchLengths()).not.toBeDisabled();
        await user.click(matchLengths());
        expect(matchLengths()).not.toBeChecked();
    });

    it("a read-only form shows no stepper buttons", () => {
        renderAngle(500, 300, true);

        expect(screen.queryByRole("button", { name: "increment value" })).not.toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "decrement value" })).not.toBeInTheDocument();
    });

    it("keeps the read-only segment 2 field in the tab order", async () => {
        const { user } = renderAngle();

        await user.click(matchLengths());
        segment2().focus();

        expect(segment2()).toHaveFocus();
    });
});

describe("AngleInput accessible names", () => {
    it.each(["Unit", "Angle Units"])("names the %s select from its field label", (name) => {
        renderAngle();

        expect(screen.getByRole("combobox", { name })).toBeInTheDocument();
    });
});

describe("AngleInput angle field", () => {
    // The Angle field used to carry `max={360}`, which clamped a typed 400 to 360; the handler's
    // own wrap then reduced it to 0 and collapsed arm 1 onto arm 0.
    it("wraps an over-range angle instead of clamping it", async () => {
        const { user } = renderAngle();
        const angleField = screen.getByLabelText("Angle") as HTMLInputElement;

        await retype(user, angleField, "400");
        await user.tab();

        // Asserted numerically because Chakra normalises the formatter's trailing ".0" on blur.
        // Clamping would have produced 0 here: `max` capped the entry at 360, which the handler's
        // own `% 360` then reduced to zero, collapsing arm 1 onto arm 0.
        expect(Number(angleField.value)).toBeCloseTo(40, 1);
    });
});
