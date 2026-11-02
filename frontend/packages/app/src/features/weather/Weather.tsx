// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, VStack } from "@chakra-ui/react";
import { AccentHeading } from "@opencop/ui";
import { useTranslation } from "react-i18next";
import { WeatherPanel } from "./WeatherPanel";

export function Weather() {
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
                <AccentHeading>{t("weather.title")}</AccentHeading>
            </Flex>
            <VStack w="full" h="full" gap={4} overflowY="auto">
                <WeatherPanel />
            </VStack>
        </VStack>
    );
}
