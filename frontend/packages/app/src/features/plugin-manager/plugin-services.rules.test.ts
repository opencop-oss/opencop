// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it } from "vitest";
import { PluginManager } from "@opencop/contributions/host";
import { SDK_API_VERSION, type PluginInstallContext } from "@opencop/contributions/plugin";

import {
    contributionValidators,
    errorsOf,
    formatProblem,
    validatePluginContributions,
    type Contributions,
    type ContributionKind,
} from "@opencop/contributions/validation";
import {
    CONTRIBUTION_FIXTURES,
    FIXTURE_PLUGIN_ID,
    FIXTURE_TRANSLATIONS,
    MANIFEST_FIXTURES,
    VALID_CONTRIBUTIONS,
    VALID_MANIFEST,
} from "@opencop/contributions/validation";
import { i18n } from "~/i18n/config";
import { getAllTools } from "~/features/contributions/tool-registry";
import { getElementMapperEntries } from "~/features/contributions/element-mapper-registry";
import { getRegisteredFilterFields } from "~/features/contributions/filter-field-registry";
import { createPluginHostServices } from "./plugin-services";

/**
 * The real host refuses a plugin for exactly the problems the shared validators list —
 * so a plugin whose own checks pass is one this host loads. Each fixture is loaded as a
 * plugin through the real {@link PluginManager} and the real host services.
 */

const MANIFEST = { ...VALID_MANIFEST, id: FIXTURE_PLUGIN_ID, apiVersion: SDK_API_VERSION };

/** The register method a plugin calls for each kind. */
const REGISTER = {
    tools: "registerTool",
    elementMappers: "registerElementMapper",
    translations: "registerTranslations",
    detailsHeaders: "registerDetailsHeader",
    filterFields: "registerFilterField",
} as const satisfies Record<ContributionKind, keyof PluginInstallContext>;

/** A plugin that contributes `items` of `kind`, with its own bundle alongside. */
function pluginOf(kind: ContributionKind, items: readonly object[]) {
    return {
        manifest: MANIFEST,
        install: (ctx: PluginInstallContext) => {
            if (kind !== "translations") ctx.registerTranslations(FIXTURE_TRANSLATIONS);
            const register = ctx[REGISTER[kind]] as (item: object) => void;
            for (const item of items) register(item);
        },
    };
}

const isPluginKind = (kind: string): kind is ContributionKind =>
    Object.hasOwn(contributionValidators, kind);

let manager = new PluginManager(createPluginHostServices());

afterEach(() => {
    for (const record of manager.list()) manager.unload(record.manifest.id);
    manager = new PluginManager(createPluginHostServices());
    expect(getAllTools().filter((tool) => tool.id.startsWith(FIXTURE_PLUGIN_ID))).toEqual([]);
    expect(
        getElementMapperEntries().filter((entry) => entry.ownerId === FIXTURE_PLUGIN_ID),
    ).toEqual([]);
    expect(
        getRegisteredFilterFields().filter((field) => field.id.startsWith(FIXTURE_PLUGIN_ID)),
    ).toEqual([]);
});

describe("the real host and the shared validators", () => {
    it.each(Object.keys(REGISTER))("load a plugin's valid %s contribution", (kind) => {
        const record = manager.load(
            pluginOf(kind as ContributionKind, [VALID_CONTRIBUTIONS[kind as ContributionKind]]),
        );
        expect(record).toMatchObject({ status: "active" });
    });

    it.each(
        CONTRIBUTION_FIXTURES.filter((fixture) => isPluginKind(fixture.kind)).map(
            (fixture) => [fixture.rule, fixture] as const,
        ),
    )("refuse a plugin breaking %s, for exactly the validators' problems", (_, fixture) => {
        const kind = fixture.kind as ContributionKind;
        const contributes = {
            translations: kind === "translations" ? [] : [FIXTURE_TRANSLATIONS],
            [kind]: fixture.items,
        } as Contributions;
        const expected = validatePluginContributions(contributes, FIXTURE_PLUGIN_ID, {
            translationExists: (key) => i18n.exists(key),
        });

        const record = manager.load(pluginOf(kind, fixture.items));

        expect(expected.map((problem) => problem.rule)).toContain(fixture.rule);
        const refused = errorsOf(expected);
        expect(record).toMatchObject(
            refused.length > 0
                ? { status: "failed", error: refused.map(formatProblem).join("; ") }
                : { status: "active" },
        );
        expect(record.problems.map((problem) => problem.rule)).toEqual(
            expected.map((problem) => problem.rule),
        );
    });

    it.each(
        MANIFEST_FIXTURES.filter((fixture) => !fixture.reservedOwnerIds).map(
            (fixture) => [fixture.rule, fixture] as const,
        ),
    )("refuse a manifest breaking %s", (_, fixture) => {
        const record = manager.load({ manifest: fixture.manifest, install: () => undefined });
        expect(record.status).not.toBe("active");
        expect(record.problems.map((problem) => problem.rule)).toContain(fixture.rule);
    });
});
