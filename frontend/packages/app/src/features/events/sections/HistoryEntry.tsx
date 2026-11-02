// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, Text, Circle, Icon, VStack } from "@chakra-ui/react";
import { FaHistory, FaMapMarkerAlt, FaPaintBrush, FaPlus, FaEdit } from "react-icons/fa";
import type { IconType } from "react-icons";
import { useTranslation } from "react-i18next";
import { formatRelativeTime } from "~/date.util";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import { formatKey, type HistoryEntry } from "./history.util";
import { ChangeValue } from "./ChangeValue";

const ACTION_CONFIG: Record<string, { icon: IconType; bgColor: string }> = {
    created: { icon: FaPlus, bgColor: "event.created" },
    moved: { icon: FaMapMarkerAlt, bgColor: "event.moved" },
    styled: { icon: FaPaintBrush, bgColor: "event.styled" },
    edited: { icon: FaEdit, bgColor: "event.edited" },
    default: { icon: FaHistory, bgColor: "event" },
};

const getActionConfig = (action: string) => ACTION_CONFIG[action] || ACTION_CONFIG.default;

export function HistoryEntryCard({ entry, isLast }: { entry: HistoryEntry; isLast: boolean }) {
    const { t } = useTranslation();
    const timezone = useUserTimezone();
    const actionConfig = getActionConfig(entry.action);

    return (
        <Flex gap={3}>
            <VStack align="center" justify="center" width="24px" flexShrink={0}>
                <Circle
                    bg={actionConfig.bgColor}
                    flexShrink={0}
                    alignItems="center"
                    justifyContent="center"
                    w="15px"
                    h="15px"
                >
                    <Icon as={actionConfig.icon} color="white" boxSize="10px" />
                </Circle>
                {!isLast && <Flex width="2px" flex={1} bg="whiteAlpha.200" minHeight="20px" />}
            </VStack>

            <VStack gap="4px" pb={isLast ? 0 : 4} flex={1} align="stretch">
                <Flex justify="space-between" align="flex-start">
                    <Text fontSize="sm" fontWeight={600}>
                        {entry.description}
                    </Text>
                    <Text fontSize="xs" opacity={0.5} flexShrink={0} ml={2}>
                        {formatRelativeTime(new Date(entry.timestamp), timezone)}
                    </Text>
                </Flex>
                <Text fontSize="xs" opacity={0.6}>
                    {t("timeline.by", { author: entry.author })}
                </Text>

                {entry.changes && Object.keys(entry.changes).length > 0 && (
                    <VStack
                        gap="8px"
                        mt={2}
                        p={3}
                        bg="glass.subtle"
                        borderRadius="md"
                        align="stretch"
                    >
                        {Object.entries(entry.changes).map(([key, change]) => (
                            <VStack key={key} gap="4px" align="stretch">
                                <Text fontSize="xs" opacity={0.7} fontWeight={600}>
                                    {formatKey(key)}
                                </Text>
                                <Flex align="center" gap={2} flexWrap="wrap">
                                    <ChangeValue value={change.old} />
                                    <Text opacity={0.4}>→</Text>
                                    <ChangeValue value={change.new} />
                                </Flex>
                            </VStack>
                        ))}
                    </VStack>
                )}
            </VStack>
        </Flex>
    );
}
