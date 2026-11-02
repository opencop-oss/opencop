// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Center, Flex, Spinner, Text, VStack } from "@chakra-ui/react";
import { useQuery } from "@tanstack/react-query";
import type { EventDetails } from "@opencop/api";
import { useTranslation } from "react-i18next";
import { useState } from "react";
import { FaImage } from "react-icons/fa";
import { AttachmentCard } from "../detail/AttachmentCard";
import { AttachmentUpload } from "../detail/AttachmentUpload";
import { AttachmentCarousel } from "../detail/AttachmentCarousel";
import { isSourceOwnedAttachment } from "../detail/attachment.util";
import { eventQueries } from "../events.queries";
import { useSpinDelay } from "spin-delay";
import { useHistoricalMode } from "~/features/historical/data-plane/historical.store";

interface MediaSectionProps {
    details: EventDetails;
}

export function MediaSection({ details }: MediaSectionProps) {
    const { t } = useTranslation();
    const sourceId = details.source;
    const objectId = details.objectId;
    const isLocalEvent = sourceId === "local";
    const isHistorical = useHistoricalMode() === "historical";
    const canEdit = !isLocalEvent;

    const [isDialogOpen, setIsDialogOpen] = useState(false);
    const [startCarouselIndex, setStartCarouselIndex] = useState(0);

    const {
        data: attachments = [],
        isLoading,
        isError,
    } = useQuery({
        ...eventQueries.allAttachments(sourceId, objectId),
        enabled: !isLocalEvent && !isHistorical,
    });

    const showSpinner = useSpinDelay(isLoading, { delay: 500, minDuration: 200, ssr: false });

    const handleCardClick = (index: number) => {
        setStartCarouselIndex(index);
        setIsDialogOpen(true);
    };

    return (
        <Flex direction="column" gap={4} py={3}>
            {canEdit && (
                <Box flex={1}>
                    <AttachmentUpload
                        sourceId={sourceId}
                        objectId={objectId}
                        disabled={isLocalEvent}
                    />
                </Box>
            )}

            {showSpinner && (
                <Center py={8}>
                    <Spinner size="lg" color="fg.muted" />
                </Center>
            )}

            {isError && (
                <Center py={8}>
                    <Text fontSize="sm" color="red.500">
                        {t("events.failedToLoadAttachments")}
                    </Text>
                </Center>
            )}

            {!isLoading && !isError && attachments.length > 0 && (
                <VStack gap={2} width="full">
                    {attachments.map((attachment, index) => (
                        <AttachmentCard
                            key={attachment.record.id}
                            attachment={attachment}
                            sourceId={sourceId}
                            objectId={objectId}
                            canDelete={
                                canEdit &&
                                !(attachment.kind === "URL" && isSourceOwnedAttachment(attachment))
                            }
                            onOpen={() => handleCardClick(index)}
                        />
                    ))}
                </VStack>
            )}

            {!isLoading && !isError && attachments.length === 0 && <EmptyMediaState />}

            {isDialogOpen && (
                <AttachmentCarousel
                    key={startCarouselIndex}
                    attachments={attachments}
                    sourceId={sourceId}
                    objectId={objectId}
                    startIndex={startCarouselIndex}
                    isOpen={isDialogOpen}
                    onClose={() => setIsDialogOpen(false)}
                />
            )}
        </Flex>
    );
}

function EmptyMediaState() {
    const { t } = useTranslation();
    return (
        <Flex
            direction="column"
            align="center"
            justify="center"
            gap="2"
            py={8}
            color="fg.muted"
            textAlign="center"
        >
            <FaImage size={32} style={{ opacity: 0.3 }} />
            <Text fontSize="sm" opacity={0.8}>
                {t("events.noMediaAttached")}
            </Text>
            <Text fontSize="xs" opacity={0.6}>
                {t("events.mediaWillAppear")}
            </Text>
        </Flex>
    );
}
