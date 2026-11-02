// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Logger from "js-logger";
import { Point } from "@opencop/elements";
import {
    ELEMENT_STORE_ID,
    ToolError,
    createPointGrammar,
    type ToolDescriptor,
} from "@opencop/tools";
import { __resetToolRegistry, registerTool } from "~/features/contributions/tool-registry";
import { __resetContributionFailureLog } from "~/features/contributions/contribution-guard";
import { getInteractionState } from "./interaction.store";
import { activate, resetInteraction } from "./commands";
import { attachHost } from "../host";
import { fakeHost } from "~/test/interaction-runtime-harness";

/**
 * A throwing tool `grammar` factory is contained, and the two reasons it can throw stay
 * distinguishable.
 *
 * A params-derived `grammar` is contributed code fed the operator's current params, so a
 * throw is a question of blame, not just of recovery:
 *
 * - `ToolError` is the contract's declared refusal ("no drawable tool for these
 *   params"). Expected; the session refuses and nothing is reported.
 * - anything else is a defect in the subject. Contained and attributed to the
 *   tool, never rethrown — the nearest boundary above the interaction runtime is
 *   `MapView`, so escaping here replaces the whole map because one tool is broken.
 */

const THROWN = "tool grammar exploded";

function registerThrowingTool(id: string, error: unknown): void {
    registerTool({
        id,
        label: id,
        icon: () => null,
        produces: { store: ELEMENT_STORE_ID, type: "point" },
        draw: {
            grammar: () => {
                throw error;
            },
        },
    } satisfies ToolDescriptor);
}

let errors: string[];
let unconfigure: () => void;

beforeEach(() => {
    __resetToolRegistry();
    __resetContributionFailureLog();
    errors = [];
    vi.spyOn(Logger.get("plugins"), "error").mockImplementation((...args: unknown[]) => {
        errors.push(args.map(String).join(" "));
    });
    resetInteraction();
    unconfigure = attachHost(fakeHost().host);
});

afterEach(() => {
    resetInteraction();
    unconfigure();
    vi.restoreAllMocks();
    __resetToolRegistry();
    __resetContributionFailureLog();
});

describe("a throwing tool grammar factory is contained", () => {
    it("does not let a non-ToolError escape the command", () => {
        registerThrowingTool("brokenTool", new Error(THROWN));

        // Escaping here reaches `MapView`'s boundary and replaces the whole map.
        expect(() => activate("brokenTool")).not.toThrow();
    });

    it("refuses instead of opening a session with nothing bound to it", () => {
        registerThrowingTool("brokenTool", new Error(THROWN));

        const result = activate("brokenTool");

        expect(result).toMatchObject({ ok: false });
        expect(getInteractionState().session.kind).toBe("idle");
    });

    it("reports a defect as a contained contribution failure, attributed to the tool", () => {
        registerThrowingTool("opencop.symbology:brokenTool", new Error(THROWN));

        activate("opencop.symbology:brokenTool");

        const reported = errors.find((e) => e.includes("contribution"));
        expect(reported).toBeDefined();
        // Owner and phase, not the host frame it happened to throw in.
        expect(reported).toContain("opencop.symbology:opencop.symbology:brokenTool:grammar");
    });

    it("treats a ToolError as an expected refusal, not a defect", () => {
        registerThrowingTool("refusingTool", new ToolError("no drawable tool"));

        const result = activate("refusingTool");

        expect(result).toMatchObject({ ok: false });
        expect(getInteractionState().session.kind).toBe("idle");
        expect(errors.filter((e) => e.includes("contribution"))).toHaveLength(0);
    });

    it("leaves an already-open session untouched when the new tool's grammar throws", () => {
        registerTool({
            id: "goodTool",
            label: "good",
            icon: () => null,
            produces: { store: ELEMENT_STORE_ID, type: "point" },
            draw: {
                grammar: createPointGrammar({
                    create: (cursor) => Point.create({ position: cursor }),
                }),
            },
        } satisfies ToolDescriptor);
        registerThrowingTool("brokenTool", new Error(THROWN));

        expect(activate("goodTool")).toEqual({ ok: true });
        activate("brokenTool");

        // The broken activation aborted the previous session before resolving the
        // grammar, so the runtime lands on idle rather than a half-switched state.
        expect(getInteractionState().session.kind).toBe("idle");
    });
});
