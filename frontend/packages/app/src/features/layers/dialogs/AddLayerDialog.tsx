// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

// AddLayerDialog.tsx
import { Field, FileUpload, HStack, Input, Stack, Text } from "@chakra-ui/react";
import { RoleInfoTooltip } from "~/features/layer-groups/RoleInfoTooltip";
import { ACCESS_SCOPE_DESCRIPTIONS } from "~/features/layer-groups/layer-groups.util";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { LuUpload } from "react-icons/lu";
import { FormDialog, OcSelect, OcTabSelect, type SelectItem } from "@opencop/ui";
import { useToast } from "~/features/notifications/use-toast.hook";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { layerMutations } from "~/features/layers/layers.queries";
import { profileQueries } from "~/features/data-sources/data-source-profiles.queries";
import { ApiError, type AccessScope } from "@opencop/api";
import { useIsAdmin } from "~/features/auth/use-is-admin.hook";

export interface CreateLayerPayload {
    name: string;
    accessScope: AccessScope;
}

const ACCEPTED_FILE_TYPES = {
    "text/xml": [".xml"],
    "text/csv": [".csv"],
    "application/json": [".json"],
    "application/vnd.google-earth.kml+xml": [".kml"],
    "application/vnd.google-earth.kmz": [".kmz"],
};

interface CreateLayerModalProps {
    isOpen: boolean;
    onClose: () => void;
    onCreate?: (data: CreateLayerPayload) => void;
}

export function AddLayerDialog({ isOpen, onClose, onCreate }: CreateLayerModalProps) {
    const { t } = useTranslation();

    const accessScopes: SelectItem[] = [
        { label: t("adminLayers.scopePrivate"), value: "PRIVATE" },
        { label: t("adminLayers.scopeGlobal"), value: "GLOBAL" },
    ];
    const { showToast } = useToast();
    const queryClient = useQueryClient();
    const createLayerMutation = useMutation(layerMutations.create(queryClient));
    const importFileMutation = useMutation(layerMutations.importFile(queryClient));
    const isAdmin = useIsAdmin();

    const [form, setForm] = useState<CreateLayerPayload>({ name: "", accessScope: "PRIVATE" });
    const [file, setFile] = useState<File | null>(null);
    const [mapperId, setMapperId] = useState("");
    const [submitError, setSubmitError] = useState<string | null>(null);

    const { data: mappers } = useQuery(profileQueries.mapperProfileList());

    const mapperItems = useMemo(
        () => (mappers ?? []).map((m) => ({ label: m.name, value: m.id })),
        [mappers],
    );

    const canSubmit = form.name.trim() !== "" && (!file || mapperId !== "");
    const isSubmitting = createLayerMutation.isPending || importFileMutation.isPending;

    const resetForm = () => {
        setForm({ name: "", accessScope: "PRIVATE" });
        setFile(null);
        setMapperId("");
        setSubmitError(null);
    };

    const handleSubmit = async () => {
        if (!canSubmit) return;

        setSubmitError(null);

        try {
            const newLayer = await createLayerMutation.mutateAsync({
                name: form.name.trim(),
                accessScope: form.accessScope,
            });

            if (file && mapperId) {
                try {
                    await importFileMutation.mutateAsync({
                        layerId: newLayer.id,
                        mapperId,
                        file,
                    });
                } catch (importErr) {
                    if (onCreate) onCreate(newLayer);
                    resetForm();
                    onClose();
                    showToast({
                        type: "warning",
                        title: t("adminLayers.importFailedTitle"),
                        description:
                            importErr instanceof Error
                                ? importErr.message
                                : t("adminLayers.failedToImport"),
                    });
                    return;
                }
            }

            resetForm();
            onClose();
            try {
                if (onCreate) onCreate(newLayer);
            } catch {
                showToast({
                    type: "warning",
                    title: t("adminLayers.createdButCallbackFailed"),
                });
            }
        } catch (err) {
            if (err instanceof ApiError && err.status === 409) {
                setSubmitError(t("adminLayers.layerExists"));
            } else {
                setSubmitError(
                    err instanceof Error ? err.message : t("adminLayers.failedToCreate"),
                );
            }
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
            secondaryText={t("adminLayers.newLayer")}
            onSubmit={handleSubmit}
            submitLabel={t("common.create")}
            cancelLabel={t("common.cancel")}
            isSubmitting={isSubmitting}
            size="sm"
        >
            <Stack gap={4}>
                <Field.Root invalid={!!submitError}>
                    <Field.Label>{t("adminLayers.layerName")}</Field.Label>
                    <Input
                        name="name"
                        value={form.name}
                        onChange={(e) => setForm({ ...form, name: e.target.value })}
                        placeholder={t("adminLayers.enterLayerName")}
                    />
                    {submitError && <Field.ErrorText>{submitError}</Field.ErrorText>}
                </Field.Root>

                <Field.Root>
                    <Field.Label>
                        <HStack gap={1}>
                            {t("adminLayers.accessScope")}
                            <RoleInfoTooltip roles={ACCESS_SCOPE_DESCRIPTIONS} />
                        </HStack>
                    </Field.Label>
                    <OcTabSelect
                        onChange={(v) =>
                            setForm((prev) => ({ ...prev, accessScope: v as AccessScope }))
                        }
                        defaultValue={"PRIVATE"}
                        options={accessScopes}
                        disabled={!isAdmin}
                        disabledReason={!isAdmin ? t("layers.adminOnlyGlobalScope") : undefined}
                    />
                </Field.Root>

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
            </Stack>
        </FormDialog>
    );
}
