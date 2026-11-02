// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Stack, Text } from "@chakra-ui/react";
import { downloadExport } from "@opencop/api";
import { DefaultCheckbox, FormDialog } from "@opencop/ui";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { exportMutations } from "~/features/exports/exports.queries";
import {
    exportSubjectsWithFallback,
    formatNamedExportSubjects,
    type NamedExportSubject,
} from "~/features/exports/exports.util";
import { useToast } from "~/features/notifications/use-toast.hook";

interface ExportLayersDialogProps {
    isOpen: boolean;
    onClose: () => void;
    subjects: NamedExportSubject[];
    title: string;
}

export function ExportLayersDialog({ isOpen, onClose, subjects, title }: ExportLayersDialogProps) {
    const { t } = useTranslation();
    const { showToast } = useToast();
    const queryClient = useQueryClient();
    const exportMutation = useMutation(exportMutations.create(queryClient));
    const [includeTransitiveData, setIncludeTransitiveData] = useState(false);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const handleClose = () => {
        setIncludeTransitiveData(false);
        onClose();
    };

    const handleSubmit = async () => {
        if (subjects.length === 0) return;

        setIsSubmitting(true);
        try {
            const result = await exportSubjectsWithFallback({
                createExport: (request, callbacks) => exportMutation.mutate(request, callbacks),
                downloadExport,
                includeTransitiveData,
                subjectType: "LAYER",
                subjects,
            });
            const hasCreatedExports = result.successCount > 0 || result.downloadFailures.length > 0;

            if (result.noDataFailures.length > 0) {
                showToast({
                    type: hasCreatedExports ? "warning" : "error",
                    title: hasCreatedExports
                        ? t("exports.partialNoDataTitle")
                        : t("adminLayers.exportFailed"),
                    description:
                        result.noDataFailures.length === 1
                            ? t("exports.selectedItemDoesNotContainData", {
                                  item: formatNamedExportSubjects(
                                      result.noDataFailures.map((failure) => failure.subject),
                                      t("adminLayers.layer"),
                                      (count) => t("exports.andMore", { count }),
                                      1,
                                  ),
                              })
                            : t("exports.selectedItemsDoNotContainData", {
                                  items: formatNamedExportSubjects(
                                      result.noDataFailures.map((failure) => failure.subject),
                                      t("adminLayers.layer"),
                                      (count) => t("exports.andMore", { count }),
                                  ),
                              }),
                });
            }

            if (result.downloadFailures.length > 0) {
                const downloadFailureSubjects = formatNamedExportSubjects(
                    result.downloadFailures.map((failure) => failure.subject),
                    t("adminLayers.layer"),
                    (count) => t("exports.andMore", { count }),
                );
                const onlyDownloadFailures =
                    result.successCount === 0 &&
                    result.noDataFailures.length === 0 &&
                    result.genericFailures.length === 0;

                showToast({
                    type: "warning",
                    title: onlyDownloadFailures
                        ? t("exports.downloadFailureTitle")
                        : t("exports.partialDownloadFailureTitle"),
                    description:
                        result.downloadFailures.length === 1
                            ? `${t("exports.selectedItemCreatedButCouldNotBeDownloaded", {
                                  item: downloadFailureSubjects,
                              })} ${result.downloadFailures[0].message}`
                            : `${t("exports.selectedItemsCreatedButCouldNotBeDownloaded", {
                                  items: downloadFailureSubjects,
                              })} ${result.downloadFailures[0].message}`,
                });
            }

            if (result.genericFailures.length > 0) {
                const genericFailureSubjects = formatNamedExportSubjects(
                    result.genericFailures.map((failure) => failure.subject),
                    t("adminLayers.layer"),
                    (count) => t("exports.andMore", { count }),
                );

                showToast({
                    type: "error",
                    title: hasCreatedExports
                        ? t("exports.partialFailureTitle")
                        : t("adminLayers.exportFailed"),
                    description: `${genericFailureSubjects}: ${result.genericFailures[0].message}`,
                });
            }

            if (hasCreatedExports) {
                handleClose();
            }
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <FormDialog
            open={isOpen}
            onClose={handleClose}
            title={title}
            secondaryText={t("adminLayers.exportSelectedLayers")}
            onSubmit={handleSubmit}
            submitLabel={t("adminLayers.exportCsv")}
            cancelLabel={t("common.cancel")}
            isSubmitting={isSubmitting}
            submitDisabled={subjects.length === 0}
            size="sm"
        >
            <Stack gap={4}>
                <Text fontSize="sm" color="fg.muted">
                    {t("adminLayers.exportSelectionSummary", { count: subjects.length })}
                </Text>

                <DefaultCheckbox
                    checked={includeTransitiveData}
                    onCheckedChange={setIncludeTransitiveData}
                    label={t("adminLayers.includeAssignedDataSources")}
                />
            </Stack>
        </FormDialog>
    );
}
