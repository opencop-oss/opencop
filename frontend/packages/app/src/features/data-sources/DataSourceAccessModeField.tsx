// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Field, HStack, RadioGroup, Stack, Text } from "@chakra-ui/react";
import type { AccessMode, TransportType } from "@opencop/api";
import { useId, type ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { DataSourceAccessHint } from "./DataSourceAccessHint";
import { DataSourceAccessWarning } from "./DataSourceAccessWarning";
import {
    getAccessIncompatibilityNotice,
    getAccessModeOptions,
    isAccessModeIncompatibleByTransport,
} from "./data-source-access.policy";
import { TRANSPORT_LABEL_KEYS } from "./data-sources.util";

interface DataSourceAccessModeFieldProps {
    value: AccessMode;
    persistedValue?: AccessMode;
    transportType: TransportType;
    onChange: (value: AccessMode) => void;
}

/**
 * Access mode in an edit form. Only the stored mode is selectable: Core refuses an edit that makes a
 * source writable or changes a writable one's access, and Read to Read is the only change left.
 */
export function DataSourceAccessModeField({
    value,
    persistedValue = value,
    transportType,
    onChange,
}: DataSourceAccessModeFieldProps): ReactElement {
    const { t } = useTranslation();
    const descriptionId = useId();
    const options = getAccessModeOptions(t).map((option) => {
        const disabled = option.value !== persistedValue;
        const transportUnsupported = isAccessModeIncompatibleByTransport(
            transportType,
            option.value,
        );
        const disabledReason = !disabled
            ? undefined
            : transportUnsupported
              ? t("dataSources.accessTransportUnsupportedChoice", {
                    access: option.label,
                    transport: t(TRANSPORT_LABEL_KEYS[transportType]),
                })
              : t("dataSources.accessWriteFixed");
        const disabledTone = transportUnsupported ? "warning" : "muted";
        return { ...option, disabled, disabledReason, disabledTone };
    });
    const notice = getAccessIncompatibilityNotice({ access: persistedValue, transportType }, t);

    return (
        <Field.Root required>
            <HStack gap={1.5}>
                <Field.Label mb={0}>
                    {t("admin.accessMode")}
                    <Field.RequiredIndicator />
                </Field.Label>
                {notice && <DataSourceAccessWarning message={notice} />}
            </HStack>
            <RadioGroup.Root
                aria-label={t("admin.accessMode")}
                colorPalette="brand"
                variant="outline"
                value={value}
                onValueChange={(details) => {
                    const nextValue = typeof details === "string" ? details : details.value;
                    const option = options.find((candidate) => candidate.value === nextValue);
                    if (option && !option.disabled) onChange(option.value);
                }}
            >
                <Stack align="stretch" gap={3}>
                    {options.map((option) => (
                        <RadioGroup.Item
                            key={option.value}
                            value={option.value}
                            alignItems="start"
                            disabled={option.disabled}
                        >
                            <RadioGroup.ItemHiddenInput
                                aria-describedby={
                                    option.disabledReason
                                        ? `${descriptionId}-${option.value} ${descriptionId}-${option.value}-disabled`
                                        : `${descriptionId}-${option.value}`
                                }
                            />
                            <RadioGroup.ItemIndicator mt={1} />
                            <Stack gap={0}>
                                <HStack gap={1.5}>
                                    <RadioGroup.ItemText>{option.label}</RadioGroup.ItemText>
                                    <DataSourceAccessHint
                                        id={`${descriptionId}-${option.value}`}
                                        message={option.description}
                                    />
                                </HStack>
                                {option.disabledReason && (
                                    <Text
                                        id={`${descriptionId}-${option.value}-disabled`}
                                        color={
                                            option.disabledTone === "muted"
                                                ? "fg.muted"
                                                : "fg.warning"
                                        }
                                        fontSize="xs"
                                    >
                                        {option.disabledReason}
                                    </Text>
                                )}
                            </Stack>
                        </RadioGroup.Item>
                    ))}
                </Stack>
            </RadioGroup.Root>
        </Field.Root>
    );
}
