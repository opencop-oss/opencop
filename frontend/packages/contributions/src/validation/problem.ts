// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ContributionKind } from "./contributions.ts";

/** The details-panel and context-menu surfaces only native plugins contribute to so far. */
type NativeOnlyKind =
    | "detailsPositions"
    | "detailsEditForms"
    | "detailsInfoSections"
    | "contextMenuItems";

/** Every surface a problem can be about: the kinds a plugin contributes, and the native-only ones. */
export type ProblemKind = ContributionKind | NativeOnlyKind;

// Keyed, so a kind added to either set without one here does not compile.
const KINDS = {
    tools: true,
    elementMappers: true,
    translations: true,
    detailsHeaders: true,
    filterFields: true,
    detailsPositions: true,
    detailsEditForms: true,
    detailsInfoSections: true,
    contextMenuItems: true,
} satisfies Record<ProblemKind, true>;

export const PROBLEM_KINDS = Object.keys(KINDS) as readonly ProblemKind[];

/**
 * Every rule a contribution validator applies, by stable id. A {@link Problem} can only
 * name one of these, so the list is complete by construction: a check added without
 * its id here does not compile.
 */
export const CONTRIBUTION_RULES = [
    "tools.id-missing",
    "tools.label-missing",
    "tools.icon-missing",
    "tools.location-unavailable",
    "tools.content-or-draw-missing",
    "tools.grammar-missing",
    "tools.grammar-member-missing",
    "tools.production-invalid",
    "tools.map-product-misplaced",
    "tools.session-type-not-namespaced",
    "tools.id-has-separator",
    "elementMappers.type-missing",
    "elementMappers.duplicate",
    "elementMappers.map-missing",
    "elementMappers.selector-invalid",
    "elementMappers.preview-not-function",
    "elementMappers.text-label-key-missing",
    "elementMappers.text-order-not-number",
    "translations.namespace-missing",
    "translations.resources-missing",
    "detailsHeaders.type-missing",
    "detailsHeaders.type-icon-not-component",
    "detailsHeaders.icon-frame-unknown",
    "filterFields.id-missing",
    "filterFields.id-reserved",
    "filterFields.not-namespaced",
    "filterFields.duplicate",
    "filterFields.label-key-missing",
    "filterFields.label-key-unresolved",
    "filterFields.notice-key-unresolved",
    "filterFields.path-missing",
    "filterFields.path-not-filterable",
    "filterFields.host-path-needs-claims",
    "filterFields.order-missing",
    "filterFields.operators-empty",
    "filterFields.operator-unknown",
    "filterFields.value-editor-not-component",
    "filterFields.member-not-function",
    "detailsPositions.type-missing",
    "detailsPositions.position-not-function",
    "detailsEditForms.type-missing",
    "detailsEditForms.id-missing",
    "detailsEditForms.order-not-number",
    "detailsEditForms.component-not-component",
    "detailsInfoSections.type-missing",
    "detailsInfoSections.id-missing",
    "detailsInfoSections.order-not-number",
    "detailsInfoSections.component-not-component",
    "contextMenuItems.id-missing",
    "contextMenuItems.order-not-number",
    "contextMenuItems.target-kind-unknown",
    "contextMenuItems.target-type-missing",
    "contextMenuItems.target-store-closed",
    "contextMenuItems.content-not-component",
    "contextMenuItems.label-key-missing",
    "contextMenuItems.activate-tool-missing",
] as const satisfies readonly `${ProblemKind}.${string}`[];

export type ContributionRule = (typeof CONTRIBUTION_RULES)[number];

/**
 * One thing wrong with a contribution. `error`: the host refuses it. `warning`: the
 * host accepts it, degraded.
 */
export interface Problem {
    readonly rule: ContributionRule;
    readonly kind: ProblemKind;
    /** The contribution's identity, when it has one to name. */
    readonly id?: string;
    readonly severity: "error" | "warning";
    readonly message: string;
}

/** `kind: message`, the form the plugin host reports a refusal in. */
export function formatProblem(problem: Problem): string {
    return `${problem.kind}: ${problem.message}`;
}

/** The problems that make the host refuse: everything but warnings. */
export function errorsOf(problems: readonly Problem[]): Problem[] {
    return problems.filter((problem) => problem.severity === "error");
}

/** Collects one kind's problems: `report` one the host refuses, `warn` one it only reports. */
export function problemCollector(kind: ProblemKind) {
    const problems: Problem[] = [];
    const add =
        (severity: Problem["severity"]) => (rule: ContributionRule, message: string, id?: string) =>
            void problems.push({ rule, kind, severity, message, ...(id ? { id } : null) });
    return { problems, report: add("error"), warn: add("warning") };
}
