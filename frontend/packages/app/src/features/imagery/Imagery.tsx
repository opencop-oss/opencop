// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, VStack } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { AccentHeading } from "@opencop/ui";
import { TileList } from "./TileList";
export function Imagery() {
    const { t } = useTranslation();
    return (
        <VStack w="full" h="full" align="left">
            <Flex
                direction="row"
                justifyContent="space-between"
                alignItems="center"
                mb="1"
                flexWrap="nowrap"
            >
                <AccentHeading>{t("imagery.overlays.title")}</AccentHeading>
            </Flex>
            <Flex flex="1 1 0" minH="0" overflow="hidden" w="full">
                <TileList />
            </Flex>
        </VStack>
    );
}
