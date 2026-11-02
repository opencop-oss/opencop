// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import Logger from "js-logger";
import { createContainment } from "@opencop/contributions/containment";

/**
 * The host's one containment: the failure state every contribution's guard shares,
 * logged to the `plugins` logger. The mechanism and each contribution type's fallbacks
 * live in `@opencop/contributions/containment`, so a test host contains a plugin exactly
 * as the host does; this file names the host's instance and keeps the call sites' names.
 */
export const hostContainment = createContainment({ log: Logger.get("plugins") });

export {
    describeOrigin,
    nativeOwner,
    pluginOwner,
    type ContributionOrigin,
    type ContributionOwner,
    type OwnerKind,
} from "@opencop/contributions/containment";

export const {
    reportContributionFailure,
    runContribution,
    clearContributionFailure,
    rearmContributions,
} = hostContainment;

/** Test seam — the host's failure state is process-wide. */
export const __resetContributionFailureLog = hostContainment.reset;
