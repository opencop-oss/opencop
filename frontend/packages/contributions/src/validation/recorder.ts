// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ToolDescriptor } from "@opencop/tools";
import type { ContributedFilterField } from "@opencop/ui";
import type { PluginContributionContext } from "../contribution-context.ts";
import type { DetailsHeaderContribution } from "../details.ts";
import type { ElementMapper } from "../element-mapper.ts";
import type { TranslationContribution } from "../translation.ts";
import type { Contributions } from "./contributions.ts";

/** A recorder: the context handed to `install`, plus what it recorded. */
export interface ContributionRecorder<Base extends object> {
    /** Passed to the plugin's `install(ctx)`: `base` plus the register methods. */
    readonly ctx: Base & PluginContributionContext;
    /**
     * What `install` registered. Populated as the plugin calls the register
     * methods — read it only *after* `install` returns.
     */
    readonly contributes: Contributions;
}

/**
 * Record a plugin's `install(ctx)` registrations instead of applying them.
 *
 * This is what lets registration be an imperative call while keeping the
 * atomicity a declarative manifest gave us for free: the plugin calls
 * `ctx.registerTool(...)` and friends, the calls land in buffers, and the
 * whole set is validated *before* anything reaches a live registry. A
 * plugin that throws halfway through `install`, or contributes one malformed
 * item, therefore registers nothing at all — there is no partially-installed
 * state to unwind.
 *
 * `base` is the plugin's scoped capabilities (durable storage, the host's injected
 * hooks), passed through so `install` can decide what to contribute based on what it
 * finds. It is generic over that context: the recorder adds the register methods to
 * whatever it is given.
 */
export function createContributionRecorder<Base extends object>(
    base: Base,
): ContributionRecorder<Base> {
    const tools: ToolDescriptor[] = [];
    const elementMappers: ElementMapper[] = [];
    const translations: TranslationContribution[] = [];
    const detailsHeaders: DetailsHeaderContribution[] = [];
    const filterFields: ContributedFilterField[] = [];

    const ctx: Base & PluginContributionContext = {
        ...base,
        registerTool: (tool) => void tools.push(tool),
        // Stored at the base type: the scene pipeline hands a mapper only elements
        // of its own type, so the widening happens here, once.
        registerElementMapper: (mapper) =>
            void elementMappers.push(mapper as unknown as ElementMapper),
        registerTranslations: (bundle) => void translations.push(bundle),
        registerDetailsHeader: (contribution) => void detailsHeaders.push(contribution),
        registerFilterField: (field) => void filterFields.push(field),
    };

    // The arrays are mutated in place by the register methods above, so this
    // object reflects everything `install` contributed once it returns.
    return {
        ctx,
        contributes: {
            tools,
            elementMappers,
            translations,
            detailsHeaders,
            filterFields,
        },
    };
}
