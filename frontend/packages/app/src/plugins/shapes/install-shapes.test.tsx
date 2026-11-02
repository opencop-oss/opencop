// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { lineSpec, shapeSpec } from "./style-specs";
import {
    circleGrammar,
    lineGrammar,
    pointGrammar,
    producedElementType,
    resolveGrammar,
} from "@opencop/tools";
import { createTestHost } from "@opencop/plugin-sdk/testing";
import { installShapes } from "./install-shapes";

/**
 * Equivalence net for the registry refactor: prove the unified registry resolves
 * each generic shape tool to the SAME grammar/style/element type the old
 * hand-synced records (`tools`, `toolSpec`, `toolForElementType`) did. This is
 * the seam the refactor could plausibly break; the draw FSM is unchanged, so if
 * these hold the FSM behaves identically.
 *
 * Other subjects contribute their own tools through their own installers and
 * cover them in their own tests — the shapes subject knows nothing about them.
 */
/** One of the tools shapes registered, in a fresh test host. */
const getTool = (id: string) =>
    createTestHost({ id: "opencop.shapes", install: installShapes }).contributions.tools.get(id);

describe("installShapes — grammar equivalence to the old `tools` record", () => {
    it.each([
        ["lineTool", lineGrammar],
        ["pointTool", pointGrammar],
        ["circleTool", circleGrammar],
    ])("%s resolves to its original Tool object", (id, tool) => {
        expect(resolveGrammar(getTool(id)!, {})).toBe(tool);
    });
});

describe("installShapes — style equivalence to the old `toolSpec` record", () => {
    it("carries the same style specs each tool had inline", () => {
        expect(getTool("lineTool")?.style).toBe(lineSpec);
        expect(getTool("polygonTool")?.style).toBe(shapeSpec);
    });
});

describe("installShapes — element-type derivation (was `toolForElementType`)", () => {
    // The host finds a type's tool by what each tool declares it produces.
    it.each([
        ["line", "lineTool"],
        ["circle", "circleTool"],
        ["polygon", "polygonTool"],
    ])("declares element type %s for %s", (type, id) => {
        expect(producedElementType(getTool(id)?.produces)).toBe(type);
    });
});

describe("installShapes — subject boundary", () => {
    it("contributes no other subject's tools (each subject installs its own)", () => {
        expect(getTool("symbolTool")).toBeUndefined();
        expect(getTool("graphicTool")).toBeUndefined();
    });
});
