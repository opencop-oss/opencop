// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { VStack, Text, Icon } from "@chakra-ui/react";
import { FaHistory } from "react-icons/fa";
import { useTranslation } from "react-i18next";

export function EmptyHistoryState() {
    const { t } = useTranslation();
    return (
        <VStack align="center" justify="center" gap="8px" py={8} flex={1}>
            <Icon as={FaHistory} boxSize={8} opacity={0.3} />
            <Text fontSize="sm" opacity={0.6} textAlign="center">
                {t("timeline.noHistory")}
            </Text>
            <Text fontSize="xs" opacity={0.4} textAlign="center">
                {t("timeline.noHistoryDescription")}
            </Text>
        </VStack>
    );
}
