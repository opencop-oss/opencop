// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { screen } from "@testing-library/react";
import { createTestHost } from "@opencop/plugin-sdk/testing";
import symbologyPlugin from "../plugin";
import type { ToolSession } from "@opencop/tools";

/** The symbol tool's editor, as the plugin registers it, under a session. */
function renderSymbolEditor(toolSession: Partial<ToolSession>) {
    const host = createTestHost({ plugin: symbologyPlugin });
    return host.render(host.contributions.tools.get("opencop.symbology:symbolTool")!.editor!, {
        capabilities: { toolSession },
    });
}
import { describe, expect, it, vi } from "vitest";
import { Symbol } from "@opencop/elements";
import { SIDC } from "@opencop/symbology";

/**
 * Some codes in the symbol slot are not SIDCs — media icons imported from a KMZ are
 * today's case — so the SIDC editor can't meaningfully edit them and shows a
 * read-only notice instead.
 *
 * The editor decides this from symbology's own domain (`SIDC.isSidcString`), not by
 * asking whether the code belongs to the media-icons feature, so the behaviour holds
 * for any other scheme that ever shares the slot.
 *
 * This behaviour arrived on `main` in the ToolPicker, back when the toolbar
 * hardcoded the symbol editor. It now lives in symbology's own `SymbolToolEditor`
 * (the `editor` on its `symbolTool` descriptor), so the toolbar no longer knows
 * what a symbol is — these tests moved with it.
 */

const MOCK_SYMBOL_EDITOR_LABEL = "Mock Symbol Editor";
const MEDIA_ICON_CODE = "opencop:url:/v1/layers/abc/media/def#32x32";

vi.mock("./SymbolEditor", () => ({
    SymbolEditor: () => <div>{MOCK_SYMBOL_EDITOR_LABEL}</div>,
}));

/** The session an editor sees while drawing or editing a symbol with this code. */
const drawingSymbol = (phase: "draw" | "edit", sidc: string) => ({
    phase,
    draft: Symbol.create({ position: [0, 0], sidc }),
});

describe("SymbolToolEditor", () => {
    it("shows the SIDC editor for normal symbol codes", () => {
        renderSymbolEditor(drawingSymbol("edit", SIDC.default().toString()));

        expect(screen.getByText(MOCK_SYMBOL_EDITOR_LABEL)).not.toBeNull();
        expect(screen.queryByText(/isn't an SIDC/i)).toBeNull();
    });

    it("shows a read-only notice instead of the SIDC editor for a non-SIDC code", () => {
        renderSymbolEditor(drawingSymbol("edit", MEDIA_ICON_CODE));

        expect(screen.queryByText(MOCK_SYMBOL_EDITOR_LABEL)).toBeNull();
        expect(screen.getByText(/isn't an SIDC/i)).not.toBeNull();
    });

    it("shows the read-only notice while drawing an element with a non-SIDC code", () => {
        renderSymbolEditor(drawingSymbol("draw", MEDIA_ICON_CODE));

        expect(screen.queryByText(MOCK_SYMBOL_EDITOR_LABEL)).toBeNull();
        expect(screen.getByText(/isn't an SIDC/i)).not.toBeNull();
    });
});
