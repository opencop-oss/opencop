// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Flex, Text } from "@chakra-ui/react";
import type { ReactElement } from "react";
import { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { FaExclamationTriangle } from "react-icons/fa";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { LayerRoles, getErrorMessage } from "@opencop/api";
import type { LayerRole, LayerUser, User } from "@opencop/api";
import {
    BaseDialog,
    DialogActions,
    DialogHeader,
    OcSelect,
    clickableRoleColumn,
    roleColumn,
    usernameColumn,
} from "@opencop/ui";
import type { ChangeRoleAlerts } from "~/features/drawers/change-role.model";
import { DrawerRelationSection } from "~/features/drawers/DrawerRelationSection";
import { baseRelationLabels } from "~/features/drawers/relation-section.util";
import { useRelationSection } from "~/features/drawers/use-relation-section.hook";
import { RoleInfoTooltip } from "~/features/layer-groups/RoleInfoTooltip";
import { LAYER_ROLE_DESCRIPTIONS } from "~/features/layer-groups/layer-groups.util";
import {
    isOwnerEditBlocked,
    layerRoleOptions,
    wouldStripLastOwner,
} from "~/features/layers/layers.util";
import { useToast } from "~/features/notifications/use-toast.hook";
import { useUserPickerPool } from "~/features/users/use-user-picker-pool.hook";
import { layerMutations, layerQueries } from "../layers.queries";

/** Users assigned to a layer, with inline assign/revoke. Mounted by the admin layer drawer's side-nav. */
export function LayerMembersSection({
    layerId,
    readOnly = false,
    canAssignOwner = false,
    onLockedChange,
}: {
    layerId: string;
    readOnly?: boolean;
    /** Whether the viewer may set/change the OWNER role (global admins and layer owners). */
    canAssignOwner?: boolean;
    /** Notified when the bulk change-role panel opens/closes, so the drawer can lock its chrome. */
    onLockedChange?: (locked: boolean) => void;
}): ReactElement {
    const { t } = useTranslation();
    const { showToast } = useToast();
    const queryClient = useQueryClient();
    const userPool = useUserPickerPool();
    // Same cached query useRelationSection runs — read here to reason about owners.
    const members = useQuery(layerQueries.members(layerId));
    const assignUser = useMutation(layerMutations.assignUser(queryClient));
    const removeUser = useMutation(layerMutations.removeUser(queryClient));
    const { mutateAsync: updateMembership, isPending: isSavingRole } = useMutation(
        layerMutations.updateMembership(queryClient),
    );

    // The layer must always keep at least one owner.
    const owners = useMemo(
        () => (members.data ?? []).filter((m) => m.role === LayerRoles.OWNER),
        [members.data],
    );
    // When exactly one owner exists, they can't be demoted — used to lock their
    // inline dropdown and to disable the bulk button when only they are selected.
    const soleOwner = owners.length === 1 ? owners[0] : undefined;

    // Owners can only be set/changed by owners (or global admins); hide the option otherwise.
    const roleOptions = useMemo(
        () =>
            canAssignOwner
                ? layerRoleOptions(t)
                : layerRoleOptions(t).filter((o) => o.value !== LayerRoles.OWNER),
        [t, canAssignOwner],
    );
    // Full role → label map for display (includes OWNER even when it isn't assignable).
    const roleLabels = useMemo(
        () => new Map(layerRoleOptions(t).map((o) => [o.value, o.label])),
        [t],
    );

    const changeRole = useCallback(
        (userIds: string[], role: LayerRole) =>
            updateMembership({
                layerId,
                members: Object.fromEntries(userIds.map((id) => [id, role])),
            }),
        [updateMembership, layerId],
    );

    // Single-member role change: clicking a role opens this simple dialog.
    const [roleEdit, setRoleEdit] = useState<{
        userId: string;
        username: string;
        current: LayerRole;
    } | null>(null);
    const [pendingRole, setPendingRole] = useState("");

    const openRoleEdit = useCallback(
        (userId: string) => {
            const m = (members.data ?? []).find((x) => x.userId === userId);
            if (!m) return;
            setRoleEdit({ userId, username: m.username, current: m.role });
            setPendingRole(m.role);
        },
        [members.data],
    );

    async function applyRoleEdit() {
        if (!roleEdit || pendingRole === "" || pendingRole === roleEdit.current) return;
        try {
            await changeRole([roleEdit.userId], pendingRole as LayerRole);
            setRoleEdit(null);
        } catch (e) {
            // Keep the generic title, but append the server's reason for context.
            showToast({
                type: "error",
                title: t("adminDialogs.changeRoleFailed"),
                description: getErrorMessage(e),
            });
        }
    }

    // Rows shown in the bulk change-role dialog for the selected members.
    const getChangeRoleRows = useCallback(
        (ids: string[]) => {
            const byId = new Map((members.data ?? []).map((m) => [m.userId, m]));
            return ids
                .map((id) => byId.get(id))
                .filter((m): m is LayerUser => m != null)
                .map((m) => ({
                    id: m.userId,
                    label: m.username,
                    role: roleLabels.get(m.role) ?? m.role,
                }));
        },
        [members.data, roleLabels],
    );

    // Owner-aware validation: a downgrade caution whenever an owner is being
    // demoted, plus — if the change would remove the last owner — the affected owner
    // rows are flagged (warning icon + tooltip) and Apply is disabled until one is kept.
    const validateChangeRole = useCallback(
        (checkedIds: string[], role: LayerRole): ChangeRoleAlerts => {
            if (role === LayerRoles.OWNER) return {};
            const demotedOwners = owners.filter((o) => checkedIds.includes(o.userId));
            if (demotedOwners.length === 0) return {};
            // Only an owner (or global admin) may change an owner's role — the server
            // rejects an admin's owner change with 403, so flag those owner rows and
            // block Apply until they're kept.
            if (!canAssignOwner) {
                return {
                    warning: {
                        message: t("layers.ownerChangeRequiresOwner"),
                    },
                    blocked: {
                        rowIds: demotedOwners.map((o) => o.userId),
                        tooltip: t("layers.ownerChangeRequiresOwner"),
                    },
                };
            }
            const warning = {
                message: t("layers.ownerDowngradeWarning", {
                    count: demotedOwners.length,
                    role: roleLabels.get(role) ?? role,
                }),
            };
            return wouldStripLastOwner(checkedIds, owners)
                ? {
                      warning,
                      blocked: {
                          rowIds: demotedOwners.map((o) => o.userId),
                          tooltip: t("layers.ownerRequiredWarning"),
                      },
                  }
                : { warning };
        },
        [owners, roleLabels, t, canAssignOwner],
    );

    const assignedColumns = useMemo(
        () => [
            usernameColumn<LayerUser>(t("adminUsers.user")),
            readOnly
                ? roleColumn<LayerUser>(t("adminUsers.role"), layerRoleOptions(t))
                : clickableRoleColumn<LayerUser, LayerRole>({
                      header: t("adminUsers.role"),
                      options: roleOptions,
                      // roleOptions drops OWNER for non-owners, which is right for the role
                      // picker but not for the filter — see RoleColumnOptions.
                      filterOptions: layerRoleOptions(t),
                      getRowId: (row) => row.userId,
                      getEditAriaLabel: (row) =>
                          t("adminDialogs.changeRoleFor", { name: row.username }),
                      onClick: openRoleEdit,
                      headerInfo: <RoleInfoTooltip roles={LAYER_ROLE_DESCRIPTIONS} />,
                      // Non-owners can't modify an existing owner's role, and the sole
                      // owner can never be demoted (the layer must keep an owner).
                      isRowLocked: (row) =>
                          isOwnerEditBlocked(
                              row.role,
                              canAssignOwner,
                              soleOwner?.userId === row.userId,
                          ),
                  }),
        ],
        [t, readOnly, roleOptions, openRoleEdit, canAssignOwner, soleOwner],
    );
    const unassignedColumns = useMemo(() => [usernameColumn<User>(t("adminUsers.user"))], [t]);

    const props = useRelationSection<LayerUser, User, LayerRole>({
        assignedQuery: layerQueries.members(layerId),
        ...userPool,
        getAssignedRowId: (row) => row.userId,
        getUnassignedRowId: (row) => row.id,
        assignedColumns,
        unassignedColumns,
        roleOptions,
        roleInfo: <RoleInfoTooltip roles={LAYER_ROLE_DESCRIPTIONS} />,
        labels: baseRelationLabels(t, {
            entityLabel: t("drawerSections.entity.layer"),
            relationLabel: t("drawerSections.relation.members"),
            poolLabel: t("drawerSections.pool.directoryUsers"),
            description: t("drawerSections.description.layerMembers"),
            listAriaLabel: t("layers.manageMembers"),
            assignAriaLabel: t("adminDialogs.addNewUsers"),
            rolePlaceholder: t("adminDialogs.rolePlaceholder"),
            revokeError: t("adminDialogs.revokeFailed"),
            revokeDisabledReason: t("layers.ownerRequiredWarning"),
            assignError: t("adminDialogs.assignFailed"),
        }),
        onAssign: (ids, role) =>
            role
                ? Promise.all(
                      ids.map((userId) => assignUser.mutateAsync({ layerId, userId, role })),
                  )
                : Promise.resolve(),
        onRevoke: (userId) => removeUser.mutateAsync({ layerId, userId }),
        onChangeRole: changeRole,
        getChangeRoleRows,
        validateChangeRole,
        // A single selection uses the simple single-member dialog.
        onChangeRoleSingle: openRoleEdit,
        // Disable when *every* selected row is edit-blocked (the selection-level
        // counterpart of isOwnerEditBlocked): a non-owner admin can't touch any owner
        // (a selection of only owners is a no-op), and the sole owner can't be demoted.
        // Distinct from the last-owner guard (wouldStripLastOwner) — a mixed selection
        // stays enabled and validateChangeRole blocks Apply if it would strip the owner.
        canChangeRoleFor: (ids) => {
            if (ids.length === 0) return true;
            if (!canAssignOwner && ids.every((id) => owners.some((o) => o.userId === id)))
                return false;
            return !(soleOwner != null && ids.every((id) => id === soleOwner.userId));
        },
        // The layer must keep an owner — block revoking every owner at once
        // (the server rejects it with LAYER_REQUIRES_OWNER).
        canRevokeFor: (ids) => !wouldStripLastOwner(ids, owners),
    });

    const assignedByName = useMemo(
        () => [...props.assigned].sort((a, b) => a.username.localeCompare(b.username)),
        [props.assigned],
    );

    // Run the same owner-aware validation for the inline single-member edit so
    // demoting one of several owners shows the same caution as the bulk path.
    const roleEditAlerts: ChangeRoleAlerts =
        roleEdit && pendingRole !== "" && pendingRole !== roleEdit.current
            ? validateChangeRole([roleEdit.userId], pendingRole as LayerRole)
            : {};
    // Defense-in-depth: the inline dialog only opens for rows the gates above leave
    // editable, so validateChangeRole never returns `blocked` here today. Kept as the
    // last guard on the keep-an-owner invariant if one of those gates is ever loosened.
    const roleEditBlocked =
        roleEdit != null && (roleEditAlerts.blocked?.rowIds.includes(roleEdit.userId) ?? false);

    return (
        <>
            <DrawerRelationSection
                {...props}
                assigned={assignedByName}
                readOnly={readOnly}
                onLockedChange={onLockedChange}
            />
            {roleEdit && (
                <BaseDialog open onClose={() => setRoleEdit(null)} size="sm">
                    <DialogHeader
                        title={t("adminDialogs.changeRoleTitle", {
                            entity: t("drawerSections.entity.layer"),
                        })}
                        secondaryText={roleEdit.username}
                    />
                    <Box px={6} py={2}>
                        <OcSelect
                            size="sm"
                            items={roleOptions}
                            value={pendingRole}
                            onValueChange={setPendingRole}
                            placeholder={t("adminDialogs.rolePlaceholder")}
                            triggerProps={{
                                "aria-label": t("adminDialogs.changeRoleFor", {
                                    name: roleEdit.username,
                                }),
                            }}
                        />
                        {roleEditAlerts.warning && (
                            <Flex mt={2} align="center" gap={1.5} color="fg.warning">
                                <Box flexShrink={0} display="inline-flex" fontSize="xs">
                                    <FaExclamationTriangle />
                                </Box>
                                <Text fontSize="xs" fontStyle="italic" lineHeight="short">
                                    {roleEditAlerts.warning.message}
                                </Text>
                            </Flex>
                        )}
                    </Box>
                    <DialogActions
                        actionType="button"
                        actionLabel={t("common.apply")}
                        cancelLabel={t("common.cancel")}
                        disabled={
                            pendingRole === "" ||
                            pendingRole === roleEdit.current ||
                            roleEditBlocked
                        }
                        loading={isSavingRole}
                        onAction={applyRoleEdit}
                        onClose={() => setRoleEdit(null)}
                    />
                </BaseDialog>
            )}
        </>
    );
}
