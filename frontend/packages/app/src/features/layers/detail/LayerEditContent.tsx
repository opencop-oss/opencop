// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Field, Input, Stack } from "@chakra-ui/react";
import type { MyLayerInfo } from "@opencop/api";
import { zodResolver } from "@hookform/resolvers/zod";
import type { ReactElement } from "react";
import { Controller, useForm, type SubmitHandler } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { OcTabSelect, type SelectItem } from "@opencop/ui";
import z from "zod";
import { formatDateOnly } from "~/date.util";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import { DrawerEditActions } from "~/features/drawers/DrawerEditActions";
import { useIsAdmin } from "~/features/auth/use-is-admin.hook";
import { getLayerSaveErrorMessage, layerMutations } from "../layers.queries";

interface LayerEditContentProps {
    layer: MyLayerInfo;
    /** Called on successful save or cancel — returns the drawer to view mode. */
    onDone: () => void;
}

const editLayerSchema = z.object({
    name: z.string().trim().min(1),
    accessScope: z.enum(["PRIVATE", "GLOBAL"]),
});

type EditLayerForm = z.infer<typeof editLayerSchema>;

export function LayerEditContent({ layer, onDone }: LayerEditContentProps): ReactElement {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const isAdmin = useIsAdmin();
    const timezone = useUserTimezone();
    const { mutateAsync: patch, isPending } = useMutation(layerMutations.patch(queryClient));
    const { control, register, handleSubmit, setError, formState } = useForm<EditLayerForm>({
        resolver: zodResolver(editLayerSchema),
        defaultValues: {
            name: layer.layerName,
            accessScope: layer.accessScope,
        },
    });

    const accessScopes: SelectItem[] = [
        { label: t("adminLayers.scopePrivate"), value: "PRIVATE" },
        { label: t("adminLayers.scopeGlobal"), value: "GLOBAL" },
    ];

    const onSubmit: SubmitHandler<EditLayerForm> = async (data) => {
        try {
            await patch({
                id: layer.layerId,
                ...(formState.dirtyFields.name && { name: data.name }),
                ...(formState.dirtyFields.accessScope && { accessScope: data.accessScope }),
            });
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
                        {t("layers.name")}
                        <Field.RequiredIndicator />
                    </Field.Label>
                    <Input {...register("name")} placeholder={t("adminLayers.enterLayerName")} />
                    <Field.ErrorText>{t("common.fieldRequired")}</Field.ErrorText>
                </Field.Root>

                <Field.Root>
                    <Field.Label>{t("layers.role")}</Field.Label>
                    <Input value={layer.role} disabled opacity={0.6} cursor="not-allowed" />
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
                                disabled={!isAdmin}
                            />
                        )}
                    />
                </Field.Root>

                {layer.createdAt && (
                    <Field.Root>
                        <Field.Label>{t("layers.createdAt")}</Field.Label>
                        <Input
                            value={formatDateOnly(layer.createdAt, timezone)}
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
