// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, IconButton, Menu, Portal } from "@chakra-ui/react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { FaEllipsis, FaList } from "react-icons/fa6";
import { TbPin, TbPinFilled } from "react-icons/tb";
import { TextVisibilityIcon } from "./TextVisibilityIcon";

/** Square size of a row's trailing icon buttons; keeps the menu trigger and eye toggle aligned. */
export const ROW_ICON_BUTTON_SIZE = 6;
/** Pixel size of the expand chevron on layer and group rows. */
export const ROW_CHEVRON_SIZE = 14;

interface SourceActionsMenuProps {
    sourceName: string;
    textVisible: boolean;
    objectListOpen: boolean;
    objectListDisabled: boolean;
    pinned: boolean;
    onToggleText: () => void;
    onToggleObjectList: () => void;
    /** Omitted when pinning would have no visible effect, which hides the Pin item. */
    onTogglePin?: () => void;
}

function MenuRow({ label, icon }: { label: string; icon: ReactNode }) {
    return (
        <Flex w="full" align="center" justify="space-between" gap={4}>
            {label}
            {icon}
        </Flex>
    );
}

/** Tri-dot actions menu for a layer / data source row. */
export function SourceActionsMenu({
    sourceName,
    textVisible,
    objectListOpen,
    objectListDisabled,
    pinned,
    onToggleText,
    onToggleObjectList,
    onTogglePin,
}: SourceActionsMenuProps) {
    const { t } = useTranslation();
    return (
        <Menu.Root>
            <Menu.Trigger asChild>
                <IconButton
                    size="xs"
                    boxSize={ROW_ICON_BUTTON_SIZE}
                    minW={ROW_ICON_BUTTON_SIZE}
                    variant="ghost"
                    color="fg.muted"
                    aria-label={t("filters.sourceActions", { name: sourceName })}
                >
                    <FaEllipsis size={16} />
                </IconButton>
            </Menu.Trigger>
            <Portal>
                <Menu.Positioner>
                    <Menu.Content>
                        <Menu.Item value="toggle-text" onClick={onToggleText}>
                            <MenuRow
                                label={textVisible ? t("filters.hideText") : t("filters.showText")}
                                icon={<TextVisibilityIcon hidden={!textVisible} />}
                            />
                        </Menu.Item>
                        <Menu.Item
                            value="toggle-object-list"
                            disabled={objectListDisabled}
                            onClick={onToggleObjectList}
                        >
                            <MenuRow
                                label={
                                    objectListOpen
                                        ? t("filters.hideObjectList")
                                        : t("filters.showObjectList")
                                }
                                icon={<FaList size={16} />}
                            />
                        </Menu.Item>
                        {onTogglePin && (
                            <Menu.Item value="toggle-pin" onClick={onTogglePin}>
                                <MenuRow
                                    label={pinned ? t("filters.unpin") : t("filters.pin")}
                                    icon={pinned ? <TbPinFilled size={16} /> : <TbPin size={16} />}
                                />
                            </Menu.Item>
                        )}
                    </Menu.Content>
                </Menu.Positioner>
            </Portal>
        </Menu.Root>
    );
}
