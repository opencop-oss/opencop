// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { withContributedTranslations } from "./environment.ts";
import { validateFilterField } from "./rules.ts";

const NOTHING_RESOLVES = { translationExists: () => false };

const field = (overrides: Record<string, unknown> = {}) =>
    ({
        id: "acme.plugin:sector",
        labelKey: "acme.plugin:sector",
        path: "properties.opencop.element.symbol",
        order: 60,
        ...overrides,
    }) as never;

describe("validateFilterField", () => {
    it("asks the environment whether its keys resolve", () => {
        expect(validateFilterField(field(), { translationExists: () => true })).toEqual([]);
        expect(validateFilterField(field(), NOTHING_RESOLVES).map((p) => p.rule)).toEqual([
            "filterFields.label-key-unresolved",
        ]);
    });

    it("reports every problem, not the first", () => {
        const rules = validateFilterField(
            field({ id: "kind", path: "properties.opencop.location", order: Number.NaN }),
            { translationExists: () => true },
        ).map((p) => p.rule);

        expect(rules).toEqual([
            "filterFields.id-reserved",
            "filterFields.path-not-filterable",
            "filterFields.order-missing",
        ]);
    });
});

describe("withContributedTranslations", () => {
    const bundle = {
        namespace: "acme.plugin",
        resources: { en: { sector: "Sector", nested: { label: "Nested" }, "flat.key": "Flat" } },
    };
    const environment = withContributedTranslations(NOTHING_RESOLVES, [bundle]);

    it("resolves a key in a contributed bundle, flat or nested", () => {
        expect(environment.translationExists("acme.plugin:sector")).toBe(true);
        expect(environment.translationExists("acme.plugin:nested.label")).toBe(true);
        expect(environment.translationExists("acme.plugin:flat.key")).toBe(true);
    });

    it("does not resolve a key the bundle lacks, or one in another namespace", () => {
        expect(environment.translationExists("acme.plugin:nowhere")).toBe(false);
        expect(environment.translationExists("acme.plugin:nested")).toBe(false);
        expect(environment.translationExists("other:sector")).toBe(false);
        // Unprefixed: the default namespace, which this bundle is not.
        expect(environment.translationExists("sector")).toBe(false);
    });

    it("looks an unprefixed key up in the default namespace", () => {
        const host = withContributedTranslations(NOTHING_RESOLVES, [
            { namespace: "translation", resources: { en: { filter: { kind: "Kind" } } } },
        ]);
        expect(host.translationExists("filter.kind")).toBe(true);
    });

    it("still asks the host", () => {
        const host = withContributedTranslations({ translationExists: () => true }, [bundle]);
        expect(host.translationExists("filter.fields.kind")).toBe(true);
    });
});
