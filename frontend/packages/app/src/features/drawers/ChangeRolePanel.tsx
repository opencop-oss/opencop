// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Button, Flex, IconButton, Stack, Text } from "@chakra-ui/react";
import type { ReactElement, ReactNode } from "react";
import { useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { LuX } from "react-icons/lu";
import { partition } from "lodash";
import { getErrorMessage } from "@opencop/api";
import {
    DrawerSectionHeader,
    OcSelect,
    ResizeHandle,
    Tooltip,
    computeFlexSizes,
} from "@opencop/ui";
import { useToast } from "~/features/notifications/use-toast.hook";
import { ChangeRoleUserList } from "./ChangeRoleUserList";
import type { ChangeRoleAlerts, ChangeRoleRow } from "./change-role.model";
import type {
    DrawerRelationRoleOptions,
    DrawerRelationSectionLabels,
} from "./drawer-relation.model";

/** Hit-target height of the ResizeHandle (its "sm" size) — subtracted when splitting. */
const CHANGE_ROLE_HANDLE_H = 8;

/**
 * Bulk change-role sub-panel: pick a new role, then shuttle the selected members
 * between "Selected users" (will change) and "Excluded" (kept). Owns its own state,
 * seeded from `initialRows` when it mounts; Apply/Cancel notify the parent.
 */
export function ChangeRolePanel<R extends string = string>({
    initialRows,
    roleOptions,
    roleInfo,
    validateChangeRole,
    onChangeRole,
    labels,
    onCancel,
    onApplied,
}: {
    /** The selected members, snapshotted by the parent when the panel opens. */
    initialRows: ChangeRoleRow[];
    roleOptions?: DrawerRelationRoleOptions<R>;
    roleInfo?: ReactNode;
    validateChangeRole?: (checkedIds: string[], role: R) => ChangeRoleAlerts;
    onChangeRole: (ids: string[], role: R) => Promise<unknown>;
    labels: DrawerRelationSectionLabels;
    /** Return to list mode without applying. */
    onCancel: () => void;
    /** Called after a successful change (parent clears the selection + returns to list). */
    onApplied: () => void;
}): ReactElement {
    const { t } = useTranslation();
    const { showToast } = useToast();

    const [pendingRole, setPendingRole] = useState("");
    const [isChangingRole, setIsChangingRole] = useState(false);
    const [dialogRows] = useState(initialRows);
    // Which rows will receive the change — all checked initially; moving a row to
    // the "Excluded" list unchecks it and drops it from the change.
    const [checkedIds, setCheckedIds] = useState<string[]>(() => initialRows.map((r) => r.id));

    // Draggable split between the "to update" and "excluded" lists (mirrors the Filters panel).
    const listContainerRef = useRef<HTMLDivElement | null>(null);
    const [listSizes, setListSizes] = useState<Record<string, number>>({ update: 1, excluded: 1 });
    function resizeLists(delta: number) {
        const el = listContainerRef.current;
        if (!el) return;
        const totalH = el.clientHeight - CHANGE_ROLE_HANDLE_H;
        setListSizes((prev) => computeFlexSizes(prev, delta, "update", "excluded", totalH) ?? prev);
    }

    // Unchecking rows can withdraw an already-picked role, so the role is derived rather than
    // synced by an effect: no render or submit sees a role the caller may no longer apply.
    const resolvedRoleOptions = useMemo(
        () => (typeof roleOptions === "function" ? roleOptions(checkedIds) : roleOptions),
        [roleOptions, checkedIds],
    );
    const effectiveRole = resolvedRoleOptions?.some((o) => o.value === pendingRole)
        ? pendingRole
        : "";

    const roleAlerts: ChangeRoleAlerts =
        effectiveRole !== "" ? (validateChangeRole?.(checkedIds, effectiveRole as R) ?? {}) : {};
    const canApplyRole =
        effectiveRole !== "" &&
        checkedIds.length > 0 &&
        roleAlerts.blocked == null &&
        !isChangingRole;
    // Explains a disabled Apply (shown as a tooltip). Undefined while valid or submitting.
    const applyDisabledReason =
        effectiveRole === ""
            ? t("adminDialogs.selectRoleFirst")
            : checkedIds.length === 0
              ? t("adminDialogs.selectUsersToChange")
              : roleAlerts.blocked?.tooltip;

    function toggleChecked(id: string, checked: boolean) {
        setCheckedIds((prev) => (checked ? [...prev, id] : prev.filter((x) => x !== id)));
    }

    async function handleChangeRole() {
        if (!canApplyRole) return;
        setIsChangingRole(true);
        try {
            await onChangeRole(checkedIds, effectiveRole as R);
            onApplied();
        } catch (e) {
            // Keep the generic title, but append the server's reason for context.
            showToast({
                type: "error",
                title: labels.changeRoleError,
                description: getErrorMessage(e),
            });
        } finally {
            setIsChangingRole(false);
        }
    }

    // Hoisted out of the row map so membership checks are O(1), not O(n) per row.
    const checkedSet = new Set(checkedIds);
    const blockedRowIds = new Set(roleAlerts.blocked?.rowIds ?? []);
    // Split the selected members into the two shuttle lists.
    const [toUpdate, excluded] = partition(dialogRows, (r) => checkedSet.has(r.id));

    return (
        <Stack gap={3} minH={0} h="full">
            <DrawerSectionHeader
                title={labels.changeRoleTitle}
                description={labels.changeRoleDescription}
                actions={
                    <IconButton
                        aria-label={t("common.close")}
                        variant="ghost"
                        size="xs"
                        onClick={onCancel}
                    >
                        <LuX />
                    </IconButton>
                }
            />
            <Stack gap={1} flexShrink={0}>
                <Flex align="center" gap={1.5}>
                    <Text fontSize="sm" fontWeight="medium">
                        {t("adminDialogs.selectNewRole")}
                    </Text>
                    {roleInfo}
                </Flex>
                <OcSelect
                    size="sm"
                    items={resolvedRoleOptions ?? []}
                    value={effectiveRole}
                    onValueChange={setPendingRole}
                    placeholder={labels.rolePlaceholder ?? t("adminDialogs.rolePlaceholder")}
                />
            </Stack>

            <Stack ref={listContainerRef} gap={1} mt={2} flex="1 1 0" minH="0px" overflow="hidden">
                <ChangeRoleUserList
                    title={t("adminDialogs.usersToUpdate", { n: toUpdate.length })}
                    warning={roleAlerts.warning?.message}
                    rows={toUpdate}
                    variant="update"
                    blockedRowIds={blockedRowIds}
                    blockedTooltip={roleAlerts.blocked?.tooltip}
                    emptyLabel={t("adminDialogs.noUsersToUpdate")}
                    flexSize={listSizes.update}
                    onMove={toggleChecked}
                />
                <ResizeHandle
                    onResize={resizeLists}
                    label={t("adminDialogs.resizeLists")}
                    valueMin={0}
                    valueMax={100}
                    valueNow={Math.round(
                        (listSizes.update / (listSizes.update + listSizes.excluded)) * 100,
                    )}
                />
                <ChangeRoleUserList
                    title={t("adminDialogs.usersExcluded", { n: excluded.length })}
                    rows={excluded}
                    variant="excluded"
                    blockedRowIds={blockedRowIds}
                    emptyLabel={t("adminDialogs.noExcludedUsers")}
                    flexSize={listSizes.excluded}
                    onMove={toggleChecked}
                />
            </Stack>

            <Flex justify="flex-end" gap={2} pt={1} flexShrink={0}>
                <Button size="xs" variant="subtle" colorPalette="secondary" onClick={onCancel}>
                    {t("common.cancel")}
                </Button>
                <Tooltip content={applyDisabledReason} disabled={applyDisabledReason == null}>
                    <Box as="span" display="inline-flex" tabIndex={applyDisabledReason ? 0 : -1}>
                        <Button
                            size="xs"
                            colorPalette="brand"
                            onClick={handleChangeRole}
                            disabled={!canApplyRole}
                            loading={isChangingRole}
                            loadingText={t("common.apply")}
                        >
                            {t("common.apply")}
                        </Button>
                    </Box>
                </Tooltip>
            </Flex>
        </Stack>
    );
}
