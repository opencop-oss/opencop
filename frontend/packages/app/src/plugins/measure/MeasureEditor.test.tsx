// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { noop } from "lodash";
import { PolylineGeometry } from "@opencop/elements";
import type { ToolSession } from "@opencop/tools";
import { createTestHost } from "@opencop/plugin-sdk/testing";
import { summarizeLine, type MeasurementSystem } from "@opencop/utils";
import { HOST_TRANSLATIONS } from "~/test/host-translations";
import { installMeasure } from "./install-measure";
import { MEASURE_TOOL_ID, type MeasureShape } from "./measure-session";
import { MEASURE_PRODUCT_TYPE, type Measurement } from "./measurement.model";

/**
 * The measure editor reads only its scoped session and the display preferences —
 * the two things the host opens around it — so it renders in a test host, as measure
 * registers it, with neither the map nor the interaction runtime.
 */

/** Measure's session while armed with `shape`, with `draft` drawn so far. */
function measuring(shape: MeasureShape | null, draft: Measurement | null = null): ToolSession {
    return {
        draft,
        params: { shape },
        phase: "draw",
        setParams: vi.fn(),
        invalidate: noop,
        activate: noop,
        cancel: noop,
    };
}

function line(...positions: number[]): Measurement<PolylineGeometry> {
    return { key: "m", type: MEASURE_PRODUCT_TYPE, geometry: new PolylineGeometry(positions, 2) };
}

/** A line's length as the summary shows it. */
function lengthOf(draft: Measurement<PolylineGeometry>, system: MeasurementSystem): string {
    const summary = summarizeLine(draft.geometry, system);
    if (!summary) throw new Error("a line needs two positions");
    return summary[0];
}

/** Measure installed in a test host, its editor rendered under `session` and `measurementSystem`. */
function renderEditor(session: ToolSession, measurementSystem: MeasurementSystem = "METRIC") {
    const host = createTestHost({
        id: "opencop.measure",
        install: installMeasure,
        translations: [HOST_TRANSLATIONS],
    });
    host.render(host.contributions.tools.get(MEASURE_TOOL_ID)!.editor!, {
        capabilities: {
            toolSession: session,
            displayPreferences: { measurementSystem, angleUnit: "degrees", angleOrientation: "cw" },
        },
    });
    return host;
}

describe("MeasureEditor shape toggles", () => {
    it("clicking an inactive shape re-arms with it (params restart)", async () => {
        const session = measuring("line");
        renderEditor(session);

        await userEvent.click(screen.getByRole("button", { name: "Circle" }));

        expect(session.setParams).toHaveBeenCalledWith({ shape: "circle" });
    });

    it("clicking the currently active shape clears it", async () => {
        const session = measuring("line");
        renderEditor(session);

        await userEvent.click(screen.getByRole("button", { name: "Line" }));

        expect(session.setParams).toHaveBeenCalledWith({ shape: null });
    });

    it("marks the armed shape's toggle as pressed", () => {
        renderEditor(measuring("polygon"));

        expect(screen.getByRole("button", { name: "Polygon" })).toHaveAttribute(
            "aria-pressed",
            "true",
        );
        expect(screen.getByRole("button", { name: "Line" })).toHaveAttribute(
            "aria-pressed",
            "false",
        );
    });
});

describe("MeasureEditor summary", () => {
    it.each(["METRIC", "IMPERIAL"] as const)("shows a line's length in %s", (system) => {
        const draft = line(0, 0, 0, 1);
        const expected = lengthOf(draft, system);

        renderEditor(measuring("line", draft), system);

        expect(screen.getByText(expected)).toBeInTheDocument();
    });

    it("recomputes when the revision changes after the draft is mutated in place", () => {
        const draft = line(0, 0, 0, 1);
        const session = measuring("line", draft);
        const host = renderEditor(session);
        const before = lengthOf(draft, "METRIC");
        expect(screen.getByText(before)).toBeInTheDocument();

        // A vertex drag mutates the draft in place; only the revision says so.
        draft.geometry.push([0, 2]);
        const after = lengthOf(draft, "METRIC");
        host.update();

        expect(screen.getByText(after)).toBeInTheDocument();
        expect(screen.queryByText(before)).not.toBeInTheDocument();
    });
});
