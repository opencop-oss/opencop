// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Flex, SkipNavLink } from "@chakra-ui/react";
import React from "react";
import { useTranslation } from "react-i18next";
import { Outlet } from "react-router";
import { SidebarProvider } from "~/features/sidebar/SidebarContext";
import { SideBar as Sidebar } from "~/features/sidebar/Sidebar";
import { ActiveBannerDisplay } from "~/features/banners/ActiveBannerDisplay";
import { BannerDismissProvider } from "~/features/banners/BannerDismissContext";

export const BaseLayout: React.FC = () => {
    const { t } = useTranslation();

    return (
        <BannerDismissProvider>
            <Flex direction="column" height="100vh" width="100vw" overflow="hidden">
                {/* Top banners — above dialog overlays; zero height when empty */}
                <Box position="relative" zIndex="banner" flexShrink={0}>
                    <ActiveBannerDisplay kinds={["SYSTEM", "HEADER"]} barRole="banner" />
                </Box>

                {/* Middle row: sidebar + content fill remaining height */}
                <Flex flex={1} direction="row" overflow="hidden" minH={0} position="relative">
                    <SkipNavLink
                        _focusVisible={{
                            zIndex: "skipNav",
                            bg: "brand.solid",
                            color: "brand.contrast",
                            shadow: "md",
                            top: "4",
                            insetStart: "50%",
                            transform: "translateX(-50%)",
                        }}
                    >
                        {t("navigation.skipToMain")}
                    </SkipNavLink>
                    <SidebarProvider>
                        <Sidebar />
                        {/*
                         * Drawer containment wrapper — `transform` makes this Box the containing
                         * block for any `position: fixed` descendants (Chakra Drawers), so they
                         * are anchored here instead of the viewport. `overflow: clip` then
                         * confines them to this area (between the two banner rows) without
                         * creating a scroll container that would break `position: sticky` inside.
                         */}
                        <Box
                            flex={1}
                            minW={0}
                            display="flex"
                            flexDirection="column"
                            position="relative"
                            css={{ overflow: "clip", transform: "translateZ(0)" }}
                        >
                            <Outlet />
                        </Box>
                    </SidebarProvider>
                </Flex>

                {/* Bottom banners — above dialog overlays; zero height when empty */}
                <Box position="relative" zIndex="banner" flexShrink={0}>
                    <ActiveBannerDisplay kinds={["FOOTER"]} barRole="contentinfo" />
                </Box>
            </Flex>
        </BannerDismissProvider>
    );
};
