// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import {
    CONTRIBUTION_RULES,
    contributionValidators,
    validateContextMenuItem,
    validateDetailsEditForm,
    validateDetailsHeader,
    validateDetailsInfoSection,
    validateDetailsPosition,
    validateElementMapper,
    validateFilterField,
    validatePluginContributions,
    validateTool,
    withContributedTranslations,
    type Contributions,
    type Problem,
    type ProblemKind,
    type ValidationEnvironment,
} from "./index.ts";
import {
    CONTRIBUTION_FIXTURES,
    FIXTURE_PLUGIN_ID,
    FIXTURE_TRANSLATIONS,
    VALID_CONTRIBUTIONS,
    type ContributionFixture,
} from "./contribution-fixtures.ts";

/** The host knows no key; the fixture plugin's own bundle supplies every one that resolves. */
const ENVIRONMENT: ValidationEnvironment = withContributedTranslations(
    { translationExists: () => false },
    [FIXTURE_TRANSLATIONS],
);

/** Each kind's per-item rules, the ones every owner meets. Translations have none. */
const ITEM_RULES: Partial<Record<ProblemKind, (item: never) => Problem[]>> = {
    tools: validateTool,
    elementMappers: validateElementMapper,
    detailsHeaders: validateDetailsHeader,
    filterFields: (field) => validateFilterField(field, ENVIRONMENT),
    detailsPositions: validateDetailsPosition,
    detailsEditForms: validateDetailsEditForm,
    detailsInfoSections: validateDetailsInfoSection,
    contextMenuItems: validateContextMenuItem,
};

const isPluginKind = (kind: ProblemKind): kind is keyof Contributions =>
    Object.hasOwn(contributionValidators, kind);

/** The plugin path, with the fixture's translations registered alongside, as a plugin would. */
function onPluginPath(fixture: ContributionFixture): string[] {
    const contributes = {
        translations: fixture.kind === "translations" ? [] : [FIXTURE_TRANSLATIONS],
        [fixture.kind]: fixture.items,
    } as Contributions;
    return validatePluginContributions(contributes, FIXTURE_PLUGIN_ID, ENVIRONMENT).map(
        (problem) => problem.rule,
    );
}

const label = (fixture: ContributionFixture) => `${fixture.rule} (${fixture.owners})`;

describe("the valid contributions", () => {
    it.each(Object.entries(VALID_CONTRIBUTIONS))("%s breaks no rule", (kind, item) => {
        const rules = ITEM_RULES[kind as ProblemKind];
        if (rules) expect(rules(item as never)).toEqual([]);
        if (isPluginKind(kind as ProblemKind))
            expect(
                validatePluginContributions(
                    { translations: [FIXTURE_TRANSLATIONS], [kind]: [item] } as Contributions,
                    FIXTURE_PLUGIN_ID,
                    ENVIRONMENT,
                ),
            ).toEqual([]);
    });
});

describe("each fixture", () => {
    it.each(CONTRIBUTION_FIXTURES.map((fixture) => [label(fixture), fixture] as const))(
        "%s breaks exactly its rule",
        (_, fixture) => {
            const rules = ITEM_RULES[fixture.kind];
            if (fixture.owners === "any") {
                expect(rules, `${fixture.kind} has no per-item rules`).toBeDefined();
                expect(
                    fixture.items.flatMap((item) => rules!(item as never)).map((p) => p.rule),
                ).toEqual([fixture.rule]);
            } else if (rules) {
                // A plugin-only rule: the rules every owner meets do not refuse it.
                expect(fixture.items.flatMap((item) => rules(item as never))).toEqual([]);
            }
            if (isPluginKind(fixture.kind))
                expect(onPluginPath(fixture).sort()).toEqual(
                    [fixture.rule, ...(fixture.alsoOnPluginPath ?? [])].sort(),
                );
        },
    );
});

describe("rule coverage", () => {
    it("has a fixture for every rule", () => {
        const covered = new Set(CONTRIBUTION_FIXTURES.map((fixture) => fixture.rule));
        expect(CONTRIBUTION_RULES.filter((rule) => !covered.has(rule))).toEqual([]);
    });

    it("files each fixture under its rule's kind", () => {
        for (const fixture of CONTRIBUTION_FIXTURES)
            expect(fixture.rule.startsWith(`${fixture.kind}.`), fixture.rule).toBe(true);
    });
});
