// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ToolDescriptor } from "@opencop/tools";
import type { ContributedFilterField } from "@opencop/ui";
import type { DetailsHeaderContribution } from "../details.ts";
import type { ElementMapper } from "../element-mapper.ts";
import type { TranslationContribution } from "../translation.ts";

/**
 * Everything one plugin contributed during `install`, as one record — the
 * {@link createContributionRecorder}'s output and the unit the validators check and
 * the host registers.
 *
 * Host bookkeeping, not plugin API: a plugin contributes imperatively
 * (`ctx.registerTool(...)` and friends) and never constructs or reads this record.
 * It exists so the buffered registrations can be validated as a set before any of
 * them touch a live registry.
 *
 * A CLOSED, growing set of kinds. Each key here must have a validator in
 * `contributionValidators` and a registrar in the host — both enforced by a
 * `satisfies Record<keyof Contributions, …>` check, so adding a kind without wiring
 * it is a compile error, not a contribution that silently vanishes.
 */
export interface Contributions {
    readonly tools?: readonly ToolDescriptor[];
    /**
     * The same {@link ElementMapper} a native subject registers. Authoring guidance
     * lives on `PluginInstallContext.registerElementMapper`.
     */
    readonly elementMappers?: readonly ElementMapper[];
    readonly translations?: readonly TranslationContribution[];
    /** The same `DetailsHeaderContribution` a native subject registers. */
    readonly detailsHeaders?: readonly DetailsHeaderContribution[];
    /** The same `ContributedFilterField` a native subject registers. */
    readonly filterFields?: readonly ContributedFilterField[];
}

/** One contribution kind: a key of {@link Contributions}. */
export type ContributionKind = keyof Contributions;
