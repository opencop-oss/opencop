// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

export type { Contributions, ContributionKind } from "./contributions.ts";
export { createContributionRecorder } from "./recorder.ts";
export type { ContributionRecorder } from "./recorder.ts";
export { CONTRIBUTION_RULES, errorsOf, formatProblem } from "./problem.ts";
export type { ContributionRule, Problem, ProblemKind } from "./problem.ts";
export { toI18nResources, withContributedTranslations } from "./environment.ts";
export type { ValidationEnvironment } from "./environment.ts";
export { NATIVE_OWNER_IDS } from "./native-owner-ids.ts";
export { hostedPluginTool, PLUGIN_TOOL_ID_SEPARATOR, pluginToolId } from "./plugin-tools.ts";
export {
    attributeFieldId,
    attributeNameOf,
    attributeNameOfId,
    attributePath,
    HOST_FILTER_FIELDS,
    isAttributeName,
} from "./filter-paths.ts";
export {
    validateContextMenuItem,
    validateDetailsEditForm,
    validateDetailsHeader,
    validateDetailsInfoSection,
    validateDetailsPosition,
    validateElementMapper,
    validateFilterField,
    validateTool,
} from "./rules.ts";
export { contributionValidators, validatePluginContributions } from "./validators.ts";
export type { ContributionValidator } from "./validators.ts";
export { MANIFEST_RULES, STOPPING_MANIFEST_RULES, validateManifest } from "./manifest.ts";
export type { ManifestProblem, ManifestRule } from "./manifest.ts";
export {
    CONTRIBUTION_FIXTURES,
    FIXTURE_PLUGIN_ID,
    FIXTURE_TRANSLATIONS,
    VALID_CONTRIBUTIONS,
} from "./contribution-fixtures.ts";
export type { ContributionFixture } from "./contribution-fixtures.ts";
export { MANIFEST_FIXTURES, VALID_MANIFEST } from "./manifest-fixtures.ts";
export type { ManifestFixture } from "./manifest-fixtures.ts";
