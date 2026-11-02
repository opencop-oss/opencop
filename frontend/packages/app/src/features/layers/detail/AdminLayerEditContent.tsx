// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Field, Input, Stack } from "@chakra-ui/react";
import type { LayerInfo } from "@opencop/api";
import { zodResolver } from "@hookform/resolvers/zod";
import type { ReactElement } from "react";
import { Controller, useForm, type SubmitHandler } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { OcTabSelect, type SelectItem } from "@opencop/ui";
import z from "zod";
import { DrawerEditActions } from "~/features/drawers/DrawerEditActions";
import { getLayerSaveErrorMessage, layerMutations } from "../layers.queries";

interface AdminLayerEditContentProps {
    layer: LayerInfo;
    /** Called on successful save or cancel — returns the drawer to view mode. */
    onDone: () => void;
}

const editLayerSchema = z.object({
    name: z.string().trim().min(1),
    accessScope: z.enum(["PRIVATE", "GLOBAL"]),
});

type EditLayerForm = z.infer<typeof editLayerSchema>;

export function AdminLayerEditContent({ layer, onDone }: AdminLayerEditContentProps): ReactElement {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const { mutateAsync: patch, isPending } = useMutation(layerMutations.patch(queryClient));
    const { control, register, handleSubmit, setError, formState } = useForm<EditLayerForm>({
        resolver: zodResolver(editLayerSchema),
        defaultValues: {
            name: layer.name,
            accessScope: layer.accessScope,
        },
    });

    const accessScopes: SelectItem[] = [
        { label: t("adminLayers.scopePrivate"), value: "PRIVATE" },
        { label: t("adminLayers.scopeGlobal"), value: "GLOBAL" },
    ];

    const onSubmit: SubmitHandler<EditLayerForm> = async (data) => {
        try {
            await patch({ id: layer.id, name: data.name, accessScope: data.accessScope });
            onDone();
        } catch (err) {
            setError("root", { message: getLayerSaveErrorMessage(err, t) });
        }
    };

    return (
        <form noValidate onSubmit={handleSubmit(onSubmit)}>
            <Stack gap={4}>
                <Field.Root required invalid={!!formState.errors.name}>
                    <Field.Label>
                        {t("adminLayers.name")}
                        <Field.RequiredIndicator />
                    </Field.Label>
                    <Input {...register("name")} placeholder={t("adminLayers.enterLayerName")} />
                    <Field.ErrorText>{t("common.fieldRequired")}</Field.ErrorText>
                </Field.Root>

                <Field.Root>
                    <Field.Label>{t("adminLayers.accessScope")}</Field.Label>
                    <Controller
                        name="accessScope"
                        control={control}
                        render={({ field }) => (
                            <OcTabSelect
                                options={accessScopes}
                                defaultValue={field.value}
                                onChange={field.onChange}
                            />
                        )}
                    />
                </Field.Root>

                <Field.Root>
                    <Field.Label>{t("adminLayers.createdAt")}</Field.Label>
                    <Input value={layer.createdAt} disabled opacity={0.6} cursor="not-allowed" />
                </Field.Root>

                {layer.description && (
                    <Field.Root>
                        <Field.Label>{t("common.description")}</Field.Label>
                        <Input
                            value={layer.description}
                            disabled
                            opacity={0.6}
                            cursor="not-allowed"
                        />
                    </Field.Root>
                )}

                {formState.errors.root && (
                    <Field.Root invalid>
                        <Field.ErrorText>{formState.errors.root.message}</Field.ErrorText>
                    </Field.Root>
                )}

                <DrawerEditActions
                    onCancel={onDone}
                    loading={isPending}
                    canSave={formState.isDirty}
                />
            </Stack>
        </form>
    );
}
