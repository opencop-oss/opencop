// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ErrorInfo } from "react";
import Logger from "js-logger";

const logger = Logger.get("error-boundary");

export const createErrorHandler = (componentName: string) => {
    return (error: unknown, info: ErrorInfo) => {
        logger.error(`[${componentName}] Error caught:`, error, info);

        // TODO: Send to error tracking service (OTel, Sentry, etc.)
    };
};
