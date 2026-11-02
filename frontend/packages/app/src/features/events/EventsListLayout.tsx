// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex } from "@chakra-ui/react";
import { AccentHeading } from "@opencop/ui";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";

interface EventsListLayoutProps {
    controls?: ReactNode;
    children: ReactNode;
}

/** Shared list chrome for the default search list and lasso result list. */
export function EventsListLayout({ controls, children }: EventsListLayoutProps) {
    const { t } = useTranslation();

    return (
        <Flex direction="column" width="100%" height="100%" minH="0" gap="2">
            <AccentHeading>{t("selectionPanel.eventsList")}</AccentHeading>
            {controls}
            <Flex direction="column" flex="1" minH="0">
                {children}
            </Flex>
        </Flex>
    );
}
