// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, Spinner, Text } from "@chakra-ui/react";
import { type ReactNode } from "react";
import { useSpinDelay } from "spin-delay";

const DEFAULT_MIN_TIME_MS = 1000;

interface AppLoadingScreenProps {
    message?: string;
    loading?: boolean;
    minTime?: number;
    /** Skip loading screen entirely and render children immediately */
    skip?: boolean;
    children?: ReactNode;
}

/**
 * Full-screen loading indicator with minimum display time to prevent flicker.
 * Renders children when loading completes and minimum time has elapsed.
 * Use `skip` to bypass loading screen entirely for certain routes.
 */
export function AppLoadingScreen({
    message = "Loading...",
    loading = true,
    minTime = DEFAULT_MIN_TIME_MS,
    skip = false,
    children,
}: AppLoadingScreenProps) {
    const showLoading = useSpinDelay(loading, { delay: 0, minDuration: minTime });

    if (skip) {
        return <>{children}</>;
    }

    return showLoading ? (
        <Flex height="100vh" width="100vw" align="center" justify="center" bg="bg">
            <Flex direction="column" align="center" gap={4}>
                <Spinner size="xl" borderWidth="4px" color="fg.muted" />
                <Text fontSize="lg" fontWeight="500" color="fg.muted">
                    {message}
                </Text>
            </Flex>
        </Flex>
    ) : (
        <>{children}</>
    );
}
