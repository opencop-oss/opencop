// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Field, HStack, Input, Stack } from "@chakra-ui/react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { FormDialog, OcTabSelect, type SelectItem } from "@opencop/ui";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { layerGroupMutations } from "~/features/layer-groups/layer-groups.queries";
import type { LayerGroupAccessScope } from "~/features/layer-groups/layer-groups.queries";
import { RoleInfoTooltip } from "~/features/layer-groups/RoleInfoTooltip";
import { useIsAdmin } from "~/features/auth/use-is-admin.hook";
import { ACCESS_SCOPE_DESCRIPTIONS } from "~/features/layer-groups/layer-groups.util";

interface AddLayerGroupDialogProps {
    isOpen: boolean;
    onClose: () => void;
}

export function AddLayerGroupDialog({ isOpen, onClose }: AddLayerGroupDialogProps) {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const isAdmin = useIsAdmin();
    const createMutation = useMutation(layerGroupMutations.create(queryClient));

    const [name, setName] = useState("");
    const [description, setDescription] = useState("");
    const [accessScope, setAccessScope] = useState<LayerGroupAccessScope>("PRIVATE");
    const [submitError, setSubmitError] = useState<string | null>(null);

    const accessScopes: SelectItem[] = [
        { label: t("adminLayerGroups.scopePrivate"), value: "PRIVATE" },
        { label: t("adminLayerGroups.scopeGlobal"), value: "GLOBAL" },
    ];

    const canSubmit = name.trim() !== "";

    const resetForm = () => {
        setName("");
        setDescription("");
        setAccessScope("PRIVATE");
        setSubmitError(null);
    };

    const handleSubmit = async () => {
        if (!canSubmit) return;
        setSubmitError(null);
        try {
            await createMutation.mutateAsync({
                name: name.trim(),
                description: description.trim() || undefined,
                accessScope,
            });
            resetForm();
            onClose();
        } catch (err) {
            setSubmitError(
                err instanceof Error ? err.message : t("adminLayerGroups.failedToCreate"),
            );
        }
    };

    return (
        <FormDialog
            open={isOpen}
            onClose={() => {
                resetForm();
                onClose();
            }}
            title={t("adminLayerGroups.title")}
            secondaryText={t("adminLayerGroups.newLayerGroup")}
            onSubmit={handleSubmit}
            submitLabel={t("common.create")}
            cancelLabel={t("common.cancel")}
            isSubmitting={createMutation.isPending}
            submitDisabled={!canSubmit}
            size="sm"
        >
            <Stack gap={4}>
                <Field.Root invalid={!!submitError}>
                    <Field.Label>{t("adminLayerGroups.groupName")}</Field.Label>
                    <Input
                        name="name"
                        value={name}
                        onChange={(e) => {
                            setName(e.target.value);
                            if (submitError) setSubmitError(null);
                        }}
                        placeholder={t("adminLayerGroups.enterGroupName")}
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
                        onChange={(v) => setAccessScope(v as LayerGroupAccessScope)}
                        options={accessScopes}
                        defaultValue="PRIVATE"
                        disabled={!isAdmin}
                        disabledReason={
                            !isAdmin ? t("adminLayerGroups.adminOnlyGlobalScope") : undefined
                        }
                    />
                </Field.Root>
                <Field.Root>
                    <Field.Label>{t("adminLayerGroups.description")}</Field.Label>
                    <Input
                        name="description"
                        value={description}
                        onChange={(e) => setDescription(e.target.value)}
                        placeholder={t("adminLayerGroups.enterDescription")}
                    />
                </Field.Root>
            </Stack>
        </FormDialog>
    );
}
