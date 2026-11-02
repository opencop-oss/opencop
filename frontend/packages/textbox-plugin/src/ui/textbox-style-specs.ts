// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { ShapeStyle, StrokePattern, type TextBoxStyleProps } from "@opencop/elements";
import type { StyleSpec } from "@opencop/tools";
import { MdLineStyle, MdLineWeight } from "react-icons/md";

/**
 * The same defaults `DefaultTextBoxStyle` carries, as literals.
 *
 * Read off the style object instead, this module would touch
 * `@opencop/elements` at load — which throws in any test that mocks that
 * package partially, and the plugin is in the graph of every test that mounts
 * the app. The host's own shapes declare these as literals too. Typed against
 * the style's props so a rename cannot pass silently.
 */
const DEFAULTS = {
    fillColor: "#00000059",
    strokeColor: "#ffffffff",
    strokeWeight: 2,
} as const satisfies Partial<TextBoxStyleProps>;

/** Fill and stroke; the host's equivalents are app source. Text has no `StyleSpec` input, so it lives in the editor. */
// Keys name this plugin's own namespace: the host's `toolbar.*` equivalents
// belong to shapes and would leave raw keys here if they were renamed.
export const textBoxStyleSpecs: readonly StyleSpec[] = [
    {
        label: "fill color",
        labelKey: "textbox:style.fillColor",
        input: {
            type: "swatch",
            defaultValue: DEFAULTS.fillColor,
            onChange: (element, value) => {
                const style = element.getStyle();
                if (style instanceof ShapeStyle) style.setFillColor(value);
            },
        },
    },
    {
        label: "stroke color",
        labelKey: "textbox:style.strokeColor",
        input: {
            type: "swatch",
            defaultValue: DEFAULTS.strokeColor,
            onChange: (element, value) => {
                const style = element.getStyle();
                if (style instanceof ShapeStyle) style.setStrokeColor(value);
            },
        },
    },
    {
        label: "stroke weight",
        labelKey: "textbox:style.strokeWeight",
        icon: MdLineWeight,
        input: {
            type: "weight",
            min: 1,
            max: 10,
            step: 0.01,
            defaultValue: DEFAULTS.strokeWeight,
            onChange: (element, value) => {
                const style = element.getStyle();
                if (style instanceof ShapeStyle) style.setStrokeWeight(value);
            },
        },
    },
    {
        label: "stroke pattern",
        labelKey: "textbox:style.strokePattern",
        icon: MdLineStyle,
        input: {
            type: "pattern",
            items: ["solid", "dashed", "dotted", "outlined"],
            defaultValue: "solid",
            onChange: (element, value) => {
                const style = element.getStyle();
                if (style instanceof ShapeStyle)
                    style.setStrokePattern(StrokePattern.fromName(value));
            },
        },
    },
];
