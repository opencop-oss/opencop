// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Field, Input, Stack } from "@chakra-ui/react";
import { zodResolver } from "@hookform/resolvers/zod";
import type { ReactElement } from "react";
import { useForm, type SubmitHandler } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { getErrorMessage } from "@opencop/api";
import z from "zod";
import { DrawerEditActions } from "~/features/drawers/DrawerEditActions";
import { groupMutations } from "../groups.queries";
import type { Group } from "../groups.queries";

interface AdminGroupEditContentProps {
    group: Group;
    /** Called on successful save or cancel — returns the drawer to view mode. */
    onDone: () => void;
}

const editGroupSchema = z.object({
    name: z.string().trim().min(1),
    description: z.string(),
});

type EditGroupForm = z.infer<typeof editGroupSchema>;

/**
 * Inline group edit form. Rendered in the drawer's content panel (via
 * `DrawerSideNavLayout`'s `editContent`), so it is just fields + Save/Cancel —
 * no drawer header/footer of its own.
 */
export function AdminGroupEditContent({ group, onDone }: AdminGroupEditContentProps): ReactElement {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const { mutateAsync: patch, isPending } = useMutation(groupMutations.patch(queryClient));
    const { register, handleSubmit, setError, formState } = useForm<EditGroupForm>({
        resolver: zodResolver(editGroupSchema),
        defaultValues: {
            name: group.name,
            description: group.description ?? "",
        },
    });

    const onSubmit: SubmitHandler<EditGroupForm> = async (data) => {
        try {
            await patch({
                groupId: group.id,
                name: data.name,
                description: data.description.trim() || undefined,
            });
            onDone();
        } catch (err) {
            setError("root", { message: getErrorMessage(err, t("adminGroups.failedToCreate")) });
        }
    };

    return (
        <form noValidate onSubmit={handleSubmit(onSubmit)}>
            <Stack gap={4}>
                <Field.Root required invalid={!!formState.errors.name}>
                    <Field.Label>
                        {t("adminGroups.groupName")}
                        <Field.RequiredIndicator />
                    </Field.Label>
                    <Input {...register("name")} placeholder={t("adminGroups.enterGroupName")} />
                    <Field.ErrorText>{t("common.fieldRequired")}</Field.ErrorText>
                </Field.Root>

                <Field.Root>
                    <Field.Label>{t("adminGroups.description")}</Field.Label>
                    <Input
                        {...register("description")}
                        placeholder={t("adminGroups.enterDescription")}
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
