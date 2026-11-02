// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { Circle, type CircleGeometry, type Element } from "@opencop/elements";
import { screen } from "@testing-library/react";
import { CircleInput } from "./CircleInput";
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

function renderCircle(readOnly: boolean) {
    const element = Circle.create({ origin: [10, 45], radius: 500 }) as Element<CircleGeometry>;
    renderInHost(<CircleInput element={element} readOnly={readOnly} />);
}

describe("CircleInput", () => {
    it("an editable radius has stepper buttons", () => {
        renderCircle(false);

        expect(screen.getByRole("button", { name: "increment value" })).toBeInTheDocument();
    });

    it("a read-only radius has no stepper buttons and is marked read-only", () => {
        renderCircle(true);

        expect(screen.queryByRole("button", { name: "increment value" })).not.toBeInTheDocument();
        expect(screen.getByLabelText("Radius")).toHaveAttribute("readonly");
    });
});
