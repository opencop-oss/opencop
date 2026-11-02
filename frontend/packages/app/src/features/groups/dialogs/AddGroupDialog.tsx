// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Field, Input, Stack } from "@chakra-ui/react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { FormDialog } from "@opencop/ui";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { groupMutations } from "~/features/groups/groups.queries";

interface AddGroupDialogProps {
    isOpen: boolean;
    onClose: () => void;
}

export function AddGroupDialog({ isOpen, onClose }: AddGroupDialogProps) {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const createGroupMutation = useMutation(groupMutations.create(queryClient));

    const [name, setName] = useState("");
    const [description, setDescription] = useState("");
    const [submitError, setSubmitError] = useState<string | null>(null);

    const canSubmit = name.trim() !== "";

    const resetForm = () => {
        setName("");
        setDescription("");
        setSubmitError(null);
    };

    const handleSubmit = async () => {
        if (!canSubmit) return;
        setSubmitError(null);
        try {
            await createGroupMutation.mutateAsync({
                name: name.trim(),
                description: description.trim() || undefined,
            });
            resetForm();
            onClose();
        } catch (err) {
            setSubmitError(err instanceof Error ? err.message : t("adminGroups.failedToCreate"));
        }
    };

    return (
        <FormDialog
            open={isOpen}
            onClose={() => {
                resetForm();
                onClose();
            }}
            title={t("adminGroups.title")}
            secondaryText={t("adminGroups.newGroup")}
            onSubmit={handleSubmit}
            submitLabel={t("common.create")}
            cancelLabel={t("common.cancel")}
            isSubmitting={createGroupMutation.isPending}
            submitDisabled={!canSubmit}
            size="sm"
        >
            <Stack gap={4}>
                <Field.Root invalid={!!submitError}>
                    <Field.Label>{t("adminGroups.groupName")}</Field.Label>
                    <Input
                        name="name"
                        value={name}
                        onChange={(e) => {
                            setName(e.target.value);
                            if (submitError) setSubmitError(null);
                        }}
                        placeholder={t("adminGroups.enterGroupName")}
                    />
                    {submitError && <Field.ErrorText>{submitError}</Field.ErrorText>}
                </Field.Root>
                <Field.Root>
                    <Field.Label>{t("adminGroups.description")}</Field.Label>
                    <Input
                        name="description"
                        value={description}
                        onChange={(e) => setDescription(e.target.value)}
                        placeholder={t("adminGroups.enterDescription")}
                    />
                </Field.Root>
            </Stack>
        </FormDialog>
    );
}
