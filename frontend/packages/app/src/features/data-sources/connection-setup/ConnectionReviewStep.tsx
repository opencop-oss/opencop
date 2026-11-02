// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Alert, Box, GridItem, Stack, Text } from "@chakra-ui/react";
import { useWatch } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { getAccessModePresentation } from "~/features/data-sources/data-source-access.policy";
import { MaskedValue, ReviewField, ReviewRow, ReviewSection } from "./ReviewParts";
import { describeFraming, descriptorFieldLabel } from "./connection-setup.util";
import { tcpFramingSelection } from "./tcp-setup.model";
import type { ConnectionSetupFormValues, ConnectionSetupFlow } from "./use-connection-setup.hook";
import { TRANSPORT_LABEL_KEYS } from "~/features/data-sources/data-sources.util";
import { extractorDisplayLabel } from "~/features/data-sources/integration-text.util";

interface ConnectionReviewStepProps {
    setup: ConnectionSetupFlow;
}

/** Review for the integration-driven flow, boxed by step. */
export function ConnectionReviewStep({ setup }: ConnectionReviewStepProps) {
    const { t } = useTranslation();
    const [name, values, files, customMode, customDelimiter] = useWatch<
        ConnectionSetupFormValues,
        ["name", "values", "files", "customMode", "customDelimiter"]
    >({ name: ["name", "values", "files", "customMode", "customDelimiter"] });

    // Described from what Confirm will send, so it reads as the drawer will once it is created.
    const framingValue = setup.framing
        ? describeFraming(
              tcpFramingSelection({ framing: setup.framing, customMode, customDelimiter }),
              setup.integrationName,
              setup.framings.flatMap((choice) =>
                  choice.kind === "INTEGRATION" ? [choice.framing] : [],
              ),
              t,
          )
        : undefined;
    const mapperName =
        setup.mapperProfiles.find((m) => m.id === setup.mapperId)?.name ?? (setup.mapperId || "-");
    const egressMapperName =
        setup.egressProfiles.find((m) => m.id === setup.egressMapperId)?.name ??
        (setup.egressMapperId || "-");

    return (
        <Stack gap={3} w="full">
            {/* Mounted before it has a message: screen readers often miss a live region that
                arrives already filled. */}
            <Box role="alert">
                {setup.errorMessage && (
                    <Alert.Root status="error" size="sm">
                        <Alert.Indicator />
                        <Alert.Content>
                            <Alert.Description>{setup.errorMessage}</Alert.Description>
                        </Alert.Content>
                    </Alert.Root>
                )}
            </Box>

            <ReviewRow>
                <ReviewSection title={t("admin.dataFlow")}>
                    <ReviewField label={t("admin.reviewTransportType")}>
                        <Text fontSize="sm">{t(TRANSPORT_LABEL_KEYS[setup.protocol])}</Text>
                    </ReviewField>
                    <ReviewField label={t("connectionSetup.reviewIntegration")}>
                        <Text fontSize="sm">{setup.integrationName ?? "-"}</Text>
                    </ReviewField>
                    <ReviewField label={t("admin.reviewAccessMode")}>
                        <Text fontSize="sm">
                            {getAccessModePresentation(setup.access, t).label}
                        </Text>
                    </ReviewField>
                </ReviewSection>

                <ReviewSection title={t("admin.connection")}>
                    <ReviewField label={t("admin.reviewServerName")}>
                        <Text fontSize="sm">{name || "-"}</Text>
                    </ReviewField>
                    <ReviewField label={t("connectionSetup.reviewConnectionMethod")}>
                        <Text fontSize="sm">
                            {setup.extractor ? extractorDisplayLabel(setup.extractor, t) : "-"}
                        </Text>
                    </ReviewField>
                    {setup.extractor?.fields.map((field) => {
                        const label = `${descriptorFieldLabel(field, t)}:`;
                        if (field.kind === "FILE" || field.kind === "FILE_LIST") {
                            const chosen = files?.[field.name] ?? [];
                            return (
                                <ReviewField key={field.name} label={label}>
                                    <Text fontSize="sm">
                                        {chosen.map((file) => file.name).join(", ") || "-"}
                                    </Text>
                                </ReviewField>
                            );
                        }
                        const value = values?.[field.name] ?? "";
                        return (
                            <ReviewField key={field.name} label={label}>
                                {field.kind === "SECRET" ? (
                                    <MaskedValue value={value} />
                                ) : (
                                    <Text fontSize="sm">{value || "-"}</Text>
                                )}
                            </ReviewField>
                        );
                    })}
                </ReviewSection>
            </ReviewRow>

            <ReviewSection title={t("admin.mapping")}>
                {setup.protocol === "TCP" && (
                    <ReviewField label={t("connectionSetup.reviewFraming")}>
                        <Text fontSize="sm">{framingValue ?? "-"}</Text>
                    </ReviewField>
                )}
                {setup.reads && (
                    <ReviewField label={t("admin.reviewMapper")}>
                        <Text fontSize="sm">{mapperName}</Text>
                    </ReviewField>
                )}
                {setup.writes && (
                    <ReviewField label={t("connectionSetup.reviewEgressMapper")}>
                        <Text fontSize="sm">{egressMapperName}</Text>
                    </ReviewField>
                )}
                {setup.protocol === "KAFKA" && (
                    <GridItem colSpan={2}>
                        <Text fontSize="sm" color="fg.muted">
                            {t("admin.kafkaCredentialsNotice")}
                        </Text>
                    </GridItem>
                )}
            </ReviewSection>
        </Stack>
    );
}
