// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { act, render, screen } from "~/test";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Point } from "@opencop/elements";
import { useToolSession, useToolSessionRevision } from "@opencop/tools";
import { TbPointFilled } from "react-icons/tb";
import { __resetToolRegistry, registerTool } from "~/features/contributions/tool-registry";
import { type Session } from "~/features/map/interaction-runtime/session/interaction.store";
import { bumpRevForTest, resetInteractionForTest, setSessionForTest } from "~/test/interaction";
import { ToolEditorOutlet } from "./ToolEditorOutlet";

/**
 * The host owns editor placement: a subject declares `editor` on its descriptor
 * and the outlet decides whether and where it renders. ONE rule for every
 * session — draw and edit, with or without custody, all mount the active
 * descriptor's editor, so measure arrives by the same declaration as symbology
 * (the outlet has no hardcoded branches).
 */

const EDITOR_TEXT = "editor-body";

vi.mock("~/features/map/MapManagerContext", () => {
    // One object, as the real provider gives: a fresh one per render would
    // change the scoped session on every render.
    const mapManager = { update: vi.fn() };
    return { useMapManager: () => mapManager };
});

function registerEditorTool(id: string, withEditor = true) {
    registerTool({
        id,
        label: id,
        icon: TbPointFilled,
        ...(withEditor ? { editor: () => <div>{EDITOR_TEXT}</div> } : {}),
    });
}

function drawSession(descriptorId: string): Session {
    return {
        kind: "draw",
        descriptorId,
        epoch: 1,
        params: {},
        custody: true,
        locked: 0,
        draft: null,
    };
}

function editSession(descriptorId: string, custody = true): Session {
    return {
        kind: "edit",
        descriptorId,
        epoch: 1,
        product: Point.create({ position: [0, 0] }),
        ref: { sourceId: "local", objectId: "x" },
        custody,
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

describe("ToolEditorOutlet — the host places a subject's editor", () => {
    it.each([
        ["draw", drawSession("editorTool")],
        ["custody-less draw", { ...drawSession("editorTool"), custody: false } as Session],
        ["edit", editSession("editorTool")],
        ["custody-less edit", editSession("editorTool", false)],
    ] as const)("renders the active descriptor's editor during a %s session", (_kind, session) => {
        registerEditorTool("editorTool");
        act(() => setSessionForTest(session));

        render(<ToolEditorOutlet />);

        expect(screen.getByText(EDITOR_TEXT)).toBeInTheDocument();
    });

    it("renders nothing while idle, even with the tool still registered", () => {
        registerEditorTool("editorTool");

        render(<ToolEditorOutlet />);

        expect(screen.queryByText(EDITOR_TEXT)).not.toBeInTheDocument();
    });

    it("renders nothing for a tool that declares no editor", () => {
        registerEditorTool("plainTool", false);
        act(() => setSessionForTest(drawSession("plainTool")));

        render(<ToolEditorOutlet />);

        expect(screen.queryByText(EDITOR_TEXT)).not.toBeInTheDocument();
    });
});

describe("ToolEditorOutlet — scopes the editor's session to its tool", () => {
    it("gives the editor its own tool's session", () => {
        function PhaseEditor() {
            return <div>{`phase:${useToolSession().phase}`}</div>;
        }
        registerTool({
            id: "editorTool",
            label: "editorTool",
            icon: TbPointFilled,
            editor: PhaseEditor,
        });
        act(() => setSessionForTest(editSession("editorTool")));

        render(<ToolEditorOutlet />);

        expect(screen.getByText("phase:edit")).toBeInTheDocument();
    });

    it("re-renders on a geometry change only the parts that read the revision", () => {
        const sessionRendered = vi.fn();
        const revisionRendered = vi.fn();
        function SessionReader() {
            useToolSession();
            sessionRendered();
            return null;
        }
        function RevisionReader() {
            useToolSessionRevision();
            revisionRendered();
            return null;
        }
        function Editor() {
            return (
                <>
                    <SessionReader />
                    <RevisionReader />
                </>
            );
        }
        registerTool({
            id: "editorTool",
            label: "editorTool",
            icon: TbPointFilled,
            editor: Editor,
        });
        act(() => setSessionForTest(drawSession("editorTool")));
        render(<ToolEditorOutlet />);
        const sessionRenders = sessionRendered.mock.calls.length;
        const revisionRenders = revisionRendered.mock.calls.length;

        act(() => bumpRevForTest());
        act(() => bumpRevForTest());
        act(() => bumpRevForTest());

        expect(sessionRendered).toHaveBeenCalledTimes(sessionRenders);
        expect(revisionRendered).toHaveBeenCalledTimes(revisionRenders + 3);
    });
});
