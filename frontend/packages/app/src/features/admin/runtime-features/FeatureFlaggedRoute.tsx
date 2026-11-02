// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Center, Spinner } from "@chakra-ui/react";
import type { PropsWithChildren } from "react";
import { useTranslation } from "react-i18next";
import { Navigate } from "react-router";
import { useSpinDelay } from "spin-delay";
import type { FeatureFlagId } from "./runtime-features.manifest";
import { useFeatureFlagStatus } from "./use-feature-flag.hook";

interface FeatureFlaggedRouteProps {
    flag: FeatureFlagId;
}

/**
 * Route guard for a declared frontend flag. Compose with `ProtectedRoute` for routes that need
 * both role and flag gating.
 *
 * Renders a spinner while the gate is pending rather than redirecting. Flag state comes from app
 * metadata, which is not resolved on a cold load, so redirecting on anything short of a settled
 * `disabled` would bounce a legitimately enabled deep link to `/map` before the answer arrived —
 * and the user would never see why.
 */
export function FeatureFlaggedRoute({
    flag,
    children,
}: PropsWithChildren<FeatureFlaggedRouteProps>) {
    const { t } = useTranslation();
    const status = useFeatureFlagStatus(flag);
    // App metadata is cached for 60s, so a warm navigation resolves this immediately. Delaying the
    // spinner keeps that common case from flashing one.
    const showSpinner = useSpinDelay(status === "pending", {
        delay: 500,
        minDuration: 200,
        ssr: false,
    });

    if (status === "pending") {
        return showSpinner ? (
            <Center h="100%" w="100%">
                <Spinner size="lg" aria-label={t("common.loading")} />
            </Center>
        ) : null;
    }

    if (status === "disabled") {
        return <Navigate to="/map" replace />;
    }

    return children;
}
