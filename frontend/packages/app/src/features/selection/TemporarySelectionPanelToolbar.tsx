// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Checkbox, Flex, IconButton, Separator, Text } from "@chakra-ui/react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { MdClose, MdContentCopy, MdDelete, MdFileDownload } from "react-icons/md";
import { Tooltip } from "@opencop/ui";
import { selectionCheckboxControlProps } from "./selection-checkbox-control.const";

interface TemporarySelectionPanelToolbarProps {
    selectedCount: number;
    totalCount: number;
    allSelected: boolean;
    isExporting: boolean;
    deleteDisabled: boolean;
    deleteDisabledReason?: string;
    isDeleting: boolean;
    copyDisabled: boolean;
    copyDisabledReason?: string;
    isCopying: boolean;
    onToggleSelectAll: () => void;
    onExport: () => void;
    onDeleteSelected: () => void;
    onCopySelected: () => void;
    onCancel: () => void;
}

const iconButtonProps = {
    size: "xs" as const,
    variant: "ghost" as const,
    colorPalette: "gray" as const,
    color: "fg.muted",
    boxSize: "7",
    minW: "7",
    borderRadius: "sm",
    _hover: {
        bg: "transparent",
        color: "active",
    },
    _active: {
        bg: "transparent",
    },
    _focusVisible: {
        outline: "2px solid",
        outlineColor: "active",
        outlineOffset: "2px",
        borderRadius: "sm",
    },
    _disabled: {
        opacity: 0.4,
        cursor: "not-allowed",
        _hover: {
            bg: "transparent",
            color: "fg.muted",
        },
    },
};

const destructiveButtonProps = {
    colorPalette: "red" as const,
    color: "fg.error",
    _hover: { bg: "transparent", color: "fg.error" },
};

interface ToolbarIconButtonProps {
    /** Accessible name and tooltip text. */
    label: string;
    icon: ReactNode;
    onClick: () => void;
    disabled?: boolean;
    /** Why the action is unavailable; shown in place of `label` while it is. */
    disabledReason?: string;
    loading?: boolean;
    destructive?: boolean;
}

/**
 * An icon-only toolbar action. Its tooltip is the label, or the reason it is unavailable.
 *
 * The tree is always Tooltip > span > button, and only props change as the reason appears or
 * clears: a different element type in that slot would remount the button, and a keyboard user
 * focused on the reason's span (e.g. "Checking copy permissions…") would lose focus to <body>
 * when permissions resolve. The span is the focusable tooltip trigger while a reason shows, and
 * its label is the reason so a keyboard/screen-reader user tabbing here hears it immediately —
 * Ark only sets aria-describedby once the trigger is focused, and a disabled button's own
 * aria-label isn't exposed on this ancestor. See ChangeRoleUserList.tsx and
 * drawers/DrawerToolbarButton.tsx, which this mirrors.
 *
 * It stays local rather than reusing DrawerToolbarButton because the visual treatment differs:
 * this toolbar is ghost/xs (28px), colours destructive actions red at rest, and keeps tooltip
 * labels in sentence case; DrawerToolbarButton is plain/sm, reveals colour on hover only, and
 * uppercases label tooltips. Merging them needs a variant decision that touches every drawer
 * toolbar, so the two should be consolidated in a follow-up (which should also port the span
 * aria-label above to DrawerToolbarButton, which lacks it).
 */
function ToolbarIconButton({
    label,
    icon,
    onClick,
    disabled,
    disabledReason,
    loading,
    destructive,
}: ToolbarIconButtonProps) {
    const showingReason = Boolean(disabled && disabledReason);
    return (
        <Tooltip content={showingReason ? disabledReason : label} uppercase={false}>
            <Box
                as="span"
                tabIndex={showingReason ? 0 : -1}
                display="inline-flex"
                aria-label={showingReason ? disabledReason : undefined}
            >
                <IconButton
                    {...iconButtonProps}
                    {...(destructive && destructiveButtonProps)}
                    aria-label={label}
                    disabled={disabled || loading}
                    loading={loading}
                    onClick={onClick}
                >
                    {icon}
                </IconButton>
            </Box>
        </Tooltip>
    );
}

/**
 * Feature-local toolbar for the selection panel, styled after the admin toolbars. It is a local
 * copy rather than built on the shared toolbar primitives; see `ToolbarIconButton` for how its
 * buttons differ from `DrawerToolbarButton`.
 *
 * Selection state sits on the left: a select-all checkbox (checked when every item on the tab
 * is, dashed when only some are) and the count. Actions are icon-only on the right, with the
 * destructive one set apart, so the row stays narrow whatever the language.
 */
export function TemporarySelectionPanelToolbar({
    selectedCount,
    totalCount,
    allSelected,
    isExporting,
    deleteDisabled,
    deleteDisabledReason,
    isDeleting,
    copyDisabled,
    copyDisabledReason,
    isCopying,
    onToggleSelectAll,
    onExport,
    onDeleteSelected,
    onCopySelected,
    onCancel,
}: TemporarySelectionPanelToolbarProps) {
    const { t } = useTranslation();

    return (
        <Box w="full" flexShrink={0}>
            <Flex
                role="toolbar"
                aria-label={t("selectionPanel.eventsList")}
                align="center"
                justify="space-between"
                wrap="wrap"
                gap={2}
                py={2}
                borderBottomWidth="1px"
                borderBottomColor="border.muted"
            >
                <Flex align="center" gap={2}>
                    <Checkbox.Root
                        size="sm"
                        checked={allSelected ? true : selectedCount > 0 ? "indeterminate" : false}
                        disabled={totalCount === 0}
                        aria-label={t("selectionPanel.checkAll")}
                        onCheckedChange={onToggleSelectAll}
                    >
                        <Checkbox.HiddenInput />
                        <Checkbox.Control {...selectionCheckboxControlProps}>
                            <Checkbox.Indicator />
                        </Checkbox.Control>
                    </Checkbox.Root>
                    <Text fontSize="xs" fontWeight="500" color="fg.muted" aria-live="polite">
                        {t("selectionPanel.title", { count: selectedCount })}
                    </Text>
                </Flex>

                <Flex align="center" gap={1} wrap="nowrap" ml="auto">
                    <ToolbarIconButton
                        label={t("selectionPanel.exportSelected")}
                        icon={<MdFileDownload size={16} />}
                        disabled={selectedCount === 0}
                        loading={isExporting}
                        onClick={onExport}
                    />
                    <ToolbarIconButton
                        label={t("selectionPanel.copyToLayer")}
                        icon={<MdContentCopy size={16} />}
                        disabled={copyDisabled}
                        disabledReason={copyDisabledReason}
                        loading={isCopying}
                        onClick={onCopySelected}
                    />

                    <Separator orientation="vertical" h="5" mx={0.25} flexShrink={0} />

                    <ToolbarIconButton
                        label={t("selectionPanel.deleteSelected")}
                        icon={<MdDelete size={16} />}
                        disabled={deleteDisabled}
                        disabledReason={deleteDisabledReason}
                        loading={isDeleting}
                        onClick={onDeleteSelected}
                        destructive
                    />

                    <Separator orientation="vertical" h="5" mx={0.25} flexShrink={0} />

                    <ToolbarIconButton
                        label={t("common.cancel")}
                        icon={<MdClose size={16} />}
                        onClick={onCancel}
                    />
                </Flex>
            </Flex>
        </Box>
    );
}
