// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, SkipNavContent, useToken, type FlexProps } from "@chakra-ui/react";
import { getDefaultTransition } from "@opencop/ui";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useSidebar } from "~/features/sidebar/SidebarContext";

const childPointerEventsCss: FlexProps = {
    css: {
        "& > *": {
            pointerEvents: "auto",
        },
    },
};

/** Sidebar-aware map page layout with skip-nav and overlay positioning. */
export function MapLayout({ children }: { children?: ReactNode }) {
    const { t } = useTranslation();

    return (
        <SkipNavContent asChild>
            <main
                aria-label={t("navigation.interactiveMap")}
                style={{ position: "relative", flex: 1, overflow: "hidden" }}
            >
                {children}
            </main>
        </SkipNavContent>
    );
}

/** Sidebar-aware absolute overlay container for map UI controls. */
export function SidebarAwarePositioner({ children }: { children?: ReactNode }) {
    const { width } = useSidebar();
    const [w] = useToken("sizes", width.toString());

    return (
        <Flex
            flexDir="column"
            pos="absolute"
            left={width}
            w={`calc(100% - ${w})`}
            transition={getDefaultTransition(["left", "width"], { duration: 0.2 }).transition}
            h="full"
            pointerEvents="none"
            zIndex={3}
            {...childPointerEventsCss}
        >
            {children}
        </Flex>
    );
}
