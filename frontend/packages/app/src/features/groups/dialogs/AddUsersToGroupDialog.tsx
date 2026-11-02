// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Badge, Box, Flex, Text } from "@chakra-ui/react";
import { useState, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { FaUser } from "react-icons/fa";
import { FormDialog, ResourceTable, groupNameColumn } from "@opencop/ui";
import type { RowSelectionState } from "@opencop/ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { groupMutations, groupQueries } from "~/features/groups/groups.queries";
import type { Group } from "~/features/groups/groups.queries";
import { useToast } from "~/features/notifications/use-toast.hook";

interface SelectedUser {
    id: string;
    name: string;
}

interface AddUsersToGroupDialogProps {
    isOpen: boolean;
    onClose: () => void;
    users: SelectedUser[];
}

export function AddUsersToGroupDialog({ isOpen, onClose, users }: AddUsersToGroupDialogProps) {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const { showToast } = useToast();

    const { data: groups = [], isLoading: groupsLoading } = useQuery(groupQueries.adminList());

    const patchMutation = useMutation(groupMutations.patchMembers(queryClient));

    const [rowSelection, setRowSelection] = useState<RowSelectionState>({});
    const [globalFilter, setGlobalFilter] = useState("");

    const columns = useMemo(
        () => [groupNameColumn<Group>(t("adminGroups.name"), t("adminGroups.scimManaged"))],
        [t],
    );

    const resetAndClose = () => {
        setRowSelection({});
        setGlobalFilter("");
        onClose();
    };

    const handleAdd = async () => {
        const selectedGroupIds = Object.keys(rowSelection);
        if (selectedGroupIds.length === 0) return;
        const userIds = users.map((u) => u.id);
        const results = await Promise.allSettled(
            selectedGroupIds.map(async (groupId) => {
                const members = await queryClient.fetchQuery(groupQueries.members(groupId));
                const memberUserIds = new Set(members.map((m) => m.userId));
                const toAdd = userIds.filter((id) => !memberUserIds.has(id));
                if (toAdd.length > 0) {
                    await patchMutation.mutateAsync({ groupId, add: toAdd });
                }
            }),
        );
        const failures = results.filter((r) => r.status === "rejected").length;
        if (failures > 0) {
            showToast({ type: "error", title: t("adminUsers.addToGroupFailed") });
        } else {
            resetAndClose();
        }
    };

    const selectedGroupCount = Object.keys(rowSelection).length;

    return (
        <FormDialog
            open={isOpen}
            onClose={resetAndClose}
            title={t("adminGroups.title")}
            secondaryText={t("adminUsers.addToGroup")}
            onSubmit={handleAdd}
            submitLabel={t("common.add")}
            cancelLabel={t("common.cancel")}
            isSubmitting={patchMutation.isPending}
            submitDisabled={selectedGroupCount === 0}
            size="lg"
            contentWidth="640px"
        >
            <Flex direction="column" gap={5}>
                {/* Selected users chips */}
                <Box>
                    <Text
                        fontSize="9px"
                        fontWeight="bold"
                        textTransform="uppercase"
                        letterSpacing="0.1em"
                        color="fg.muted"
                        mb={2}
                    >
                        {t("adminUsers.selectedUsers")} ({users.length})
                    </Text>
                    <Flex wrap="wrap" gap={2}>
                        {users.map((user) => (
                            <Badge
                                key={user.id}
                                variant="subtle"
                                colorPalette="gray"
                                display="inline-flex"
                                alignItems="center"
                                gap={1}
                                px={2}
                                py={1}
                            >
                                <FaUser size={9} />
                                <Text fontSize="xs">{user.name}</Text>
                            </Badge>
                        ))}
                    </Flex>
                </Box>

                {/* Groups resource table */}
                <Box borderTop="1px solid" borderColor="border.muted" pt={4}>
                    <Text
                        fontSize="9px"
                        fontWeight="bold"
                        textTransform="uppercase"
                        letterSpacing="0.1em"
                        color="active"
                        mb={3}
                    >
                        {t("adminGroups.title")}
                    </Text>
                    <Box h="320px">
                        <ResourceTable
                            aria-label={t("adminGroups.title")}
                            data={groups}
                            columns={columns}
                            isLoading={groupsLoading}
                            getRowId={(row) => row.id}
                            enableRowSelection
                            rowSelection={rowSelection}
                            onRowSelectionChange={setRowSelection}
                            globalFilter={globalFilter}
                            onGlobalFilterChange={setGlobalFilter}
                            toolbar={{}}
                        />
                    </Box>
                </Box>
            </Flex>
        </FormDialog>
    );
}
