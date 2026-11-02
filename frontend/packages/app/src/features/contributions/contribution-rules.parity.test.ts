// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import Logger from "js-logger";
import {
    validateContextMenuItem,
    validateDetailsEditForm,
    validateDetailsHeader,
    validateDetailsInfoSection,
    validateDetailsPosition,
    validateElementMapper,
    validateFilterField,
    validateTool,
    type Problem,
    type ProblemKind,
} from "@opencop/contributions/validation";
import {
    CONTRIBUTION_FIXTURES,
    FIXTURE_TRANSLATIONS,
    VALID_CONTRIBUTIONS,
} from "@opencop/contributions/validation";
import { i18n } from "~/i18n/config";
import { nativeOwner } from "./contribution-guard";
import { registerTool } from "./tool-registry";
import { registerElementMapper } from "./element-mapper-registry";
import {
    registerDetailsEditForm,
    registerDetailsHeader,
    registerDetailsInfoSection,
    registerDetailsPosition,
} from "./details-registry";
import { registerFilterField } from "./filter-field-registry";
import { registerContextMenuItem } from "./context-menu-registry";

/**
 * The app registries refuse a native contribution for exactly the shared rules a
 * plugin's test host reports — the fixtures every owner must meet, run through the
 * real registries. The plugin path's half is `plugin-manager/plugin-services.rules.test.ts`.
 */

const OWNER = nativeOwner("opencop.fixture");
const ENVIRONMENT = { translationExists: (key: string) => i18n.exists(key) };

/** Each kind's registry, and the shared rule it applies. Translations are plugin-only. */
const REGISTRIES: Partial<
    Record<
        ProblemKind,
        { register: (item: never) => () => void; rules: (item: never) => Problem[] }
    >
> = {
    tools: { register: (item) => registerTool(item), rules: validateTool },
    elementMappers: {
        register: (item) => registerElementMapper(OWNER, item),
        rules: validateElementMapper,
    },
    detailsHeaders: {
        register: (item) => registerDetailsHeader(OWNER, item),
        rules: validateDetailsHeader,
    },
    filterFields: {
        register: (item) => registerFilterField(OWNER, item),
        rules: (item) => validateFilterField(item, ENVIRONMENT),
    },
    detailsPositions: {
        register: (item) => registerDetailsPosition(OWNER, item),
        rules: validateDetailsPosition,
    },
    detailsEditForms: {
        register: (item) => registerDetailsEditForm(OWNER, item),
        rules: validateDetailsEditForm,
    },
    detailsInfoSections: {
        register: (item) => registerDetailsInfoSection(OWNER, item),
        rules: validateDetailsInfoSection,
    },
    contextMenuItems: {
        register: (item) => registerContextMenuItem(OWNER, item),
        rules: validateContextMenuItem,
    },
};

const disposers: Array<() => void> = [];

beforeAll(() => {
    // As a native's strings would be: in the host's own instance.
    for (const [language, resources] of Object.entries(FIXTURE_TRANSLATIONS.resources))
        i18n.addResourceBundle(language, FIXTURE_TRANSLATIONS.namespace, resources);
});

afterAll(() => {
    for (const language of Object.keys(FIXTURE_TRANSLATIONS.resources))
        i18n.removeResourceBundle(language, FIXTURE_TRANSLATIONS.namespace);
});

afterEach(() => {
    for (const dispose of disposers.splice(0)) dispose();
    vi.restoreAllMocks();
});

describe("the app registries and the shared rules", () => {
    it.each(Object.keys(REGISTRIES))("accept the valid %s contribution", (kind) => {
        const registry = REGISTRIES[kind as ProblemKind]!;
        disposers.push(registry.register(VALID_CONTRIBUTIONS[kind as ProblemKind] as never));
    });

    it.each(
        CONTRIBUTION_FIXTURES.filter((fixture) => fixture.owners === "any").map(
            (fixture) => [fixture.rule, fixture] as const,
        ),
    )("refuse a native contribution breaking %s, with the rule's message", (_, fixture) => {
        vi.spyOn(Logger.get("plugins"), "error").mockImplementation(() => undefined);
        const registry = REGISTRIES[fixture.kind]!;
        const [item] = fixture.items;
        const problem = registry.rules(item as never).find((p) => p.rule === fixture.rule);

        expect(problem).toBeDefined();
        expect(() => disposers.push(registry.register(item as never))).toThrow(problem!.message);
    });
});
