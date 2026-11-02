// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { VStack } from "@chakra-ui/react";
import { type ReactNode } from "react";
import { getDefaultTransition } from "@opencop/ui";
import { useTranslation } from "react-i18next";
import { useSidebar } from "./SidebarContext";
import { useActiveBanners } from "~/features/banners/useActiveBanners";
import { useBannerDismiss } from "~/features/banners/BannerDismissContext";
import { BANNER_BAR_HEIGHT } from "~/features/banners/banner.util";

export function SideBarWrapper({ children }: { children?: ReactNode }) {
    const { width } = useSidebar();
    const { t } = useTranslation();
    const activeBanners = useActiveBanners();
    const { dismissedIds } = useBannerDismiss();

    const visible = activeBanners.filter((b) => !dismissedIds.has(b.id));
    const topCount =
        visible.filter((b) => b.kind === "SYSTEM").length +
        (visible.some((b) => b.kind === "HEADER") ? 1 : 0);
    const hasFooter = visible.some((b) => b.kind === "FOOTER");

    return (
        <VStack
            as="nav"
            aria-label={t("navigation.mainNavigation")}
            pos="fixed"
            zIndex="10"
            w={width}
            top={topCount * BANNER_BAR_HEIGHT}
            bottom={hasFooter ? BANNER_BAR_HEIGHT : 0}
            justify="space-between"
            bg="bg.glass"
            backdropFilter="blur(4px)"
            boxShadow="0 4px 12px rgba(0, 0, 0, 0.15)"
            borderRightWidth="1px"
            borderColor="border"
            transition={
                getDefaultTransition(["width", "top", "bottom"], { duration: 0.2 }).transition
            }
        >
            {children}
        </VStack>
    );
}
