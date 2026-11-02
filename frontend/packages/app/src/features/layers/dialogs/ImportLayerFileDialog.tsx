// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Field, FileUpload, Stack, Text } from "@chakra-ui/react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { LuUpload } from "react-icons/lu";
import { FormDialog, OcSelect } from "@opencop/ui";
import { useToast } from "~/features/notifications/use-toast.hook";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { layerMutations } from "~/features/layers/layers.queries";
import { profileQueries } from "~/features/data-sources/data-source-profiles.queries";

const ACCEPTED_FILE_TYPES = {
    "text/xml": [".xml"],
    "text/csv": [".csv"],
    "application/json": [".json"],
    "application/vnd.google-earth.kml+xml": [".kml"],
    "application/vnd.google-earth.kmz": [".kmz"],
};

interface ImportLayerFileDialogProps {
    layerId: string;
    isOpen: boolean;
    onClose: () => void;
}

export function ImportLayerFileDialog({ layerId, isOpen, onClose }: ImportLayerFileDialogProps) {
    const { t } = useTranslation();
    const { showToast } = useToast();
    const queryClient = useQueryClient();
    const importFileMutation = useMutation(layerMutations.importFile(queryClient));

    const [file, setFile] = useState<File | null>(null);
    const [mapperId, setMapperId] = useState("");
    const [submitError, setSubmitError] = useState<string | null>(null);

    const { data: mappers } = useQuery(profileQueries.mapperProfileList());

    const mapperItems = useMemo(
        () => (mappers ?? []).map((m) => ({ label: m.name, value: m.id })),
        [mappers],
    );

    const canSubmit = file !== null && mapperId !== "";
    const isSubmitting = importFileMutation.isPending;

    const resetForm = () => {
        setFile(null);
        setMapperId("");
        setSubmitError(null);
    };

    const handleSubmit = async () => {
        if (!canSubmit) return;

        setSubmitError(null);

        try {
            await importFileMutation.mutateAsync({ layerId, mapperId, file });
            showToast({
                type: "success",
                title: t("adminLayers.importSuccess"),
            });
            resetForm();
            onClose();
        } catch (err) {
            setSubmitError(err instanceof Error ? err.message : t("adminLayers.failedToImport"));
        }
    };

    return (
        <FormDialog
            open={isOpen}
            onClose={() => {
                resetForm();
                onClose();
            }}
            title={t("adminLayers.title")}
            secondaryText={t("adminActions.importFile")}
            onSubmit={handleSubmit}
            submitLabel={t("common.import")}
            cancelLabel={t("common.cancel")}
            isSubmitting={isSubmitting}
            size="sm"
        >
            <Stack gap={4}>
                <Field.Root>
                    <Field.Label>{t("adminLayers.importFile")}</Field.Label>
                    <FileUpload.Root
                        maxFiles={1}
                        accept={ACCEPTED_FILE_TYPES}
                        onFileAccept={(details) => {
                            setFile(details.files[0]);
                        }}
                    >
                        <FileUpload.HiddenInput />
                        <FileUpload.Dropzone
                            w="full"
                            minH="100px"
                            borderColor={file ? "type.success" : undefined}
                            borderStyle={file ? "solid" : undefined}
                            bg={file ? "success.500/5" : undefined}
                            _hover={file ? { bg: "success.500/10" } : undefined}
                        >
                            <LuUpload />
                            <FileUpload.DropzoneContent>
                                <Text color={file ? "type.success" : undefined}>
                                    {file ? file.name : t("adminLayers.dropFileHint")}
                                </Text>
                            </FileUpload.DropzoneContent>
                        </FileUpload.Dropzone>
                    </FileUpload.Root>
                </Field.Root>

                {file && (
                    <Field.Root>
                        <Field.Label>{t("adminLayers.selectMapper")}</Field.Label>
                        <OcSelect
                            items={mapperItems}
                            value={mapperId}
                            onValueChange={setMapperId}
                            placeholder={t("adminLayers.selectMapper")}
                            emptyMessage={t("adminLayers.noMappers")}
                        />
                    </Field.Root>
                )}

                {submitError && (
                    <Field.Root invalid>
                        <Field.ErrorText>{submitError}</Field.ErrorText>
                    </Field.Root>
                )}
            </Stack>
        </FormDialog>
    );
}
