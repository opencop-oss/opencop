// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Button, Field as ChakraField, Grid, HStack, Input, Stack, Text } from "@chakra-ui/react";
import type { FramingMode } from "@opencop/api";
import { OcSelect, type SelectItem } from "@opencop/ui";
import { Controller, useFormContext, useFormState, useWatch } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useRegisterField } from "~/features/data-sources/use-register-field.hook";
import { describeFraming, framingModeLabel } from "./connection-setup.util";
import { CUSTOM_FRAMING, CUSTOM_FRAMING_MODES, modeNeedsDelimiter } from "./tcp-setup.model";
import type { ConnectionSetupFormValues, ConnectionSetupFlow } from "./use-connection-setup.hook";

interface MappingStepProps {
    setup: ConnectionSetupFlow;
}

/** Two fields share a row from `md` up; a field without its partner takes the whole row. */
function pairColumns(paired: boolean) {
    return paired ? { base: "1fr", md: "1fr 1fr" } : "1fr";
}

/**
 * Framing (TCP only), then the mappers the access needs: the ingress mapper when the connection
 * reads and the egress mapper when it writes. Each list holds only what the integration permits,
 * and a single permitted option shows as selected.
 */
export function MappingStep({ setup }: MappingStepProps) {
    const { t } = useTranslation();
    const { control, clearErrors } = useFormContext<ConnectionSetupFormValues>();
    // Clears the field's error per keystroke; see useRegisterField.
    const register = useRegisterField<ConnectionSetupFormValues>();
    const { errors } = useFormState({ control });
    const customMode = useWatch({ control, name: "customMode" });
    const { framings, framing, mappers, mapperId, mapperProfiles, egressIds, egressMapperId } =
        setup;
    const selectionRequired = t("connectionSetup.selectionRequired");
    const mappersLoadFailed = t("connectionSetup.mappersLoadFailed");

    const modeLabel = (mode: FramingMode) => framingModeLabel(mode, t);
    const framingItems: SelectItem[] = framings.map((choice) =>
        choice.kind === "CUSTOM"
            ? { value: CUSTOM_FRAMING, label: t("connectionSetup.customFraming") }
            : {
                  value: choice.value,
                  label:
                      describeFraming(
                          { type: "INTEGRATION", framingId: choice.value },
                          setup.integrationName,
                          [choice.framing],
                          t,
                      ) ?? modeLabel(choice.framing.mode),
              },
    );
    const mapperName = (id: string) => mapperProfiles.find((m) => m.id === id)?.name ?? id;
    const mapperItems: SelectItem[] =
        mappers.kind === "ANY"
            ? mapperProfiles.map((m) => ({ value: m.id, label: m.name }))
            : mappers.mapperIds.map((id) => ({ value: id, label: mapperName(id) }));
    const egressName = (id: string) => setup.egressProfiles.find((m) => m.id === id)?.name ?? id;
    const egressItems: SelectItem[] = egressIds.map((id) => ({ value: id, label: egressName(id) }));
    const modeItems: SelectItem[] = CUSTOM_FRAMING_MODES.map((mode) => ({
        value: mode,
        label: modeLabel(mode),
    }));

    return (
        <Stack p={2} gap={3}>
            {setup.protocol === "TCP" && (
                <>
                    <Controller
                        control={control}
                        name="framing"
                        rules={{ validate: () => (framing ? true : selectionRequired) }}
                        render={({ field, fieldState }) => (
                            <ChakraField.Root invalid={fieldState.invalid} required>
                                <ChakraField.Label>
                                    {t("connectionSetup.framing")}
                                    <ChakraField.RequiredIndicator />
                                </ChakraField.Label>
                                <OcSelect
                                    items={framingItems}
                                    value={framing}
                                    onValueChange={(value) => {
                                        field.onChange(value);
                                        // Validating here would read the previous render's selection;
                                        // a pick clears the error and Next re-validates.
                                        clearErrors("framing");
                                    }}
                                    placeholder={t("connectionSetup.chooseFraming")}
                                />
                                <ChakraField.ErrorText>
                                    {fieldState.error?.message}
                                </ChakraField.ErrorText>
                            </ChakraField.Root>
                        )}
                    />

                    {framing === CUSTOM_FRAMING && (
                        <Grid
                            templateColumns={pairColumns(modeNeedsDelimiter(customMode))}
                            gap={3}
                            alignItems="start"
                        >
                            <Controller
                                control={control}
                                name="customMode"
                                render={({ field }) => (
                                    <ChakraField.Root required>
                                        <ChakraField.Label>
                                            {t("dataSources.mode")}
                                            <ChakraField.RequiredIndicator />
                                        </ChakraField.Label>
                                        <OcSelect
                                            items={modeItems}
                                            value={field.value}
                                            onValueChange={field.onChange}
                                        />
                                    </ChakraField.Root>
                                )}
                            />
                            {modeNeedsDelimiter(customMode) && (
                                <ChakraField.Root invalid={!!errors.customDelimiter} required>
                                    <ChakraField.Label htmlFor="connection-setup-delimiter">
                                        {t("dataSources.delimiter")}
                                        <ChakraField.RequiredIndicator />
                                    </ChakraField.Label>
                                    <Input
                                        {...register("customDelimiter", {
                                            required: t("connectionSetup.fieldRequired", {
                                                label: t("dataSources.delimiter"),
                                            }),
                                        })}
                                        id="connection-setup-delimiter"
                                        placeholder={t("dataSources.delimiterPlaceholder")}
                                    />
                                    <ChakraField.HelperText>
                                        {t("connectionSetup.delimiterEscapesHint")}
                                    </ChakraField.HelperText>
                                    <ChakraField.ErrorText>
                                        {errors.customDelimiter?.message}
                                    </ChakraField.ErrorText>
                                </ChakraField.Root>
                            )}
                        </Grid>
                    )}
                </>
            )}

            <Grid
                templateColumns={pairColumns(setup.reads && setup.writes)}
                gap={3}
                alignItems="start"
            >
                {setup.reads && (
                    <Controller
                        control={control}
                        name="mapperId"
                        rules={{ validate: () => (mapperId ? true : selectionRequired) }}
                        render={({ field, fieldState }) => (
                            <ChakraField.Root invalid={fieldState.invalid} required>
                                <ChakraField.Label>
                                    {t("connectionSetup.mapper")}
                                    <ChakraField.RequiredIndicator />
                                </ChakraField.Label>
                                <OcSelect
                                    items={mapperItems}
                                    value={mapperId}
                                    onValueChange={(value) => {
                                        field.onChange(value);
                                        clearErrors("mapperId");
                                    }}
                                    placeholder={t("connectionSetup.chooseMapper")}
                                    emptyMessage={
                                        setup.mappersFailed
                                            ? mappersLoadFailed
                                            : t("connectionSetup.noMappers")
                                    }
                                />
                                <ChakraField.ErrorText>
                                    {fieldState.error?.message}
                                </ChakraField.ErrorText>
                            </ChakraField.Root>
                        )}
                    />
                )}

                {setup.writes && (
                    <Controller
                        control={control}
                        name="egressMapperId"
                        rules={{ validate: () => (egressMapperId ? true : selectionRequired) }}
                        render={({ field, fieldState }) => (
                            <ChakraField.Root invalid={fieldState.invalid} required>
                                <ChakraField.Label>
                                    {t("connectionSetup.egressMapper")}
                                    <ChakraField.RequiredIndicator />
                                </ChakraField.Label>
                                <OcSelect
                                    items={egressItems}
                                    value={egressMapperId}
                                    onValueChange={(value) => {
                                        field.onChange(value);
                                        clearErrors("egressMapperId");
                                    }}
                                    placeholder={t("connectionSetup.chooseEgressMapper")}
                                    emptyMessage={
                                        setup.egressMappersFailed
                                            ? mappersLoadFailed
                                            : t("connectionSetup.noEgressMappers")
                                    }
                                />
                                <ChakraField.ErrorText>
                                    {fieldState.error?.message}
                                </ChakraField.ErrorText>
                            </ChakraField.Root>
                        )}
                    />
                )}
            </Grid>

            {(setup.mappersFailed || setup.egressMappersFailed) && (
                <HStack gap={2}>
                    <Text color="fg.error" fontSize="sm">
                        {mappersLoadFailed}
                    </Text>
                    <Button type="button" size="xs" variant="ghost" onClick={setup.retryMappers}>
                        {t("common.retry")}
                    </Button>
                </HStack>
            )}
        </Stack>
    );
}
