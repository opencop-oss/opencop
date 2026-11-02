// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The measure subject: an ephemeral ruler (line, polygon or circle) the
 * runtime holds no custody over. Its tool produces into the host's session
 * store, and the scene draws the in-flight draft and the finished measurement
 * (`measure.mapper.ts`), with the handles on top.
 *
 * Distinct from `features/measurements/`, which owns the operator's unit
 * system (metric / imperial) and is read by this subject's formatting.
 */
import type { ContributionContext } from "@opencop/contributions";
import { measureDescriptor } from "./measure.descriptor";

export function installMeasure(ctx: ContributionContext): void {
    ctx.registerTool(measureDescriptor);
}
