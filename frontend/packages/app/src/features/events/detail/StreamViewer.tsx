// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { countBy } from "lodash";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { FaCamera, FaVideo } from "react-icons/fa";
import { Box, HStack, Text, VStack } from "@chakra-ui/react";
import type { UrlAttachment } from "@opencop/api";
import {
    isCameraFeed,
    streamKindName,
    streamUrlTitle,
    urlAttachmentTitle,
} from "./attachment.util";
import { UrlAttachmentPlayer } from "./UrlAttachmentPlayer";

interface StreamViewerProps {
    attachments: UrlAttachment[];
    sourceId: string;
    objectId: string;
}

/**
 * One stream in the main frame, with every stream listed underneath to switch between (the playing
 * one is highlighted).
 *
 * Only the selected stream is connected, so an event with many streams opens one connection, not
 * one per stream. The frame has a fixed 16:9 shape and letterboxes whatever it plays on black, so
 * switching streams never changes the layout around it.
 */
export function StreamViewer({ attachments, sourceId, objectId }: StreamViewerProps) {
    const { t } = useTranslation();
    const [selectedId, setSelectedId] = useState<string | null>(null);
    const selected = attachments.find((a) => a.record.id === selectedId) ?? attachments[0];
    if (!selected) return null;

    // Ports, paths and ids are hidden from titles, so two streams on one host read the same. Number
    // only the ones that collide, which exposes nothing from the URL.
    const titles = attachments.map((a) => urlAttachmentTitle(a.record));
    const titleCounts = countBy(titles);
    const collides = (index: number) => titleCounts[titles[index]] > 1;
    const labelAt = (index: number) =>
        collides(index) ? `${titles[index]} (${index + 1})` : titles[index];
    const numberAt = (index: number) => (collides(index) ? ` (${index + 1})` : "");

    return (
        <VStack gap={2} w="full" align="stretch">
            <Text fontSize="sm" fontWeight="medium" textAlign="center" truncate>
                {labelAt(attachments.indexOf(selected))}
            </Text>
            <Box
                position="relative"
                w="full"
                aspectRatio="16 / 9"
                bg="black"
                borderRadius="md"
                overflow="hidden"
            >
                <UrlAttachmentPlayer
                    key={selected.record.id}
                    attachment={selected}
                    sourceId={sourceId}
                    objectId={objectId}
                    fill
                />
            </Box>

            {attachments.length > 1 && (
                <VStack
                    as="ul"
                    role="list"
                    aria-label={t("events.streams")}
                    gap={1}
                    align="stretch"
                    maxH="44"
                    overflowY="auto"
                    m={0}
                    p={0}
                    listStyleType="none"
                >
                    {attachments.map((attachment, index) => {
                        const isSelected = attachment === selected;
                        return (
                            <li key={attachment.record.id}>
                                <HStack
                                    as="button"
                                    onClick={() => setSelectedId(attachment.record.id)}
                                    aria-current={isSelected ? "true" : undefined}
                                    aria-label={t("events.showStream", {
                                        title: labelAt(index),
                                    })}
                                    gap={3}
                                    w="full"
                                    px={3}
                                    py={2}
                                    borderRadius="md"
                                    border="1px solid"
                                    borderColor={isSelected ? "brand.fg" : "border.muted"}
                                    bg={isSelected ? "bg.subtle" : "glass.subtle"}
                                    cursor="pointer"
                                    textAlign="start"
                                    transition="background 0.15s ease"
                                    _hover={{ borderColor: "brand.fg" }}
                                >
                                    <Box color="fg.muted" flexShrink={0}>
                                        {isCameraFeed(attachment.record.streamKind) ? (
                                            <FaCamera size={14} />
                                        ) : (
                                            <FaVideo size={14} />
                                        )}
                                    </Box>
                                    <Text fontSize="sm" flex={1} minW={0} truncate>
                                        {streamUrlTitle(attachment.record.url)}
                                        {numberAt(index)}
                                    </Text>
                                    <Text fontSize="xs" color="fg.muted" flexShrink={0}>
                                        {streamKindName(attachment.record.streamKind)}
                                    </Text>
                                </HStack>
                            </li>
                        );
                    })}
                </VStack>
            )}
        </VStack>
    );
}
