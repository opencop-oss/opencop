// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { IconButton } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { TbLayoutSidebarFilled } from "react-icons/tb";

export function SideBarButton() {
    const { t } = useTranslation();

    return (
        <IconButton
            aria-label={t("navigation.toggleSidebar")}
            w="full"
            maxW="8.5rem" // half of sidebar width
            minW="10"
            variant="ghost"
        >
            <TbLayoutSidebarFilled />
        </IconButton>
    );
}
