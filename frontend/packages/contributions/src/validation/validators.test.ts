// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { CONTRIBUTION_RULES, PROBLEM_KINDS } from "./problem.ts";
import { validatePluginContributions } from "./validators.ts";
import { createContributionRecorder } from "./recorder.ts";

const PLUGIN_ID = "acme.test-plugin";

/** Only the plugin's own bundles resolve. */
const ENVIRONMENT = { translationExists: () => false };

// Deliberately malformed: these drive the runtime checks a plugin built without the
// protocol's types reaches.
const tool = (overrides: Record<string, unknown> = {}) =>
    ({
        id: "t",
        label: "T",
        icon: () => null,
        content: () => null,
        ...overrides,
    }) as never;

const mapper = (overrides: Record<string, unknown> = {}) =>
    ({ type: "test-type", map: () => [], ...overrides }) as never;

describe("validatePluginContributions", () => {
    it("accepts contributions that break no rule", () => {
        expect(validatePluginContributions({ tools: [tool()] }, PLUGIN_ID, ENVIRONMENT)).toEqual(
            [],
        );
    });

    it("reports every problem in one item, each with its rule and the item's id", () => {
        const problems = validatePluginContributions(
            { tools: [tool({ label: undefined, icon: undefined })] },
            PLUGIN_ID,
            ENVIRONMENT,
        );

        expect(problems).toEqual([
            {
                rule: "tools.label-missing",
                kind: "tools",
                id: "t",
                severity: "error",
                message: 'tool "t" label missing',
            },
            {
                rule: "tools.icon-missing",
                kind: "tools",
                id: "t",
                severity: "error",
                message: 'tool "t" icon missing',
            },
        ]);
    });

    it("stops at an item with no identity, since nothing after could name it", () => {
        const problems = validatePluginContributions(
            { tools: [tool({ id: "", label: undefined })] },
            PLUGIN_ID,
            ENVIRONMENT,
        );

        expect(problems.map((p) => p.rule)).toEqual(["tools.id-missing"]);
    });

    it("reports a duplicate once rather than repeating the first registration's problems", () => {
        const problems = validatePluginContributions(
            {
                elementMappers: [
                    mapper({ preview: "thumbnail" }),
                    mapper({ preview: "thumbnail" }),
                ],
            },
            PLUGIN_ID,
            ENVIRONMENT,
        );

        expect(
            problems.filter((p) => p.rule === "elementMappers.preview-not-function"),
        ).toHaveLength(1);
        expect(problems.filter((p) => p.rule === "elementMappers.duplicate")).toHaveLength(1);
    });

    it("reports problems from every kind together", () => {
        const problems = validatePluginContributions(
            {
                tools: [tool({ content: undefined })],
                translations: [{ namespace: "", resources: {} }],
            },
            PLUGIN_ID,
            ENVIRONMENT,
        );

        expect(problems.map((p) => p.rule)).toEqual([
            "tools.content-or-draw-missing",
            "translations.namespace-missing",
        ]);
    });
});

describe("CONTRIBUTION_RULES", () => {
    it("names each rule once, under its kind", () => {
        const kinds: readonly string[] = PROBLEM_KINDS;
        expect(new Set(CONTRIBUTION_RULES).size).toBe(CONTRIBUTION_RULES.length);
        for (const rule of CONTRIBUTION_RULES) expect(kinds).toContain(rule.split(".")[0]);
    });
});

describe("createContributionRecorder", () => {
    it("passes the base context through and records registrations instead of applying them", () => {
        const base = { storage: "the plugin's storage" };
        const recorder = createContributionRecorder(base);

        recorder.ctx.registerTool(tool());

        expect(recorder.ctx.storage).toBe("the plugin's storage");
        expect(recorder.contributes.tools).toHaveLength(1);
    });
});
