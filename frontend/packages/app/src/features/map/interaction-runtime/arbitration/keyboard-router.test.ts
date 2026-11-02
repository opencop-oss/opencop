// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/** The router's chord and Enter branches — who receives a Ctrl/Cmd chord, and Enter reaching the running draw's grammar — and the exception it states first: a focused text control holds every key. See `~/test/interaction-runtime-harness` for the fixtures. */
import { afterEach, describe, expect, it, vi } from "vitest";
import { registerTool } from "~/features/contributions/tool-registry";
import { activate } from "../session/commands";
import { getInteractionState } from "../session/interaction.store";
import { registerKeyBinding, type KeyBinding } from "./key-bindings";
import {
    attachRuntime,
    fakeDeps,
    lineFixture,
    installInteractionRuntimeHarness,
    mapMouse,
} from "~/test/interaction-runtime-harness";

installInteractionRuntimeHarness();

const disposers: Array<() => void> = [];
afterEach(() => {
    disposers.splice(0).forEach((dispose) => dispose());
    document.body.innerHTML = "";
});

function bind(canRun = true) {
    const binding: KeyBinding = {
        key: "c",
        modifier: "ctrlOrMeta",
        canRun: () => canRun,
        run: vi.fn(),
    };
    disposers.push(registerKeyBinding(binding));
    return binding;
}

function pressChord(): KeyboardEvent {
    const e = new KeyboardEvent("keydown", {
        key: "c",
        ctrlKey: true,
        cancelable: true,
        bubbles: true,
    });
    document.dispatchEvent(e);
    return e;
}

describe("a Ctrl/Cmd chord", () => {
    it("reaches the registered binding when idle, and the browser's default is prevented", () => {
        const deps = fakeDeps();
        const detach = attachRuntime(deps);
        const binding = bind();

        const e = pressChord();

        expect(binding.run).toHaveBeenCalledTimes(1);
        expect(e.defaultPrevented).toBe(true);
        detach();
    });

    it("is the browser's when the binding cannot act", () => {
        const deps = fakeDeps();
        const detach = attachRuntime(deps);
        const binding = bind(false);

        const e = pressChord();

        expect(binding.run).not.toHaveBeenCalled();
        expect(e.defaultPrevented).toBe(false);
        detach();
    });

    it("is not routed while a session is live — the session outranks every binding", () => {
        const deps = fakeDeps();
        const detach = attachRuntime(deps);
        const binding = bind();
        registerTool(lineFixture);
        expect(activate("fixtureLine")).toEqual({ ok: true });

        const e = pressChord();

        expect(binding.run).not.toHaveBeenCalled();
        expect(e.defaultPrevented).toBe(false);
        detach();
    });

    it("is not routed while a text control is focused", () => {
        const deps = fakeDeps();
        const detach = attachRuntime(deps);
        const binding = bind();
        const input = document.createElement("input");
        document.body.appendChild(input);
        input.focus();

        const e = pressChord();

        expect(binding.run).not.toHaveBeenCalled();
        expect(e.defaultPrevented).toBe(false);
        detach();
    });
});

describe("a focused text control holds every key (exceptions.ts, stated once at the top of the router)", () => {
    it("a letter shortcut typed into a text control does not arm the tool", () => {
        const deps = fakeDeps();
        const detach = attachRuntime(deps);
        registerTool({
            ...lineFixture,
            id: "fixtureShortcut",
            shortcut: "l",
        });
        const input = document.createElement("input");
        document.body.appendChild(input);
        input.focus();

        const e = new KeyboardEvent("keydown", { key: "l", cancelable: true, bubbles: true });
        document.dispatchEvent(e);

        expect(getInteractionState().session.kind).toBe("idle");
        expect(e.defaultPrevented).toBe(false);
        detach();
    });

    it("the same letter with focus on the map arms the tool", () => {
        const deps = fakeDeps();
        const detach = attachRuntime(deps);
        registerTool({
            ...lineFixture,
            id: "fixtureShortcut",
            shortcut: "l",
        });

        const e = new KeyboardEvent("keydown", { key: "l", cancelable: true, bubbles: true });
        document.dispatchEvent(e);

        expect(getInteractionState().session.kind).toBe("draw");
        expect(e.defaultPrevented).toBe(true);
        detach();
    });
});

describe("Enter", () => {
    function pressEnter(): KeyboardEvent {
        const e = new KeyboardEvent("keydown", { key: "Enter", cancelable: true, bubbles: true });
        document.dispatchEvent(e);
        return e;
    }

    /** A line draw with one point locked and the next following the cursor. */
    function drawLine() {
        const deps = fakeDeps();
        const detach = attachRuntime(deps);
        registerTool(lineFixture);
        expect(activate("fixtureLine")).toEqual({ ok: true });
        deps.fire("mousemove", mapMouse([0, 0], { x: 0, y: 0 }));
        deps.fire("click", mapMouse([0, 0], { x: 0, y: 0 }));
        return { deps, detach };
    }

    it("reaches the draw's grammar, which completes the line once it holds its minimum", () => {
        const { deps, detach } = drawLine();

        // One locked point and no cursor point beyond it: not yet a line.
        pressEnter();
        expect(getInteractionState().session.kind).toBe("draw");

        deps.fire("mousemove", mapMouse([1, 1], { x: 10, y: 10 }));
        pressEnter();
        expect(getInteractionState().session.kind).toBe("edit");
        detach();
    });

    it("typed into a text control does not reach the draw", () => {
        const { deps, detach } = drawLine();
        deps.fire("mousemove", mapMouse([1, 1], { x: 10, y: 10 }));
        const input = document.createElement("input");
        document.body.appendChild(input);
        input.focus();

        pressEnter();

        expect(getInteractionState().session.kind).toBe("draw");
        detach();
    });

    it("does nothing over an edit — the edit binds no Enter", () => {
        const { deps, detach } = drawLine();
        deps.fire("mousemove", mapMouse([1, 1], { x: 10, y: 10 }));
        pressEnter();
        const edit = getInteractionState().session;
        expect(edit.kind).toBe("edit");

        pressEnter();

        expect(getInteractionState().session).toBe(edit);
        detach();
    });
});
