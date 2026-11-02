// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import React from "react";
import type { ErrorInfo } from "react";
import { ErrorBoundary, type FallbackProps } from "react-error-boundary";
import { CompactErrorFallback } from "./ErrorFallbacks.tsx";
import { createErrorHandler } from "./error-boundary.util";

interface ErrorBoundaryWrapperProps {
    componentName: string;
    children: React.ReactNode;
    fallback?: React.ComponentType<FallbackProps>;
    onError?: (error: unknown, info: ErrorInfo) => void;
    onReset?: () => void;
}

/**
 *
 * <ErrorBoundaryWrapper componentName="MyComponent">
 *   <MyComponent />
 * </ErrorBoundaryWrapper>
 * ```
 */
export const ErrorBoundaryWrapper: React.FC<ErrorBoundaryWrapperProps> = ({
    componentName,
    children,
    fallback: CustomFallback,
    onError,
    onReset,
}) => {
    const FallbackComponent =
        CustomFallback ||
        ((props) => <CompactErrorFallback {...props} componentName={componentName} />);

    return (
        <ErrorBoundary
            FallbackComponent={FallbackComponent}
            onError={onError || createErrorHandler(componentName)}
            onReset={onReset}
        >
            {children}
        </ErrorBoundary>
    );
};
