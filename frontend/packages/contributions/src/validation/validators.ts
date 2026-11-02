// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { SESSION_STORE_ID, type ToolLocation } from "@opencop/tools";
import type { ContributionKind, Contributions } from "./contributions.ts";
import { withContributedTranslations, type ValidationEnvironment } from "./environment.ts";
import { PLUGIN_TOOL_ID_SEPARATOR } from "./plugin-tools.ts";
import { problemCollector, type Problem } from "./problem.ts";
import {
    validateDetailsHeader,
    validateElementMapper,
    validateFilterField,
    validateTool,
} from "./rules.ts";

/**
 * Checks one kind's slice of a plugin's {@link Contributions} and returns every
 * problem: the rules any owner's contribution must meet (`rules.ts`) and the ones that
 * apply to a plugin only — its namespace, an id it uses twice, and the shape a plugin
 * may use today. `environment` already counts the plugin's own translations.
 */
export type ContributionValidator = (
    contributes: Contributions,
    pluginId: string,
    environment: ValidationEnvironment,
) => Problem[];

/**
 * Whether `name` is `"<pluginId>:<name>"` — an identity inside the plugin's own
 * namespace, so two plugins can never claim one, and a plugin can never take a
 * name a native subject or another plugin may need.
 */
function inPluginNamespace(pluginId: string, name: string): boolean {
    const prefix = `${pluginId}:`;
    return (
        name.startsWith(prefix) &&
        name.length > prefix.length &&
        !name.slice(prefix.length).includes(":")
    );
}

/** Whether an item's own rules refused it for having no identity, so nothing more can name it. */
const lacksIdentity = (problems: readonly Problem[]) =>
    problems.some(
        (problem) => problem.rule.endsWith(".id-missing") || problem.rule.endsWith(".type-missing"),
    );

// Fail closed, not just on "external": a location added later stays
// native-only until someone deliberately adds it here, the same "native
// first, graduate once trusted" path every other capability in
// plugin-capabilities.md takes. A denylist on "external" alone would let a
// typo or a future location pass validation and then match no toolbar
// query — the tool registers but renders no button anywhere, with no error.
const PLUGIN_TOOL_LOCATIONS: readonly ToolLocation[] = ["topBarStart", "topBarCenter", "topBarEnd"];

const validateTools: ContributionValidator = (contributes, pluginId) => {
    const { problems, report, warn } = problemCollector("tools");
    for (const tool of contributes.tools ?? []) {
        const own = validateTool(tool);
        if (lacksIdentity(own)) {
            problems.push(...own);
            continue;
        }
        const { id } = tool;
        // The host joins a plugin's id and a tool's with the separator, and splits on it
        // to attribute a failure; a separator in the tool's own id would split wrongly.
        if (id.includes(PLUGIN_TOOL_ID_SEPARATOR))
            warn(
                "tools.id-has-separator",
                `tool "${id}" contains "${PLUGIN_TOOL_ID_SEPARATOR}"; the id the host gives it will not parse back`,
                id,
            );
        if (typeof tool.label !== "string")
            report("tools.label-missing", `tool "${id}" label missing`, id);
        if (typeof tool.icon !== "function")
            report("tools.icon-missing", `tool "${id}" icon missing`, id);
        if (tool.location !== undefined && !PLUGIN_TOOL_LOCATIONS.includes(tool.location))
            report(
                "tools.location-unavailable",
                `tool "${id}" location "${tool.location}" is not available to plugins yet — only "topBarStart", "topBarCenter" and "topBarEnd" are`,
                id,
            );
        // A tool must either open a popover (`content`) or draw (`draw`).
        if (typeof tool.content !== "function" && tool.draw == null)
            report("tools.content-or-draw-missing", `tool "${id}" needs content or draw`, id);
        // A static grammar is checked now; a params-derived one is contained
        // when it resolves at activation.
        const grammar = tool.draw?.grammar;
        if (tool.draw && grammar == null)
            report("tools.grammar-missing", `tool "${id}" draw.grammar missing`, id);
        else if (grammar && typeof grammar !== "function") {
            for (const member of ["bind", "hints", "create"] as const) {
                if (typeof grammar[member] !== "function")
                    report(
                        "tools.grammar-member-missing",
                        `tool "${id}" draw.grammar.${member} missing`,
                        id,
                    );
            }
        }
        problems.push(...own);
        // A session product has no store to own its type, so the tool that
        // produces it does: the type lives in the plugin's namespace.
        if (
            own.length === 0 &&
            tool.produces?.store === SESSION_STORE_ID &&
            !inPluginNamespace(pluginId, tool.produces.type)
        )
            report(
                "tools.session-type-not-namespaced",
                `tool "${id}" session type "${tool.produces.type}" must be namespaced as "${pluginId}:<name>"`,
                id,
            );
    }
    return problems;
};

const validateElementMappers: ContributionValidator = (contributes) => {
    const { problems, report } = problemCollector("elementMappers");
    const types = new Set<string>();
    for (const mapper of contributes.elementMappers ?? []) {
        const own = validateElementMapper(mapper);
        if (lacksIdentity(own)) {
            problems.push(...own);
            continue;
        }
        // The first registration of this type has already been checked.
        if (types.has(mapper.type)) {
            report(
                "elementMappers.duplicate",
                `elementMapper "${mapper.type}" registered twice`,
                mapper.type,
            );
            continue;
        }
        types.add(mapper.type);
        problems.push(...own);
    }
    return problems;
};

const validateTranslations: ContributionValidator = (contributes) => {
    const { problems, report } = problemCollector("translations");
    for (const bundle of contributes.translations ?? []) {
        if (typeof bundle.namespace !== "string" || bundle.namespace.length === 0) {
            report("translations.namespace-missing", "translations.namespace missing");
            continue;
        }
        if (!bundle.resources || typeof bundle.resources !== "object")
            report(
                "translations.resources-missing",
                `translations "${bundle.namespace}" resources missing`,
                bundle.namespace,
            );
    }
    return problems;
};

const validateDetailsHeaders: ContributionValidator = (contributes) =>
    (contributes.detailsHeaders ?? []).flatMap(validateDetailsHeader);

const validateFilterFields: ContributionValidator = (contributes, pluginId, environment) => {
    const { problems, report } = problemCollector("filterFields");
    const ids = new Set<string>();
    for (const field of contributes.filterFields ?? []) {
        const own = validateFilterField(field, environment);
        if (lacksIdentity(own)) {
            problems.push(...own);
            continue;
        }
        const { id } = field;
        if (!inPluginNamespace(pluginId, id))
            report(
                "filterFields.not-namespaced",
                `filterField "${id}" must be namespaced as "${pluginId}:<name>"`,
                id,
            );
        if (ids.has(id)) {
            report("filterFields.duplicate", `filterField "${id}" registered twice`, id);
            continue;
        }
        ids.add(id);
        problems.push(...own);
    }
    return problems;
};

/**
 * One validator per contribution kind. Keyed by kind and `satisfies`-checked against
 * {@link Contributions}, so a kind added there without a validator here is a compile
 * error rather than a kind that is registered unchecked.
 */
export const contributionValidators = {
    tools: validateTools,
    elementMappers: validateElementMappers,
    translations: validateTranslations,
    detailsHeaders: validateDetailsHeaders,
    filterFields: validateFilterFields,
} satisfies Record<ContributionKind, ContributionValidator>;

/**
 * Every problem in a plugin's contributions, across every kind. Its own translations
 * count as registered, since they register alongside the rest.
 */
export function validatePluginContributions(
    contributes: Contributions,
    pluginId: string,
    environment: ValidationEnvironment,
): Problem[] {
    const withOwn = withContributedTranslations(environment, contributes.translations);
    return Object.values(contributionValidators).flatMap((validate) =>
        validate(contributes, pluginId, withOwn),
    );
}
