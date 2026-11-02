// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Spinner, Stack, Text } from "@chakra-ui/react";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { ConfirmDialog } from "@opencop/ui";
import { groupQueries } from "~/features/groups/groups.queries";

interface Props {
    isOpen: boolean;
    onClose: () => void;
    onConfirm: () => Promise<void>;
    group: { id: string; name: string };
    currentUserId: string;
}

export function ConfirmRevokeGroupDialog({
    isOpen,
    onClose,
    onConfirm,
    group,
    currentUserId,
}: Props) {
    const { t } = useTranslation();
    const [isPending, setIsPending] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const { data: members = [], isPending: isMembersLoading } = useQuery({
        ...groupQueries.members(group.id),
        enabled: isOpen && !!group.id,
    });

    const isCurrentUserInGroup = members.some((m) => m.userId === currentUserId);

    const confirmMessage = isCurrentUserInGroup
        ? t("adminDialogs.revokeGroupOwnershipWarning", { groupName: group.name })
        : t("adminDialogs.revokeGroupConfirm", { groupName: group.name });

    function handleConfirm() {
        setIsPending(true);
        setError(null);
        onConfirm()
            .then(() => {
                setIsPending(false);
                onClose();
            })
            .catch((err: unknown) => {
                setError(err instanceof Error ? err.message : t("adminDialogs.somethingWentWrong"));
                setIsPending(false);
            });
    }

    return (
        <ConfirmDialog
            open={isOpen}
            onClose={onClose}
            onConfirm={handleConfirm}
            title={t("adminDialogs.revokeGroupTitle")}
            secondaryText={t("adminConfirmDelete.action")}
            loading={isPending || isMembersLoading}
            dismissDuringLoading={!isPending}
            disabled={isPending || isMembersLoading}
            message={
                isMembersLoading ? (
                    <Spinner size="sm" />
                ) : (
                    <Stack gap={2}>
                        <Text>{confirmMessage}</Text>
                        {error && (
                            <Text color="red.emphasized" fontSize="sm">
                                {error}
                            </Text>
                        )}
                    </Stack>
                )
            }
        />
    );
}
