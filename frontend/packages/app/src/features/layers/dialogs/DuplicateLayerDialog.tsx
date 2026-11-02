// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Checkbox, Dialog, Field, Input, VStack } from "@chakra-ui/react";
import { zodResolver } from "@hookform/resolvers/zod";
import { ApiError } from "@opencop/api";
import { BaseDialog, DialogActions, DialogHeader } from "@opencop/ui";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useMemo } from "react";
import { Controller, useForm, useWatch, type SubmitHandler } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { z } from "zod";
import { layerMutations } from "~/features/layers/layers.queries";

const duplicateLayerSchema = z.object({
    name: z.string().trim().min(1),
    copyRemarks: z.boolean(),
    copyMedia: z.boolean(),
});

type DuplicateLayerForm = z.infer<typeof duplicateLayerSchema>;

interface DuplicateLayerDialogProps {
    isOpen: boolean;
    onClose: () => void;
    layerId: string;
    layerName: string;
}

export function DuplicateLayerDialog({
    isOpen,
    onClose,
    layerId,
    layerName,
}: DuplicateLayerDialogProps) {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const cloneMutation = useMutation(layerMutations.clone(queryClient));

    const formValues = useMemo(
        () => ({
            name: `${layerName} (Copy)`,
            copyRemarks: true,
            copyMedia: true,
        }),
        [layerName],
    );

    const { control, register, handleSubmit, formState, setError } = useForm<DuplicateLayerForm>({
        resolver: zodResolver(duplicateLayerSchema),
        values: formValues,
    });
    const layerNameValue = useWatch({ control, name: "name" });
    const canSubmit = (layerNameValue ?? "").trim() !== "";

    const onSubmit: SubmitHandler<DuplicateLayerForm> = async (data) => {
        try {
            await cloneMutation.mutateAsync({
                layerId,
                request: {
                    name: data.name.trim(),
                    users: {},
                    copyRemarks: data.copyRemarks,
                    copyMedia: data.copyMedia,
                },
            });

            onClose();
        } catch (err) {
            if (err instanceof ApiError && err.status === 409) {
                setError("name", { message: t("adminLayers.layerExists") });
            } else {
                setError("name", {
                    message: err instanceof Error ? err.message : t("layers.duplicateError"),
                });
            }
        }
    };

    return (
        <BaseDialog open={isOpen} onClose={onClose} size="sm">
            <form noValidate onSubmit={handleSubmit(onSubmit)}>
                <DialogHeader
                    title={t("layers.duplicateLayer")}
                    secondaryText={t("layers.duplicateLayerDescription")}
                />
                <Dialog.Body>
                    <VStack gap={4} alignItems="flex-start">
                        <Field.Root invalid={!!formState.errors.name} required>
                            <Field.Label>
                                {t("layers.newLayerName")}
                                <Field.RequiredIndicator />
                            </Field.Label>
                            <Input
                                {...register("name")}
                                placeholder={t("adminLayers.enterLayerName")}
                            />
                            <Field.ErrorText>{formState.errors.name?.message}</Field.ErrorText>
                        </Field.Root>

                        <Controller
                            name="copyRemarks"
                            control={control}
                            render={({ field }) => (
                                <Checkbox.Root
                                    checked={field.value}
                                    onCheckedChange={(e) => field.onChange(!!e.checked)}
                                >
                                    <Checkbox.HiddenInput />
                                    <Checkbox.Control />
                                    <Checkbox.Label>{t("layers.copyRemarks")}</Checkbox.Label>
                                </Checkbox.Root>
                            )}
                        />

                        <Controller
                            name="copyMedia"
                            control={control}
                            render={({ field }) => (
                                <Checkbox.Root
                                    checked={field.value}
                                    onCheckedChange={(e) => field.onChange(!!e.checked)}
                                >
                                    <Checkbox.HiddenInput />
                                    <Checkbox.Control />
                                    <Checkbox.Label>{t("layers.copyMedia")}</Checkbox.Label>
                                </Checkbox.Root>
                            )}
                        />
                    </VStack>
                </Dialog.Body>
                <DialogActions
                    actionType="submit"
                    actionLabel={t("layers.duplicate")}
                    cancelLabel={t("common.cancel")}
                    disabled={!canSubmit}
                    loading={cloneMutation.isPending}
                    onClose={onClose}
                />
            </form>
        </BaseDialog>
    );
}
