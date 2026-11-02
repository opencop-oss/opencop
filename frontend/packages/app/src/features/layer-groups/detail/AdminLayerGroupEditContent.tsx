// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Field, HStack, Input, Stack } from "@chakra-ui/react";
import { zodResolver } from "@hookform/resolvers/zod";
import type { ReactElement } from "react";
import { Controller, useForm, type SubmitHandler } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { getErrorMessage } from "@opencop/api";
import { OcTabSelect, type SelectItem } from "@opencop/ui";
import z from "zod";
import { DrawerEditActions } from "~/features/drawers/DrawerEditActions";
import { layerGroupMutations } from "~/features/layer-groups/layer-groups.queries";
import { RoleInfoTooltip } from "~/features/layer-groups/RoleInfoTooltip";
import { ACCESS_SCOPE_DESCRIPTIONS } from "~/features/layer-groups/layer-groups.util";
import type { LayerGroup } from "~/features/layer-groups/layer-groups.queries";

interface AdminLayerGroupEditContentProps {
    group: LayerGroup;
    /** Called on successful save or cancel — returns the drawer to view mode. */
    onDone: () => void;
}

const editLayerGroupSchema = z.object({
    name: z.string().trim().min(1),
    description: z.string(),
    accessScope: z.enum(["PRIVATE", "GLOBAL"]),
});

type EditLayerGroupForm = z.infer<typeof editLayerGroupSchema>;

export function AdminLayerGroupEditContent({
    group,
    onDone,
}: AdminLayerGroupEditContentProps): ReactElement {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const { mutateAsync: patch, isPending } = useMutation(layerGroupMutations.patch(queryClient));
    const { control, register, handleSubmit, setError, formState } = useForm<EditLayerGroupForm>({
        resolver: zodResolver(editLayerGroupSchema),
        defaultValues: {
            name: group.name,
            description: group.description ?? "",
            accessScope: group.accessScope,
        },
    });

    const accessScopes: SelectItem[] = [
        { label: t("adminLayerGroups.scopePrivate"), value: "PRIVATE" },
        { label: t("adminLayerGroups.scopeGlobal"), value: "GLOBAL" },
    ];

    const onSubmit: SubmitHandler<EditLayerGroupForm> = async (data) => {
        try {
            await patch({
                id: group.id,
                name: data.name,
                description: data.description.trim() || undefined,
                accessScope: data.accessScope,
            });
            onDone();
        } catch (err) {
            setError("root", {
                message: getErrorMessage(err, t("adminLayerGroups.failedToCreate")),
            });
        }
    };

    return (
        <form noValidate onSubmit={handleSubmit(onSubmit)}>
            <Stack gap={4}>
                <Field.Root required invalid={!!formState.errors.name}>
                    <Field.Label>
                        {t("adminLayerGroups.groupName")}
                        <Field.RequiredIndicator />
                    </Field.Label>
                    <Input
                        {...register("name")}
                        placeholder={t("adminLayerGroups.enterGroupName")}
                    />
                    <Field.ErrorText>{t("common.fieldRequired")}</Field.ErrorText>
                </Field.Root>

                <Field.Root>
                    <Field.Label>{t("adminLayerGroups.description")}</Field.Label>
                    <Input
                        {...register("description")}
                        placeholder={t("adminLayerGroups.enterDescription")}
                    />
                </Field.Root>

                <Field.Root>
                    <Field.Label>
                        <HStack gap={1}>
                            {t("adminLayers.accessScope")}
                            <RoleInfoTooltip roles={ACCESS_SCOPE_DESCRIPTIONS} />
                        </HStack>
                    </Field.Label>
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
