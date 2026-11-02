// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Field as ChakraField, Grid, RadioGroup, Stack, Text } from "@chakra-ui/react";
import type { ConnectionExtractor, TransportType } from "@opencop/api";
import { Controller, useFormContext } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { DescriptorField } from "./DescriptorField";
import type { ConnectionSetupFormValues } from "./use-connection-setup.hook";
import { groupFieldRows } from "./connection-setup.util";
import { extractorDisplayLabel } from "~/features/data-sources/integration-text.util";

interface ConnectionStepProps {
    protocol: TransportType;
    extractors: readonly ConnectionExtractor[];
    /** The extractor whose fields render: the chosen one, or the only one offered. */
    extractor: ConnectionExtractor | undefined;
}

export function ConnectionStep({ protocol, extractors, extractor }: ConnectionStepProps) {
    const { t } = useTranslation();
    const { control } = useFormContext<ConnectionSetupFormValues>();

    return (
        <Stack p={2} gap={3}>
            {protocol === "KAFKA" && (
                <Text color="fg.muted" fontSize="sm">
                    {t("admin.kafkaConnectionHint")}
                </Text>
            )}
            {extractors.length > 1 && (
                <Controller
                    control={control}
                    name="extractorId"
                    rules={{ required: t("connectionSetup.selectionRequired") }}
                    render={({ field, fieldState }) => (
                        <ChakraField.Root invalid={fieldState.invalid} required>
                            <ChakraField.Label>
                                {t("connectionSetup.extractor")}
                                <ChakraField.RequiredIndicator />
                            </ChakraField.Label>
                            <RadioGroup.Root
                                aria-label={t("connectionSetup.extractor")}
                                colorPalette="brand"
                                variant="outline"
                                value={field.value}
                                onValueChange={(details) => {
                                    field.onChange(details.value ?? "");
                                    field.onBlur();
                                }}
                            >
                                <Stack direction="row" gap={6} wrap="wrap">
                                    {extractors.map((option, index) => (
                                        <RadioGroup.Item key={option.id} value={option.id}>
                                            <RadioGroup.ItemHiddenInput
                                                // A rejection focuses the chosen radio, else the first.
                                                ref={
                                                    (
                                                        field.value
                                                            ? field.value === option.id
                                                            : index === 0
                                                    )
                                                        ? field.ref
                                                        : undefined
                                                }
                                            />
                                            <RadioGroup.ItemIndicator />
                                            <RadioGroup.ItemText>
                                                {extractorDisplayLabel(option, t)}
                                            </RadioGroup.ItemText>
                                        </RadioGroup.Item>
                                    ))}
                                </Stack>
                            </RadioGroup.Root>
                            <ChakraField.ErrorText>
                                {fieldState.error?.message}
                            </ChakraField.ErrorText>
                        </ChakraField.Root>
                    )}
                />
            )}
            {extractors.length === 1 && extractor && (
                <Text color="fg.muted" fontSize="sm">
                    {extractorDisplayLabel(extractor, t)}
                </Text>
            )}

            {extractor &&
                groupFieldRows(extractor.fields).map((row) =>
                    row.length === 1 ? (
                        <DescriptorField key={`${extractor.id}:${row[0].name}`} field={row[0]} />
                    ) : (
                        <Grid
                            key={`${extractor.id}:${row.map((field) => field.name).join(":")}`}
                            templateColumns={{ base: "1fr", md: "1fr 1fr" }}
                            gap={3}
                            alignItems="start"
                        >
                            {row.map((field) => (
                                <DescriptorField key={field.name} field={field} />
                            ))}
                        </Grid>
                    ),
                )}
        </Stack>
    );
}
