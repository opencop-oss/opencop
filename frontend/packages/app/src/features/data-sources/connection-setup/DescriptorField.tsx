// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    Field as ChakraField,
    FileUpload,
    Group,
    IconButton,
    Input,
    Stack,
    Text,
} from "@chakra-ui/react";
import type { ExtractorField } from "@opencop/api";
import { useState } from "react";
import { Controller, useFormContext, useFormState } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useRegisterField } from "~/features/data-sources/use-register-field.hook";
import { FaEye, FaEyeSlash } from "react-icons/fa6";
import { LuUpload } from "react-icons/lu";
import type { ConnectionSetupFormValues } from "./use-connection-setup.hook";
import {
    descriptorFieldLabel,
    hasAcceptedExtension,
    isValidPort,
    MAX_PORT,
    MIN_PORT,
} from "./connection-setup.util";

interface DescriptorFieldProps {
    field: ExtractorField;
}

/**
 * One extractor field as a form input, chosen by its `kind`. Text kinds register under
 * `values.<name>` and file kinds under `files.<name>`. Only presence, declared file types, and the
 * range of a `PORT` standard field are checked here; the extractor validates content on submit.
 */
export function DescriptorField({ field }: DescriptorFieldProps) {
    const { t } = useTranslation();
    const { control } = useFormContext<ConnectionSetupFormValues>();
    // Clears the field's error per keystroke; see useRegisterField.
    const register = useRegisterField<ConnectionSetupFormValues>();
    const { errors } = useFormState({ control });
    const [showSecret, setShowSecret] = useState(false);
    const label = descriptorFieldLabel(field, t);
    const requiredMessage = t("connectionSetup.fieldRequired", { label });
    const inputId = `connection-setup-field-${field.name}`;

    if (field.kind === "TEXT" || field.kind === "SECRET") {
        const error = errors.values?.[field.name];
        const registration = register(`values.${field.name}`, {
            required: field.required ? requiredMessage : false,
            // Descriptors have no numeric kind, so a port arrives as TEXT.
            validate:
                field.standardKey === "PORT"
                    ? (value) =>
                          isValidPort(value) ||
                          t("connectionSetup.portInvalid", { min: MIN_PORT, max: MAX_PORT })
                    : undefined,
        });
        return (
            <ChakraField.Root invalid={!!error} required={field.required}>
                <ChakraField.Label htmlFor={inputId}>
                    {label}
                    <ChakraField.RequiredIndicator />
                </ChakraField.Label>
                {field.kind === "TEXT" ? (
                    <Input {...registration} id={inputId} />
                ) : (
                    <Group attached w="full">
                        <Input
                            {...registration}
                            id={inputId}
                            type={showSecret ? "text" : "password"}
                            autoComplete="off"
                            flex={1}
                        />
                        <IconButton
                            aria-label={showSecret ? t("common.hide") : t("common.show")}
                            variant="outline"
                            size="md"
                            onClick={() => setShowSecret(!showSecret)}
                        >
                            {showSecret ? <FaEyeSlash /> : <FaEye />}
                        </IconButton>
                    </Group>
                )}
                <ChakraField.ErrorText>{error?.message}</ChakraField.ErrorText>
            </ChakraField.Root>
        );
    }

    if (field.kind !== "FILE" && field.kind !== "FILE_LIST") {
        // A kind newer than this build: say so rather than guess an input for it.
        return (
            <ChakraField.Root invalid={field.required}>
                <ChakraField.Label>{label}</ChakraField.Label>
                <Text color="fg.muted" fontSize="sm">
                    {t("connectionSetup.unsupportedField")}
                </Text>
            </ChakraField.Root>
        );
    }

    const multiple = field.kind === "FILE_LIST";
    const accept = [...(field.extensions ?? []), ...(field.mediaTypes ?? [])];
    return (
        <Controller
            control={control}
            name={`files.${field.name}`}
            defaultValue={[]}
            rules={{
                validate: (files = []) => {
                    if (field.required && files.length === 0) return requiredMessage;
                    if (files.some((file) => !hasAcceptedExtension(file, field.extensions))) {
                        return t("connectionSetup.fileTypeNotAccepted", {
                            types: (field.extensions ?? []).join(", "),
                        });
                    }
                    return true;
                },
            }}
            render={({ field: controller, fieldState }) => {
                const chosen = controller.value ?? [];
                return (
                    <ChakraField.Root invalid={fieldState.invalid} required={field.required}>
                        <ChakraField.Label>
                            {label}
                            <ChakraField.RequiredIndicator />
                        </ChakraField.Label>
                        <FileUpload.Root
                            maxFiles={multiple ? undefined : 1}
                            accept={accept.length > 0 ? accept : undefined}
                            invalid={fieldState.invalid}
                            onFileAccept={(details) => {
                                controller.onChange(details.files);
                                controller.onBlur();
                            }}
                        >
                            <FileUpload.HiddenInput aria-label={label} />
                            {/* The hidden input is aria-hidden, so a rejected file is focused on
                                the drop zone, named after the field rather than Ark's generic
                                "dropzone". */}
                            <FileUpload.Dropzone
                                ref={controller.ref}
                                aria-label={label}
                                w="full"
                                minH="10"
                                flexDirection="row"
                                gap={2}
                                py={2}
                                _invalid={{ borderColor: "border.error" }}
                            >
                                <LuUpload />
                                <FileUpload.DropzoneContent>
                                    {chosen.length > 0 ? (
                                        <Stack gap={0}>
                                            {chosen.map((file) => (
                                                <Text key={file.name} color="type.success">
                                                    {file.name}
                                                </Text>
                                            ))}
                                        </Stack>
                                    ) : (
                                        <Text>{t("connectionSetup.dropFiles")}</Text>
                                    )}
                                </FileUpload.DropzoneContent>
                            </FileUpload.Dropzone>
                        </FileUpload.Root>
                        <ChakraField.ErrorText>{fieldState.error?.message}</ChakraField.ErrorText>
                    </ChakraField.Root>
                );
            }}
        />
    );
}
