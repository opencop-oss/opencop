// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Badge, Box, Flex, Text } from "@chakra-ui/react";
import { keyBy, omit } from "lodash";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { TbStack2 } from "react-icons/tb";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { getErrorMessage } from "@opencop/api";
import type { AccessScope, LayerRole } from "@opencop/api";
import {
    FormDialog,
    OcSelect,
    PanelSectionHeading,
    ResourceTable,
    createColumnHelper,
} from "@opencop/ui";
import type { RowSelectionState } from "@opencop/ui";
import { useToast } from "~/features/notifications/use-toast.hook";
import { layerRoleOptions } from "~/features/layers/layers.util";
import {
    PartialAssignError,
    layerGroupMutations,
    layerGroupQueries,
} from "~/features/layer-groups/layer-groups.queries";
import type { LayerGroup } from "~/features/layer-groups/layer-groups.queries";
import { assignableLayerGroups } from "~/features/layer-groups/layer-groups.util";

export interface AssignLayerSubject {
    id: string;
    name: string;
    accessScope: AccessScope;
}

interface AssignLayersToGroupDialogProps {
    isOpen: boolean;
    onClose: () => void;
    layers: AssignLayerSubject[];
}

const columnHelper = createColumnHelper<LayerGroup>();

/**
 * Assigns the selected layers to one or more layer groups with a single role. Mirrors the
 * add-users-to-a-group flow, plus a role picker (the assign endpoint requires one).
 *
 * Groups that cannot legally take every selected layer are filtered out rather than offered and
 * rejected — see `assignableLayerGroups`.
 */
export function AssignLayersToGroupDialog({
    isOpen,
    onClose,
    layers,
}: AssignLayersToGroupDialogProps) {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const { showToast } = useToast();

    const { data: groups = [], isLoading: groupsLoading } = useQuery(layerGroupQueries.adminList());
    const assignToGroups = useMutation(layerGroupMutations.assignLayersToGroups(queryClient));

    const [rowSelection, setRowSelection] = useState<RowSelectionState>({});
    const [globalFilter, setGlobalFilter] = useState("");
    const [role, setRole] = useState<LayerRole | "">("");

    const layerIds = useMemo(() => layers.map((layer) => layer.id), [layers]);
    const eligibleGroups = useMemo(
        () =>
            assignableLayerGroups(
                groups,
                layers.map((layer) => layer.accessScope),
            ),
        [groups, layers],
    );
    // Distinguish "no groups exist" from "every group was filtered out by the scope rule", so the
    // empty state can say which.
    const excludedByScope = groups.length > 0 && eligibleGroups.length === 0;

    const columns = useMemo(
        () => [
            columnHelper.accessor("name", {
                header: () => t("adminLayerGroups.name"),
                cell: (info) => info.getValue(),
                enableSorting: true,
            }),
        ],
        [t],
    );

    /**
     * Dismissal is refused while the fan-out is in flight. Neither the Cancel button nor backdrop
     * dismissal is gated on `isSubmitting`, and this dialog unmounts on close — so without this the
     * in-flight closure would outlive the component and call `onClose` a second time on settle,
     * clearing the table selection (and any modal the user had since opened) out from under them.
     */
    const handleClose = () => {
        if (assignToGroups.isPending) return;
        onClose();
    };

    const handleAssign = async () => {
        const selectedGroupIds = Object.keys(rowSelection);
        if (selectedGroupIds.length === 0 || role === "") return;

        const groupsById = keyBy(groups, "id");
        const selected = selectedGroupIds.map((id) => ({
            id,
            name: groupsById[id]?.name ?? id,
        }));

        try {
            await assignToGroups.mutateAsync({ groups: selected, layerIds, role });
            onClose();
        } catch (e) {
            // Stay open so the failed groups can be retried — but drop the ones that already
            // committed first. The assign endpoint upserts, so re-submitting them (likely with a
            // different role, since changing it is the natural retry) would silently rewrite
            // assignments the user already made.
            if (e instanceof PartialAssignError && e.succeededIds.length > 0) {
                setRowSelection((prev) => omit(prev, e.succeededIds));
            }
            // The mutation names the groups that failed — a per-group rejection is the expected
            // outcome when the caller lacks an assigning role there, and a bare count wouldn't say
            // which to retry.
            showToast({
                type: "error",
                title: t("adminLayers.assignToLayerGroupFailed"),
                description: getErrorMessage(e),
            });
        }
    };

    const selectedGroupCount = Object.keys(rowSelection).length;

    return (
        <FormDialog
            open={isOpen}
            onClose={handleClose}
            title={t("adminLayerGroups.layerGroups")}
            secondaryText={t("adminLayers.assignToLayerGroup")}
            onSubmit={handleAssign}
            submitLabel={t("common.add")}
            cancelLabel={t("common.cancel")}
            isSubmitting={assignToGroups.isPending}
            submitDisabled={selectedGroupCount === 0 || role === ""}
            size="lg"
            contentWidth="640px"
        >
            <Flex direction="column" gap={5}>
                <Box>
                    <PanelSectionHeading>
                        {t("adminLayers.selectedLayers")} ({layers.length})
                    </PanelSectionHeading>
                    <Flex wrap="wrap" gap={2}>
                        {layers.map((layer) => (
                            <Badge
                                key={layer.id}
                                variant="subtle"
                                colorPalette="gray"
                                display="inline-flex"
                                alignItems="center"
                                gap={1}
                                px={2}
                                py={1}
                            >
                                <TbStack2 size={9} />
                                <Text fontSize="xs">{layer.name}</Text>
                            </Badge>
                        ))}
                    </Flex>
                </Box>

                <Box borderTopWidth="1px" borderColor="border.muted" pt={4}>
                    <Flex align="center" justify="space-between" gap={3}>
                        <PanelSectionHeading>
                            {t("adminLayerGroups.layerGroups")}
                        </PanelSectionHeading>
                        <Box w="40" mb={2}>
                            <OcSelect
                                size="sm"
                                items={layerRoleOptions(t)}
                                value={role}
                                onValueChange={(value) => setRole(value as LayerRole)}
                                placeholder={t("adminDialogs.rolePlaceholder")}
                                // OcSelect exposes no top-level aria-label; the trigger is the
                                // focusable control, so the name has to go through triggerProps.
                                triggerProps={{ "aria-label": t("adminUsers.role") }}
                            />
                        </Box>
                    </Flex>
                    {excludedByScope ? (
                        <Text fontSize="sm" color="fg.muted" py={6} textAlign="center">
                            {t("adminLayers.noEligibleLayerGroups")}
                        </Text>
                    ) : (
                        <Box h="80">
                            <ResourceTable
                                aria-label={t("adminLayerGroups.layerGroups")}
                                data={eligibleGroups}
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
                    )}
                </Box>
            </Flex>
        </FormDialog>
    );
}
