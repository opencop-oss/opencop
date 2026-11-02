// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    Box,
    Field as ChakraField,
    Flex,
    Input,
    RadioGroup,
    Stack,
    Text,
    VisuallyHidden,
} from "@chakra-ui/react";
import { OcSelect, Tooltip } from "@opencop/ui";
import { useId } from "react";
import { Controller, useFormContext, useFormState } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { DataSourceAccessHint } from "~/features/data-sources/DataSourceAccessHint";
import {
    ALL_ACCESS_MODES,
    getAccessModePresentation,
} from "~/features/data-sources/data-source-access.policy";
import { useRegisterField } from "~/features/data-sources/use-register-field.hook";
import type { ConnectionSetupFormValues, ConnectionSetupFlow } from "./use-connection-setup.hook";
import { TRANSPORT_LABEL_KEYS } from "~/features/data-sources/data-sources.util";
import { integrationDisplayName } from "~/features/data-sources/integration-text.util";

interface IntegrationStepProps {
    setup: ConnectionSetupFlow;
}

/**
 * Picks the integration, then lists every access mode with the ones it does not support muted and
 * explained: the choice is the integration's, never the transport's. The server name follows once
 * an integration is chosen; it belongs to no integration, so switching keeps it.
 */
export function IntegrationStep({ setup }: IntegrationStepProps) {
    const { t } = useTranslation();
    const accessDescriptionId = useId();
    const { control } = useFormContext<ConnectionSetupFormValues>();
    // Clears the field's error per keystroke; see useRegisterField.
    const register = useRegisterField<ConnectionSetupFormValues>();
    const { errors } = useFormState({ control, name: "name" });
    const accessOptions = ALL_ACCESS_MODES.map((mode) => ({
        ...getAccessModePresentation(mode, t),
        supported: setup.accessModes.includes(mode),
    }));
    const firstSupportedIndex = accessOptions.findIndex((option) => option.supported);
    const integration = setup.integrationName ?? "";
    const protocol = t(TRANSPORT_LABEL_KEYS[setup.protocol]);
    // Shown from the moment an integration is picked, so switching never unmounts and remounts
    // them while the next setup loads.
    const chosen = !!setup.integrationKey && !setup.setupFailed && !setup.noProtocolSetup;
    const unsupportedReason =
        setup.accessModes.length === 1 && setup.accessModes[0] === "R"
            ? t("connectionSetup.accessReadOnly", { integration })
            : setup.accessModes.length === 1 && setup.accessModes[0] === "W"
              ? t("connectionSetup.accessWriteOnly", { integration })
              : t("connectionSetup.accessModeUnsupported", { integration });

    return (
        <Stack p={2} gap={6}>
            <Controller
                control={control}
                name="integrationKey"
                rules={{ required: t("connectionSetup.selectionRequired") }}
                render={({ field, fieldState }) => (
                    <ChakraField.Root invalid={fieldState.invalid} required>
                        <ChakraField.Label>
                            {t("connectionSetup.integration")}
                            <ChakraField.RequiredIndicator />
                        </ChakraField.Label>
                        <OcSelect
                            items={setup.integrations.map((entry) => ({
                                value: `${entry.integrationId}:${entry.schemaVersion}`,
                                label: integrationDisplayName(entry, t),
                            }))}
                            value={field.value}
                            onValueChange={(value) => {
                                setup.chooseIntegration(value);
                                field.onBlur();
                            }}
                            placeholder={t("connectionSetup.chooseIntegration")}
                            emptyMessage={t("connectionSetup.noIntegrations", { protocol })}
                        />
                        <ChakraField.ErrorText>{fieldState.error?.message}</ChakraField.ErrorText>
                    </ChakraField.Root>
                )}
            />

            {chosen && (
                <ChakraField.Root invalid={!!errors.name} required>
                    <ChakraField.Label htmlFor="connection-setup-name">
                        {t("admin.serverName")}
                        <ChakraField.RequiredIndicator />
                    </ChakraField.Label>
                    <Input
                        {...register("name", {
                            // Core owns the length limit and reports it against `name` on submit.
                            validate: (name) =>
                                name.trim() !== "" ||
                                t("connectionSetup.fieldRequired", {
                                    label: t("admin.serverName"),
                                }),
                        })}
                        id="connection-setup-name"
                        placeholder={t("admin.enterServerName")}
                    />
                    <ChakraField.ErrorText>{errors.name?.message}</ChakraField.ErrorText>
                </ChakraField.Root>
            )}

            {chosen && (
                <Controller
                    control={control}
                    name="access"
                    render={({ field, fieldState }) => (
                        <ChakraField.Root required invalid={fieldState.invalid}>
                            <ChakraField.Label>
                                {t("admin.accessMode")}
                                <ChakraField.RequiredIndicator />
                            </ChakraField.Label>
                            <RadioGroup.Root
                                aria-label={t("admin.accessMode")}
                                colorPalette="brand"
                                variant="outline"
                                value={setup.access}
                                onValueChange={(details) => field.onChange(details.value)}
                            >
                                <Stack direction="row" gap={6} wrap="wrap">
                                    {accessOptions.map((option, index) => {
                                        const reasonId = `${accessDescriptionId}-${option.value}-unsupported`;
                                        const item = (
                                            <RadioGroup.Item
                                                key={option.value}
                                                value={option.value}
                                                disabled={!option.supported}
                                                opacity={option.supported ? 1 : 0.5}
                                                cursor={
                                                    option.supported ? "pointer" : "not-allowed"
                                                }
                                            >
                                                <RadioGroup.ItemHiddenInput
                                                    // A disabled radio cannot take focus, so a
                                                    // rejected access lands on a supported one.
                                                    ref={
                                                        index === firstSupportedIndex
                                                            ? field.ref
                                                            : undefined
                                                    }
                                                    aria-describedby={
                                                        option.supported
                                                            ? `${accessDescriptionId}-${option.value}`
                                                            : `${accessDescriptionId}-${option.value} ${reasonId}`
                                                    }
                                                />
                                                <RadioGroup.ItemIndicator />
                                                <Flex align="center" gap={1.5}>
                                                    <RadioGroup.ItemText>
                                                        {option.label}
                                                    </RadioGroup.ItemText>
                                                    <DataSourceAccessHint
                                                        id={`${accessDescriptionId}-${option.value}`}
                                                        message={option.description}
                                                    />
                                                    {!option.supported && (
                                                        <VisuallyHidden id={reasonId}>
                                                            {unsupportedReason}
                                                        </VisuallyHidden>
                                                    )}
                                                </Flex>
                                            </RadioGroup.Item>
                                        );
                                        // Every option sits in the same wrapper so muting one
                                        // never moves the others. A disabled radio leaves the tab
                                        // order, so a muted option's wrapper takes focus instead.
                                        const wrapped = (
                                            <Box
                                                key={option.value}
                                                as="span"
                                                tabIndex={option.supported ? undefined : 0}
                                                display="block"
                                                _focusVisible={{
                                                    outline: "2px solid",
                                                    outlineColor: "brand.focusRing",
                                                    borderRadius: "sm",
                                                }}
                                            >
                                                {item}
                                            </Box>
                                        );
                                        return option.supported ? (
                                            wrapped
                                        ) : (
                                            <Tooltip
                                                key={option.value}
                                                content={unsupportedReason}
                                                uppercase={false}
                                            >
                                                {wrapped}
                                            </Tooltip>
                                        );
                                    })}
                                </Stack>
                            </RadioGroup.Root>
                            <ChakraField.ErrorText>
                                {fieldState.error?.message}
                            </ChakraField.ErrorText>
                        </ChakraField.Root>
                    )}
                />
            )}

            {(setup.catalogFailed || setup.setupFailed) && (
                <Text color="fg.error" fontSize="sm">
                    {t("connectionSetup.setupLoadFailed")}
                </Text>
            )}
            {setup.noProtocolSetup && (
                <Text color="fg.error" fontSize="sm">
                    {t("connectionSetup.noProtocolSetup", { protocol })}
                </Text>
            )}
        </Stack>
    );
}
