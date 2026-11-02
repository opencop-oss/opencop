// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The lasso-selection subject: a freehand trace whose completion becomes the
 * operator's current selection.
 *
 * The live trace is the tool's product in the host's session store, drawn by the
 * scene (`lasso.mapper.ts`); the completed ring is drawn by
 * `SelectionBoundaryLayer`, which the tool does not know about.
 */
import type { ContributionContext } from "@opencop/contributions";
import { lassoDescriptor } from "./lasso.descriptor";

export function installLassoSelection(ctx: ContributionContext): void {
    ctx.registerTool(lassoDescriptor);
}
