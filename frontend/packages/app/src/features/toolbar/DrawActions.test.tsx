// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { act, fireEvent, render, screen } from "~/test";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createElement } from "react";
import { Line, Polygon } from "@opencop/elements";
import { ELEMENT_STORE_ID, polygonGrammar } from "@opencop/tools";
import { useHistoricalStore } from "~/features/historical/data-plane/historical.store";
import { type Session } from "~/features/map/interaction-runtime/session/interaction.store";
import { resetInteractionForTest, setSessionForTest } from "~/test/interaction";

import { LASSO_TOOL_ID, lassoDescriptor } from "~/plugins/lasso/lasso.descriptor";
import { measureDescriptor } from "~/plugins/measure/measure.descriptor";
import { MEASURE_TOOL_ID } from "~/plugins/measure/measure-session";
import { watchboxDescriptor } from "~/features/watchbox/watchbox.descriptor";
import {
    __resetToolRegistry,
    registerTool,
    type ToolDescriptor,
} from "~/features/contributions/tool-registry";
import { DrawActions } from "./DrawActions";

const { mockActivate, mockExit } = vi.hoisted(() => ({
    mockActivate: vi.fn(),
    mockExit: vi.fn(),
}));

vi.mock("~/features/map/interaction-runtime/session/commands", () => ({
    activate: (...args: unknown[]) => mockActivate(...args),
    exit: (...args: unknown[]) => mockExit(...args),
}));

/** A custody-less tool waiting for its first click. */
function waitingSession(descriptorId: string): Session {
    return {
        kind: "draw",
        descriptorId,
        epoch: 1,
        params: {},
        custody: false,
        draft: null,
        locked: 0,
    };
}

/** A draw with a placed draft — in flight. */
function inFlightSession(descriptorId: string, custody: boolean): Session {
    return {
        kind: "draw",
        descriptorId,
        epoch: 1,
        params: {},
        custody,
        draft: { object: Polygon.create({ positions: [0, 0, 1, 1, 1, 0] }), placed: true },
        locked: 1,
    };
}

function editSession(descriptorId: string, custody: boolean): Session {
    return {
        kind: "edit",
        descriptorId,
        epoch: 1,
        product: Line.create({ positions: [0, 0, 1, 1] }),
        ref: { sourceId: "local", objectId: "x" },
        custody,
        params: {},
        isNew: true,
        sub: { hotspot: null, dragging: null },
    };
}

/** A shape tool as `install-shapes` registers it: `topBarStart` location, its own group. */
const shapeDescriptor: ToolDescriptor = {
    id: "polygonTool",
    label: "polygon",
    icon: () => createElement("span"),
    group: "shapes",
    produces: { store: ELEMENT_STORE_ID, type: "polygon" },
    draw: { grammar: polygonGrammar },
};

function resetStores() {
    resetInteractionForTest();
    useHistoricalStore.setState({ mode: "live" });
    __resetToolRegistry();
}

beforeEach(() => {
    mockActivate.mockReset();
    mockExit.mockReset();
    act(() => {
        resetStores();
        // The interaction group as the app installs it (watchbox is flag-gated
        // and registered per test). A shape tool proves the group boundary.
        registerTool(measureDescriptor);
        registerTool(lassoDescriptor);
        registerTool(shapeDescriptor);
    });
});

afterEach(() => act(() => resetStores()));

describe("DrawActions", () => {
    it("renders Interact, then the interaction group in declared order — and no other topBarStart-location tool", () => {
        render(<DrawActions />);

        const names = screen.getAllByRole("button").map((b) => b.getAttribute("aria-label"));
        expect(names).toEqual(["Interact", "Lasso", "Measure"]);
        expect(screen.queryByRole("button", { name: "Polygon" })).not.toBeInTheDocument();
    });

    it("activates the lasso descriptor by id", () => {
        render(<DrawActions />);

        act(() => {
            fireEvent.click(screen.getByRole("button", { name: "Lasso" }));
        });

        expect(mockActivate).toHaveBeenCalledWith(LASSO_TOOL_ID);
    });

    it("activates measure with no params — the tool arms its default shape itself", () => {
        render(<DrawActions />);

        act(() => {
            fireEvent.click(screen.getByRole("button", { name: "Measure" }));
        });

        expect(mockActivate).toHaveBeenCalledWith(MEASURE_TOOL_ID);
    });

    it("clicking the armed tool again leaves it (toggle), as in the picker", () => {
        act(() => setSessionForTest(waitingSession(LASSO_TOOL_ID)));

        render(<DrawActions />);

        const lasso = screen.getByRole("button", { name: "Lasso" });
        expect(lasso).toHaveAttribute("aria-pressed", "true");
        act(() => {
            fireEvent.click(lasso);
        });

        expect(mockExit).toHaveBeenCalledOnce();
        expect(mockActivate).not.toHaveBeenCalled();
    });

    it("keeps the tools enabled while a tool is only waiting for its first click (switching is allowed)", () => {
        act(() => setSessionForTest(waitingSession(LASSO_TOOL_ID)));

        render(<DrawActions />);

        expect(screen.getByRole("button", { name: "Lasso" })).toBeEnabled();
        expect(screen.getByRole("button", { name: "Measure" })).toBeEnabled();
    });

    it("disables the tools while any draft is in flight — a shape's included (Escape abandons, a tool switch does not)", () => {
        act(() => setSessionForTest(inFlightSession("polygonTool", true)));

        render(<DrawActions />);

        expect(screen.getByRole("button", { name: "Lasso" })).toBeDisabled();
        expect(screen.getByRole("button", { name: "Measure" })).toBeDisabled();
        expect(screen.getByRole("button", { name: "Interact" })).toBeEnabled();
    });

    it("disables every button while an element is being edited (Save/Cancel must resolve it)", () => {
        act(() => setSessionForTest(editSession("polygonTool", true)));

        render(<DrawActions />);

        expect(screen.getByRole("button", { name: "Interact" })).toBeDisabled();
        expect(screen.getByRole("button", { name: "Lasso" })).toBeDisabled();
        expect(screen.getByRole("button", { name: "Measure" })).toBeDisabled();
    });

    it("over a finished measurement the tools disable, Measure stays lit, and Interact still leaves", () => {
        // A finished measurement is an edit session without custody: switching
        // tools is not a way to abandon it, but there is nothing to save, so
        // leaving is always legal. The button stays pressed so the operator
        // can tell the measurement on display still belongs to the tool.
        act(() => setSessionForTest(editSession(MEASURE_TOOL_ID, false)));

        render(<DrawActions />);

        const measure = screen.getByRole("button", { name: "Measure" });
        expect(screen.getByRole("button", { name: "Lasso" })).toBeDisabled();
        expect(measure).toBeDisabled();
        expect(measure).toHaveAttribute("aria-pressed", "true");
        const interact = screen.getByRole("button", { name: "Interact" });
        expect(interact).toBeEnabled();
        act(() => {
            fireEvent.click(interact);
        });
        expect(mockExit).toHaveBeenCalledOnce();
    });

    it("exits the session when Interact is clicked while measuring", () => {
        act(() => setSessionForTest(waitingSession(MEASURE_TOOL_ID)));

        render(<DrawActions />);

        act(() => {
            fireEvent.click(screen.getByRole("button", { name: "Interact" }));
        });

        expect(mockExit).toHaveBeenCalledOnce();
    });

    it("drops an interaction-group tool when its descriptor is removed", () => {
        // Availability is registration (interaction-rules.md Q3): the button
        // exists iff the descriptor does. Generic over lasso's own descriptor so
        // this pin doesn't depend on watchbox, whose own control moved off the
        // toolbar entirely (2026-10).
        const flagged: ToolDescriptor = {
            ...lassoDescriptor,
            id: "flaggedTool",
            label: "Flagged",
            labelKey: undefined,
            shortcut: undefined,
        };
        let dispose: () => void = () => {};
        act(() => {
            dispose = registerTool(flagged);
        });

        render(<DrawActions />);
        expect(screen.getByRole("button", { name: "Flagged" })).toBeInTheDocument();

        act(() => dispose());

        expect(screen.queryByRole("button", { name: "Flagged" })).not.toBeInTheDocument();
    });

    it("in historical mode the tools disable and keep their names (a control must not rename itself)", () => {
        act(() => useHistoricalStore.setState({ mode: "historical" }));

        render(<DrawActions />);

        const lasso = screen.getByRole("button", { name: "Lasso" });
        expect(lasso).toBeDisabled();
        expect(lasso).toHaveAttribute("aria-pressed", "false");
        expect(screen.getByRole("button", { name: "Measure" })).toBeDisabled();
    });

    describe("watchbox", () => {
        it('never offers a toolbar button, even while its descriptor is registered — it declares location "external" and is activated from its own panel instead', () => {
            act(() => {
                registerTool(watchboxDescriptor);
            });

            render(<DrawActions />);

            const names = screen.getAllByRole("button").map((b) => b.getAttribute("aria-label"));
            expect(names).toEqual(["Interact", "Lasso", "Measure"]);
            expect(screen.queryByRole("button", { name: "Watchbox" })).not.toBeInTheDocument();
        });
    });
});
