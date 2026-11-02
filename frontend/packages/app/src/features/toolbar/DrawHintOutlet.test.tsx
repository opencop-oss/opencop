// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { act, render, screen } from "~/test";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Polygon } from "@opencop/elements";
import { polygonGrammar } from "@opencop/tools";
import { TbPolygon } from "react-icons/tb";
import { __resetToolRegistry, registerTool } from "~/features/contributions/tool-registry";
import { type Session } from "~/features/map/interaction-runtime/session/interaction.store";
import { watchboxDescriptor } from "~/features/watchbox/watchbox.descriptor";
import { resetInteractionForTest, setSessionForTest } from "~/test/interaction";
import { DrawHintOutlet } from "./DrawHintOutlet";
import { ToolEditorOutlet } from "./ToolEditorOutlet";

const EDITOR_TEXT = "editor-body";

// The editor outlet scopes each editor's session, which reaches the map manager
// to redraw an element; the map itself is not under test here.
vi.mock("~/features/map/MapManagerContext", () => {
    const mapManager = { update: vi.fn() };
    return { useMapManager: () => mapManager };
});

/**
 * The host shows the active session's hints in one slot for every tool,
 * resolved through the translation catalog, apart from the tool's editor.
 */

function draw(
    descriptorId: string,
    overrides: Partial<Extract<Session, { kind: "draw" }>> = {},
): Session {
    return {
        kind: "draw",
        descriptorId,
        epoch: 1,
        params: {},
        custody: false,
        locked: 0,
        draft: null,
        ...overrides,
    };
}

/** A finished product held open for adjustment, without custody (as a watchbox's is). */
function edit(descriptorId: string): Session {
    return {
        kind: "edit",
        descriptorId,
        epoch: 1,
        product: Polygon.create({ positions: [0, 0, 1, 1, 1, 0] }),
        custody: false,
        params: {},
        isNew: true,
        sub: { hotspot: null, dragging: null },
    };
}

function reset() {
    __resetToolRegistry();
    resetInteractionForTest();
}

beforeEach(() => act(() => reset()));
afterEach(() => act(() => reset()));

describe("DrawHintOutlet", () => {
    it("shows the behaviour's default hint for an armed tool", () => {
        registerTool({
            id: "polygonTool",
            label: "polygon",
            icon: TbPolygon,
            draw: { grammar: polygonGrammar },
        });
        act(() => setSessionForTest(draw("polygonTool")));

        render(<DrawHintOutlet />);

        expect(screen.getByText("Click the map to place the first point.")).toBeInTheDocument();
    });

    it("shows a tool's hints alongside its editor — the two are separate slots", () => {
        registerTool({
            id: "polygonTool",
            label: "polygon",
            icon: TbPolygon,
            draw: { grammar: polygonGrammar },
            editor: () => <div>{EDITOR_TEXT}</div>,
        });
        act(() => setSessionForTest(draw("polygonTool")));

        render(
            <>
                <DrawHintOutlet />
                <ToolEditorOutlet />
            </>,
        );

        expect(screen.getByText("Click the map to place the first point.")).toBeInTheDocument();
        expect(screen.getByText(EDITOR_TEXT)).toBeInTheDocument();
    });

    it("shows the polygon's instructions for a watchbox while placing, and none once finished", () => {
        registerTool(watchboxDescriptor);
        act(() =>
            setSessionForTest(
                draw(watchboxDescriptor.id, {
                    locked: 2,
                    draft: {
                        object: Polygon.create({ positions: [0, 0, 1, 1, 1, 0] }),
                        placed: true,
                    },
                }),
            ),
        );

        render(<DrawHintOutlet />);

        expect(
            screen.getByText("Click to add points; double-click or press Enter to finish."),
        ).toBeInTheDocument();
        expect(screen.getByText("Right-click to undo the last point.")).toBeInTheDocument();

        act(() => setSessionForTest(edit(watchboxDescriptor.id)));
        // The watchbox form takes over once the polygon is finished: no host edit lines.
        expect(screen.queryByText("Right-click to undo the last point.")).not.toBeInTheDocument();
        expect(screen.queryByText(/Drag a handle/)).not.toBeInTheDocument();
    });

    it("is a polite live region, not an alert", () => {
        registerTool({
            id: "polygonTool",
            label: "polygon",
            icon: TbPolygon,
            draw: { grammar: polygonGrammar },
        });
        act(() => setSessionForTest(draw("polygonTool")));

        render(<DrawHintOutlet />);

        const region = screen
            .getByText("Click the map to place the first point.")
            .closest("[aria-live]");
        expect(region).toHaveAttribute("aria-live", "polite");
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    it("contains a tool whose hints throw — the outlet shows nothing and the map survives", () => {
        registerTool({
            id: "brokenTool",
            label: "broken",
            icon: TbPolygon,
            draw: {
                grammar: polygonGrammar,
                hints: () => {
                    throw new Error("boom");
                },
            },
        });
        act(() => setSessionForTest(draw("brokenTool")));

        expect(() => render(<DrawHintOutlet />)).not.toThrow();
        expect(
            screen.queryByText("Click the map to place the first point."),
        ).not.toBeInTheDocument();
    });

    it("shows nothing while idle", () => {
        registerTool({
            id: "polygonTool",
            label: "polygon",
            icon: TbPolygon,
            draw: { grammar: polygonGrammar },
        });

        render(<DrawHintOutlet />);

        expect(
            screen.queryByText("Click the map to place the first point."),
        ).not.toBeInTheDocument();
    });
});
