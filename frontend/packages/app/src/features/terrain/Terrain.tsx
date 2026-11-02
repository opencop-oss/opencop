// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, VStack } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { AccentHeading } from "@opencop/ui";
import { TerrainPanel } from "./TerrainPanel";

export function Terrain() {
    const { t } = useTranslation();
    return (
        <VStack w="full" h="full" align="left">
            <Flex
                direction="row"
                justifyContent="space-between"
                alignItems="center"
                mb="4"
                flexWrap="nowrap"
            >
                <AccentHeading>{t("mapbar.terrain")}</AccentHeading>
            </Flex>
            <VStack w="full" h="full" gap={4} overflowY="auto">
                <TerrainPanel />
            </VStack>
        </VStack>
    );
}
