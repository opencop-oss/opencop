// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { hostContainment } from "./contribution-guard";

/** The error boundary for a rendered contribution, reporting into the host's containment. */
export const { ContributionBoundary } = hostContainment;
