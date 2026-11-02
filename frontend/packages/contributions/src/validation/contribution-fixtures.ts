// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { TranslationContribution } from "../translation.ts";
import type { ContributionRule, ProblemKind } from "./problem.ts";

/** The plugin id every fixture is contributed under. */
export const FIXTURE_PLUGIN_ID = "acme.fixture";

const FIXTURE_TYPE = "fixture-type";
const noop = () => null;

/**
 * The fixture plugin's own bundle. Every key a fixture means to resolve is in it, so a
 * key resolves wherever the fixture's translations are registered — and only there.
 */
export const FIXTURE_TRANSLATIONS: TranslationContribution = {
    namespace: FIXTURE_PLUGIN_ID,
    resources: { en: { label: "Fixture label", notice: "Fixture notice" } },
};

/**
 * One contribution of each kind that breaks no rule, for any owner or a plugin
 * contributing under {@link FIXTURE_PLUGIN_ID}. Each fixture below breaks exactly one.
 */
export const VALID_CONTRIBUTIONS = {
    tools: { id: "fixture-tool", label: "Fixture", icon: noop, content: noop },
    elementMappers: { type: FIXTURE_TYPE, map: () => [] },
    translations: FIXTURE_TRANSLATIONS,
    detailsHeaders: { type: FIXTURE_TYPE },
    filterFields: {
        id: `${FIXTURE_PLUGIN_ID}:field`,
        labelKey: `${FIXTURE_PLUGIN_ID}:label`,
        path: "properties.opencop.element.symbol",
        order: 60,
    },
    detailsPositions: { type: FIXTURE_TYPE, position: noop },
    detailsEditForms: { id: "fixture-form", type: FIXTURE_TYPE, order: 1, Component: noop },
    detailsInfoSections: { id: "fixture-section", type: FIXTURE_TYPE, order: 1, Component: noop },
    contextMenuItems: {
        id: "fixture-item",
        target: { kind: "ground" },
        labelKey: `${FIXTURE_PLUGIN_ID}:label`,
        activate: { toolId: "fixture-tool" },
    },
} as const satisfies Record<ProblemKind, object>;

/**
 * A contribution that breaks one rule. `owners: "any"` is a rule every owner's
 * contribution meets — the app registries refuse it from a native too; `"plugins"`
 * is a rule only the plugin path applies (its namespace, an id used twice, the shape a
 * plugin may use today). The plugin path reports `rule`, plus `alsoOnPluginPath` where
 * one broken item necessarily breaks a plugin-only rule as well.
 */
export interface ContributionFixture {
    readonly rule: ContributionRule;
    readonly kind: ProblemKind;
    readonly owners: "any" | "plugins";
    /** The contributions, in order; most fixtures are one item. */
    readonly items: readonly object[];
    readonly alsoOnPluginPath?: readonly ContributionRule[];
}

/** {@link VALID_CONTRIBUTIONS}'s item of `kind`, with `overrides`. */
function broken<K extends ProblemKind>(kind: K, overrides: Record<string, unknown>): object {
    return { ...VALID_CONTRIBUTIONS[kind], ...overrides };
}

const one = (
    rule: ContributionRule,
    kind: ProblemKind,
    owners: ContributionFixture["owners"],
    overrides: Record<string, unknown>,
    alsoOnPluginPath?: readonly ContributionRule[],
): ContributionFixture => ({
    rule,
    kind,
    owners,
    items: [broken(kind, overrides)],
    ...(alsoOnPluginPath ? { alsoOnPluginPath } : null),
});

/**
 * One fixture or more per rule in `CONTRIBUTION_RULES`, each with the problem it must
 * produce. The validators' tests and the app's run the same set — the app through its
 * real registries and plugin manager — so "the kit passes" and "the host accepts" stay
 * one statement; a coverage test fails for a rule with no fixture here.
 */
export const CONTRIBUTION_FIXTURES: readonly ContributionFixture[] = [
    one("tools.id-missing", "tools", "any", { id: "" }),
    one("tools.production-invalid", "tools", "any", { produces: { store: "elements", type: "" } }),
    one("tools.label-missing", "tools", "plugins", { label: undefined }),
    one("tools.icon-missing", "tools", "plugins", { icon: undefined }),
    one("tools.location-unavailable", "tools", "plugins", { location: "external" }),
    one("tools.content-or-draw-missing", "tools", "plugins", { content: undefined }),
    one("tools.grammar-missing", "tools", "plugins", { content: undefined, draw: {} }),
    one("tools.grammar-member-missing", "tools", "plugins", {
        content: undefined,
        draw: { grammar: { hints: () => [], create: noop } },
    }),
    one("tools.session-type-not-namespaced", "tools", "plugins", {
        produces: { store: "session", type: "other:ruler" },
    }),
    one("tools.map-product-misplaced", "tools", "any", { mapProduct: () => [] }),
    one("tools.id-has-separator", "tools", "plugins", { id: "fixture:tool" }),

    one("elementMappers.type-missing", "elementMappers", "any", { type: "" }),
    one("elementMappers.map-missing", "elementMappers", "any", { map: undefined }),
    one("elementMappers.selector-invalid", "elementMappers", "any", { selector: {} }),
    one("elementMappers.preview-not-function", "elementMappers", "any", { preview: "thumbnail" }),
    one("elementMappers.text-label-key-missing", "elementMappers", "any", { textSetting: {} }),
    one("elementMappers.text-order-not-number", "elementMappers", "any", {
        textSetting: { labelKey: `${FIXTURE_PLUGIN_ID}:label`, order: "first" },
    }),
    {
        rule: "elementMappers.duplicate",
        kind: "elementMappers",
        owners: "plugins",
        items: [VALID_CONTRIBUTIONS.elementMappers, VALID_CONTRIBUTIONS.elementMappers],
    },

    one("translations.namespace-missing", "translations", "plugins", { namespace: "" }),
    one("translations.resources-missing", "translations", "plugins", { resources: null }),

    one("detailsHeaders.type-missing", "detailsHeaders", "any", { type: "" }),
    one("detailsHeaders.type-icon-not-component", "detailsHeaders", "any", { typeIcon: "svg" }),
    one("detailsHeaders.icon-frame-unknown", "detailsHeaders", "any", { iconFrame: "dashed" }),

    one("filterFields.id-missing", "filterFields", "any", { id: "" }),
    // A host id is never in a plugin's namespace, so the plugin path also says so.
    one("filterFields.id-reserved", "filterFields", "any", { id: "kind" }, [
        "filterFields.not-namespaced",
    ]),
    one("filterFields.label-key-missing", "filterFields", "any", { labelKey: "" }),
    one("filterFields.label-key-unresolved", "filterFields", "any", {
        labelKey: `${FIXTURE_PLUGIN_ID}:nowhere`,
    }),
    one("filterFields.notice-key-unresolved", "filterFields", "any", {
        noticeKey: `${FIXTURE_PLUGIN_ID}:nowhere`,
    }),
    one("filterFields.path-missing", "filterFields", "any", { path: "" }),
    one("filterFields.path-not-filterable", "filterFields", "any", {
        path: "properties.opencop.location",
    }),
    one("filterFields.host-path-needs-claims", "filterFields", "any", {
        path: "properties.opencop.displayName",
    }),
    one("filterFields.order-missing", "filterFields", "any", { order: Number.NaN }),
    one("filterFields.operators-empty", "filterFields", "any", { operators: [] }),
    one("filterFields.operator-unknown", "filterFields", "any", { operators: ["MATCHES"] }),
    one("filterFields.value-editor-not-component", "filterFields", "any", {
        ValueEditor: "Editor",
    }),
    one("filterFields.member-not-function", "filterFields", "any", { claims: "yes" }),
    one("filterFields.not-namespaced", "filterFields", "plugins", { id: "field" }),
    {
        rule: "filterFields.duplicate",
        kind: "filterFields",
        owners: "plugins",
        items: [VALID_CONTRIBUTIONS.filterFields, VALID_CONTRIBUTIONS.filterFields],
    },

    one("detailsPositions.type-missing", "detailsPositions", "any", { type: "" }),
    one("detailsPositions.position-not-function", "detailsPositions", "any", { position: "here" }),

    one("detailsEditForms.type-missing", "detailsEditForms", "any", { type: "" }),
    one("detailsEditForms.id-missing", "detailsEditForms", "any", { id: "" }),
    one("detailsEditForms.order-not-number", "detailsEditForms", "any", { order: "1" }),
    one("detailsEditForms.component-not-component", "detailsEditForms", "any", { Component: null }),

    one("detailsInfoSections.type-missing", "detailsInfoSections", "any", { type: "" }),
    one("detailsInfoSections.id-missing", "detailsInfoSections", "any", { id: "" }),
    one("detailsInfoSections.order-not-number", "detailsInfoSections", "any", { order: "1" }),
    one("detailsInfoSections.component-not-component", "detailsInfoSections", "any", {
        Component: null,
    }),

    one("contextMenuItems.id-missing", "contextMenuItems", "any", { id: "" }),
    one("contextMenuItems.order-not-number", "contextMenuItems", "any", { order: "1" }),
    one("contextMenuItems.target-kind-unknown", "contextMenuItems", "any", {
        target: { kind: "sky" },
    }),
    one("contextMenuItems.target-type-missing", "contextMenuItems", "any", {
        target: { kind: "object" },
    }),
    one("contextMenuItems.target-store-closed", "contextMenuItems", "any", {
        target: { kind: "object", type: FIXTURE_TYPE, store: "watchboxes" },
    }),
    one("contextMenuItems.content-not-component", "contextMenuItems", "any", {
        labelKey: undefined,
        activate: undefined,
        Content: "div",
    }),
    one("contextMenuItems.label-key-missing", "contextMenuItems", "any", { labelKey: "" }),
    one("contextMenuItems.activate-tool-missing", "contextMenuItems", "any", {
        activate: undefined,
    }),
];
