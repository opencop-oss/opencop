// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { beforeEach, describe, expect, it } from "vitest";
import { render, screen, userEvent, waitFor } from "~/test";
import { defaultMapSettings, useMapSettingsStore } from "~/features/map/map-settings.store";
import { TextZoomThresholdControl } from "./TextZoomThresholdControl";

const LABEL = "Minimum Zoom for Text (exact value)";
const WARNING =
    "Making text visible at lower zooms may affect performance. Too many objects on the map will cause stuttery, lagging map movements. If you experience this, increase this setting back to 5.";

beforeEach(() => {
    useMapSettingsStore.setState({ ...defaultMapSettings });
});

describe("TextZoomThresholdControl", () => {
    it("hides the performance warning for the default threshold", () => {
        render(<TextZoomThresholdControl />);

        expect(screen.getByText(WARNING)).not.toBeVisible();
        expect(screen.getByRole("spinbutton", { name: LABEL })).toHaveValue("5");
    });

    it("shows the warning and commits immediately when the number input drops below 5", async () => {
        const user = userEvent.setup();
        render(<TextZoomThresholdControl />);

        const input = screen.getByRole("spinbutton", { name: LABEL });
        await user.clear(input);
        await user.type(input, "3");

        await waitFor(() => expect(screen.getByText(WARNING)).toBeVisible());
        await waitFor(() => expect(useMapSettingsStore.getState().textZoomThreshold).toBe(3));
    });

    it("hides the warning again once raised back to 5 or above", async () => {
        useMapSettingsStore.getState().setTextZoomThreshold(3);
        const user = userEvent.setup();
        render(<TextZoomThresholdControl />);
        expect(screen.getByText(WARNING)).toBeVisible();

        const input = screen.getByRole("spinbutton", { name: LABEL });
        await user.clear(input);
        await user.type(input, "5");

        await waitFor(() => expect(screen.getByText(WARNING)).not.toBeVisible());
        expect(useMapSettingsStore.getState().textZoomThreshold).toBe(5);
    });

    it("clamps number-input values to the map's reachable zoom range", async () => {
        const user = userEvent.setup();
        render(<TextZoomThresholdControl />);
        const input = screen.getByRole("spinbutton", { name: LABEL });

        await user.clear(input);
        await user.type(input, "30");
        await waitFor(() => expect(useMapSettingsStore.getState().textZoomThreshold).toBe(22));

        await user.clear(input);
        await user.type(input, "-5");
        await waitFor(() => expect(useMapSettingsStore.getState().textZoomThreshold).toBe(0));
    });

    it("commits a slider adjustment to the store", async () => {
        const user = userEvent.setup();
        render(<TextZoomThresholdControl />);

        // The slider machine only handles arrow keys once it has processed a
        // `FOCUS` event, so tab onto the thumb rather than calling `.focus()`
        // directly (which bypasses userEvent's act-wrapped event dispatch).
        // `getByRole("slider")` isn't viable here: OcSlider's thumb stays
        // `visibility: hidden` until a ResizeObserver measures it, which never
        // fires in jsdom — don't "simplify" this back to a role query.
        await user.tab();
        expect(document.querySelector('[role="slider"]')).toHaveFocus();
        await user.keyboard("{ArrowLeft}");

        await waitFor(() => expect(screen.getByText(WARNING)).toBeVisible());
        await waitFor(() =>
            expect(useMapSettingsStore.getState().textZoomThreshold).toBeLessThan(5),
        );
    });
});
