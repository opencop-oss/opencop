// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { MdLineStyle, MdLineWeight } from "react-icons/md";
import { LuRadius } from "react-icons/lu";
import { Point, ShapeStyle, StrokeStyle, StrokePattern } from "@opencop/elements";
import type { StyleSpec } from "@opencop/tools";

/** The shapes tools' formatting-bar inputs. */

// ---------------------------------------------------------------------------
// Shared spec builders
// ---------------------------------------------------------------------------

/** Stroke specs: color + weight + pattern (3 separate toolbar items). */
export const lineSpec: StyleSpec[] = [
    {
        label: "stroke color",
        labelKey: "toolbar.strokeColor",
        input: {
            type: "swatch",
            defaultValue: "#ff0000b3",
            onChange: (e, v) => {
                const s = e.getStyle();
                if (s instanceof StrokeStyle) s.setColor(v);
                if (s instanceof ShapeStyle) s.setStrokeColor(v);
            },
        },
    },
    {
        label: "stroke weight",
        labelKey: "toolbar.strokeWeight",
        icon: MdLineWeight,
        input: {
            type: "weight",
            min: 1,
            max: 10,
            step: 0.01,
            defaultValue: 3,
            onChange: (e, v) => {
                const s = e.getStyle();
                if (s instanceof StrokeStyle) s.setWeight(v);
                if (s instanceof ShapeStyle) s.setStrokeWeight(v);
            },
        },
    },
    {
        label: "stroke pattern",
        labelKey: "toolbar.strokePattern",
        icon: MdLineStyle,
        input: {
            type: "pattern",
            items: ["solid", "dashed", "dotted", "outlined"],
            defaultValue: "solid",
            onChange: (e, v) => {
                const s = e.getStyle();
                if (s instanceof StrokeStyle) s.setPattern(StrokePattern.fromName(v));
                if (s instanceof ShapeStyle) s.setStrokePattern(StrokePattern.fromName(v));
            },
        },
    },
];

/** Shape specs: fill color + stroke specs (4 separate toolbar items). */
export const shapeSpec: StyleSpec[] = [
    {
        label: "fill color",
        labelKey: "toolbar.fillColor",
        input: {
            type: "swatch",
            defaultValue: "#ff000033",
            onChange: (e, v) => {
                const s = e.getStyle();
                if (s instanceof ShapeStyle) s.setFillColor(v);
            },
        },
    },
    ...lineSpec,
];

// ---------------------------------------------------------------------------
// Per-tool specs
// ---------------------------------------------------------------------------

/** Style inputs for the point tool: fill colour and radius. */
export const pointSpec: StyleSpec[] = [
    {
        label: "fill color",
        labelKey: "toolbar.fillColor",
        input: {
            type: "swatch",
            defaultValue: "#ff0000b3",
            onChange: (e, v) => {
                if (Point.is(e)) e.getStyle().setColor(v);
            },
        },
    },
    {
        label: "radius",
        labelKey: "toolbar.radius",
        icon: LuRadius,
        input: {
            type: "number",
            min: 1,
            step: 1,
            defaultValue: 3,
            suffix: "px",
            onChange: (e, v) => {
                if (Point.is(e)) e.getStyle().setRadius(v);
            },
        },
    },
];
