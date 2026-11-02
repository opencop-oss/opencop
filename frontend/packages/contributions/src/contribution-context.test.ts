// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expectTypeOf, it } from "vitest";
import type { ContributionContext, PluginContributionContext } from "./contribution-context.ts";

/**
 * Compile-time checks, enforced by `npm run typecheck`: the plugin registration
 * set may differ from the native one only in the ways its doc names — no return
 * value, a namespaced filter-field id, and translations.
 */

type Arg<C, K extends keyof C> = C[K] extends (arg: infer A) => unknown ? A : never;

describe("PluginContributionContext", () => {
    it("offers only members the native context has, plus translations", () => {
        expectTypeOf<Exclude<keyof PluginContributionContext, "registerTranslations">>().toExtend<
            keyof ContributionContext
        >();
    });

    it("takes the same contract as each native twin", () => {
        expectTypeOf<Arg<PluginContributionContext, "registerTool">>().toEqualTypeOf<
            Arg<ContributionContext, "registerTool">
        >();
        expectTypeOf<Arg<PluginContributionContext, "registerElementMapper">>().toEqualTypeOf<
            Arg<ContributionContext, "registerElementMapper">
        >();
        expectTypeOf<Arg<PluginContributionContext, "registerDetailsHeader">>().toEqualTypeOf<
            Arg<ContributionContext, "registerDetailsHeader">
        >();
    });

    it("returns nothing; the plugin manager owns removal", () => {
        expectTypeOf<ReturnType<PluginContributionContext["registerTool"]>>().toBeVoid();
        expectTypeOf<ReturnType<ContributionContext["registerTool"]>>().toEqualTypeOf<() => void>();
    });
});
