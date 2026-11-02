// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import {
    sourceTextVisibility,
    sourceTextVisibilityUpdateTriggers,
    textProps,
} from "./element-layer-props";
import type { RenderContext } from "./render-context";

const STORE_ELEMENT = {
    key: "managed\0alpha",
    type: "circle",
    source: "managed",
};

describe("sourceTextVisibility", () => {
    it("exempts a live dataset clone by its stable element key", () => {
        const datasetClone = { ...STORE_ELEMENT };
        const isVisible = sourceTextVisibility({
            hovered: { circle: STORE_ELEMENT },
            filterManagedSources: ["managed"],
            textVisibleSources: [],
        });

        expect(datasetClone).not.toBe(STORE_ELEMENT);
        expect(isVisible(datasetClone)).toBe(true);
    });

    it("uses the same stable-key exemption for selection", () => {
        const datasetClone = { ...STORE_ELEMENT };
        const isVisible = sourceTextVisibility({
            hovered: {},
            selected: STORE_ELEMENT,
            filterManagedSources: ["managed"],
            textVisibleSources: [],
        });

        expect(isVisible(datasetClone)).toBe(true);
    });
});

describe("textProps", () => {
    it("forwards the configured text zoom threshold to the culled text layer", () => {
        const options: RenderContext = {
            settings: {
                symbolSize: 30,
                textSize: 14,
                textZoomThreshold: 3.5,
                textEnabled: { circle: true },
            },
            hovered: {},
            measurementSystem: "METRIC",
            filterManagedSources: [],
            textVisibleSources: [],
        };

        expect(textProps("circle", options).zoomThreshold).toBe(3.5);
    });

    it("shows a type's text unless its toggle is off; a type with no toggle entry shows it", () => {
        const options: RenderContext = {
            settings: {
                symbolSize: 30,
                textSize: 14,
                textZoomThreshold: 3.5,
                textEnabled: { circle: false },
            },
            hovered: {},
            measurementSystem: "METRIC",
            filterManagedSources: [],
            textVisibleSources: [],
        };

        expect(textProps("circle", options).text).toBe(false);
        expect(textProps("line", options).text).toBe(true);
    });
});

describe("sourceTextVisibilityUpdateTriggers", () => {
    it("includes only the selected and hovered identities for the rendered element type", () => {
        const route = { key: "managed\0route", type: "line" };
        const triggers = sourceTextVisibilityUpdateTriggers("circle", {
            hovered: { circle: STORE_ELEMENT },
            selected: route,
            filterManagedSources: ["managed"],
            textVisibleSources: [],
        });

        expect(triggers).toEqual([["managed"], [], undefined, STORE_ELEMENT]);
    });
});
