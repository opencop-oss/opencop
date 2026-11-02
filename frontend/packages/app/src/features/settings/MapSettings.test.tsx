// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { nativeOwner } from "~/features/contributions/contribution-guard";
import { render, screen } from "~/test";
import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import { noop } from "lodash";
import { defaultMapSettings, useMapSettingsStore } from "~/features/map/map-settings.store";
import { contributionContextFor } from "~/features/contributions/contribution-installer";
import { installNativeSubjects } from "~/features/contributions/native-install";
import { registerElementMapper } from "~/features/contributions/element-mapper-registry";
import { installShapes } from "~/plugins/shapes/install-shapes";
import { installMediaIcons } from "~/plugins/media-icons/install-media-icons";
import { MapSettings } from "./MapSettings";

/**
 * Characterization: the label toggles the settings panel lists. The list names no
 * element type: it comes from the renderers' `textSetting`, so the shapes and
 * media-icons subjects are installed, and a stub stands in for the symbology
 * plugin's graphic renderer.
 */
describe("MapSettings — label toggles today", () => {
    let dispose = noop;
    beforeAll(() => {
        const disposeSubjects = installNativeSubjects(
            [
                { id: "opencop.shapes", install: installShapes },
                { id: "opencop.media-icons", install: installMediaIcons },
            ],
            contributionContextFor,
        );
        // Stand-ins for symbology's two renderers.
        const disposeSymbol = registerElementMapper(nativeOwner("opencop.test"), {
            type: "symbol",
            map: () => [],
            textSetting: { labelKey: "mapSettings.textLabels.symbol", order: 70 },
        });
        const disposeGraphic = registerElementMapper(nativeOwner("opencop.test"), {
            type: "graphic",
            map: () => [],
            textSetting: { labelKey: "mapSettings.textLabels.graphic", order: 100 },
        });
        dispose = () => {
            disposeSymbol();
            disposeGraphic();
            disposeSubjects();
        };
    });
    afterAll(() => dispose());

    beforeEach(() => {
        useMapSettingsStore.setState({
            ...defaultMapSettings,
            accordion: ["general", "text", "metrics"],
        });
    });

    it("lists one switch per element type the renderers declare, in declared order", () => {
        render(<MapSettings />);

        const labels = screen
            .getAllByRole("switch")
            .map((input) => input.closest("label")?.textContent?.trim() ?? "")
            .filter((label) => label.endsWith(" Text"));

        expect(labels).toEqual([
            "Polygon Text",
            "Rectangle Text",
            "Ellipse Text",
            "Circle Text",
            "Arrow Text",
            "Angle Text",
            "Symbol Text",
            "Imported Icon Text",
            "Point Text",
            "Line Text",
            "Graphic Text",
        ]);
    });
});
