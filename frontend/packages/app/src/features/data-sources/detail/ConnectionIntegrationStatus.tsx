// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Alert, Box, Flex, Text, VisuallyHidden } from "@chakra-ui/react";
import { Tooltip } from "@opencop/ui";
import { useTranslation } from "react-i18next";
import { LuCircleCheck, LuTriangleAlert } from "react-icons/lu";
import {
    connectionIntegrationLabel,
    type ConnectionIntegration,
} from "./use-connection-integration.hook";

interface ConnectionIntegrationProps {
    integration: ConnectionIntegration;
}

interface IntegrationUnavailableAlertProps {
    integration: ConnectionIntegration | undefined;
    unavailable: boolean;
}

/**
 * The integration a connection is bound to, as a detail-row value: its name, or its stored
 * identity, led by an availability marker once availability is known. The marker's shape differs
 * by status as well as its colour, so the status never rests on colour alone; it is focusable and
 * carries its status as real text, so keyboard and screen-reader users get what the tooltip says.
 */
export function ConnectionIntegrationValue({ integration }: ConnectionIntegrationProps) {
    const { t } = useTranslation();
    const status =
        integration.availability === "unknown"
            ? undefined
            : integration.availability === "available"
              ? {
                    label: t("dataSources.integrationAvailable"),
                    color: "type.success",
                    Icon: LuCircleCheck,
                }
              : {
                    label: t("dataSources.integrationUnavailable"),
                    color: "fg.error",
                    Icon: LuTriangleAlert,
                };
    return (
        <Flex align="center" gap={1.5}>
            {status && (
                <Tooltip content={status.label} uppercase={false}>
                    <Box
                        as="span"
                        tabIndex={0}
                        color={status.color}
                        display="inline-flex"
                        flexShrink={0}
                        _focusVisible={{
                            outline: "2px solid",
                            outlineColor: "brand.focusRing",
                            borderRadius: "sm",
                        }}
                    >
                        <status.Icon aria-hidden focusable={false} />
                        <VisuallyHidden>{status.label}</VisuallyHidden>
                    </Box>
                </Tooltip>
            )}
            <Text fontSize="sm">{connectionIntegrationLabel(integration, t)}</Text>
        </Flex>
    );
}

/**
 * Explains an unavailable integration in operator terms: what it means and that it recovers on its
 * own. Shows no connection details beyond the integration's name. Its `status` live region stays
 * mounted whether or not the alert shows, so the alert is announced when a refetch brings it in.
 */
export function IntegrationUnavailableAlert({
    integration,
    unavailable,
}: IntegrationUnavailableAlertProps) {
    const { t } = useTranslation();
    const shown = integration !== undefined && unavailable;
    return (
        <>
            <VisuallyHidden role="status">
                {shown ? t("dataSources.integrationUnavailableTitle") : ""}
            </VisuallyHidden>
            {shown && (
                <Alert.Root status="warning" size="sm" mb={4}>
                    <Alert.Indicator />
                    <Alert.Content>
                        <Alert.Title>{t("dataSources.integrationUnavailableTitle")}</Alert.Title>
                        <Alert.Description>
                            {t("dataSources.integrationUnavailableDescription", {
                                integration: connectionIntegrationLabel(integration, t),
                            })}
                        </Alert.Description>
                    </Alert.Content>
                </Alert.Root>
            )}
        </>
    );
}
