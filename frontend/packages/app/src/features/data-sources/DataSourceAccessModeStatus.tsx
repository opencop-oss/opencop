// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Button, HStack, Text } from "@chakra-ui/react";
import type { AccessMode, TransportType } from "@opencop/api";
import type { ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { DataSourceAccessWarning } from "./DataSourceAccessWarning";
import {
    getAccessIncompatibilityNotice,
    getAccessModePresentation,
    getCapabilityCheckPresentation,
    type CapabilityCheck,
} from "./data-source-access.policy";

interface DataSourceAccessModeStatusProps {
    access: AccessMode;
    transportType: TransportType;
    capabilityCheck?: CapabilityCheck;
    onRetryCapability?: () => void;
}

export function DataSourceAccessModeStatus({
    access,
    transportType,
    capabilityCheck,
    onRetryCapability,
}: DataSourceAccessModeStatusProps): ReactElement {
    const { t } = useTranslation();
    const presentation = getAccessModePresentation(access, t);
    const capability = capabilityCheck && getCapabilityCheckPresentation(capabilityCheck, t);
    const notice = capability
        ? undefined
        : getAccessIncompatibilityNotice({ access, transportType }, t);

    return (
        <HStack gap={1.5}>
            <Text as="span">{presentation.label}</Text>
            {notice && <DataSourceAccessWarning message={notice} />}
            {capability?.tone === "warning" && (
                <DataSourceAccessWarning message={capability.message} />
            )}
            {capability?.tone === "muted" && (
                <Text as="span" fontSize="xs" color="fg.muted">
                    {capability.message}
                </Text>
            )}
            {capability?.retryable && onRetryCapability && (
                <Button type="button" size="xs" variant="ghost" onClick={onRetryCapability}>
                    {t("common.retry")}
                </Button>
            )}
        </HStack>
    );
}
