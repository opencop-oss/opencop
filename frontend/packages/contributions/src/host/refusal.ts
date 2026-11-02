// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { errorsOf, type Problem } from "../validation/index.ts";

/**
 * A registry's refusal of one registration, carrying the rules it broke so whoever
 * installed the owner — the native installer, the plugin manager — reports them by
 * rule id rather than by message.
 */
export class ContributionRefusal extends Error {
    readonly problems: readonly Problem[];

    constructor(registry: string, problems: readonly Problem[]) {
        super(`[${registry}]: refused: ${problems.map((problem) => problem.message).join("; ")}.`);
        this.problems = problems;
    }
}

/** Throw a {@link ContributionRefusal} for every error in `problems`; warnings pass. */
export function refuseProblems(registry: string, problems: readonly Problem[]): void {
    const errors = errorsOf(problems);
    if (errors.length > 0) throw new ContributionRefusal(registry, errors);
}
