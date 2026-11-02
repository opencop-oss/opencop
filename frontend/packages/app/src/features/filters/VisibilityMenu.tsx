// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { IconButton, Menu, Portal } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { FaEllipsis } from "react-icons/fa6";

export type VisiblityMenuProps = {
    allVisible: boolean;
    anyVisible: boolean;
    anySyncing: boolean;
    anyTextVisible: boolean;
    anyTextHidden: boolean;
    showAll: () => void;
    hideAll: () => void;
    showAllText: () => void;
    hideAllText: () => void;
};

export function VisibilityMenu(props: VisiblityMenuProps) {
    const { t } = useTranslation();
    return (
        <Menu.Root>
            <Menu.Trigger asChild>
                <IconButton color="slate.text.muted" aria-label={t("filters.visibilityOptions")}>
                    <FaEllipsis />
                </IconButton>
            </Menu.Trigger>
            <Portal>
                <Menu.Positioner>
                    <Menu.Content>
                        <Menu.Item
                            value="show-all"
                            disabled={props.anySyncing || props.allVisible}
                            onClick={props.showAll}
                        >
                            {t("mapSettings.showAll")}
                        </Menu.Item>
                        <Menu.Item
                            value="hide-all"
                            disabled={props.anySyncing || !props.anyVisible}
                            onClick={props.hideAll}
                        >
                            {t("mapSettings.hideAll")}
                        </Menu.Item>
                        <Menu.Item
                            value="show-all-text"
                            disabled={!props.anyTextHidden}
                            onClick={props.showAllText}
                        >
                            {t("filters.showAllText")}
                        </Menu.Item>
                        <Menu.Item
                            value="hide-all-text"
                            disabled={!props.anyTextVisible}
                            onClick={props.hideAllText}
                        >
                            {t("filters.hideAllText")}
                        </Menu.Item>
                    </Menu.Content>
                </Menu.Positioner>
            </Portal>
        </Menu.Root>
    );
}
