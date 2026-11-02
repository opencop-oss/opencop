// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Carousel, Dialog, Flex, IconButton, Image } from "@chakra-ui/react";
import type { EventAttachment } from "@opencop/api";
import { useTranslation } from "react-i18next";
import { useState } from "react";
import { FaCamera, FaChevronLeft, FaChevronRight, FaTimes } from "react-icons/fa";
import { BaseDialog, DialogHeader, FileTypeIcon } from "@opencop/ui";
import { CarouselSlide } from "./CarouselSlide";
import { truncate } from "lodash";
import { getThumbnailUrl, isCameraFeed, urlAttachmentTitle } from "./attachment.util";

interface AttachmentCarouselProps {
    attachments: EventAttachment[];
    sourceId: string;
    objectId: string;
    startIndex: number;
    isOpen: boolean;
    onClose: () => void;
}

/** The dialog header doesn't clip, so a very long domain is cut here. */
const MAX_TITLE_LENGTH = 60;

export function AttachmentCarousel({
    attachments,
    sourceId,
    objectId,
    startIndex,
    isOpen,
    onClose,
}: AttachmentCarouselProps) {
    const { t } = useTranslation();
    const [currentIndex, setCurrentIndex] = useState(startIndex);
    const current = attachments[currentIndex];
    const title =
        current?.kind === "URL"
            ? truncate(urlAttachmentTitle(current.record), { length: MAX_TITLE_LENGTH })
            : (current?.record.originalFilename ?? "");

    return (
        <BaseDialog open={isOpen} onClose={onClose} size="xl">
            <DialogHeader title={title} />
            <Dialog.CloseTrigger asChild>
                <IconButton
                    aria-label={t("common.close")}
                    variant="ghost"
                    size="sm"
                    position="absolute"
                    top="3"
                    right="3"
                >
                    <FaTimes />
                </IconButton>
            </Dialog.CloseTrigger>

            <Dialog.Body p="4">
                <Carousel.Root
                    slideCount={attachments.length}
                    page={currentIndex}
                    onPageChange={(e) => setCurrentIndex(e.page)}
                    loop
                >
                    <Flex align="center" gap="2" width="full">
                        <Carousel.PrevTrigger asChild>
                            <IconButton
                                size="xs"
                                variant="outline"
                                flexShrink={0}
                                aria-label={t("common.previous")}
                            >
                                <FaChevronLeft />
                            </IconButton>
                        </Carousel.PrevTrigger>

                        <Box flex={1} overflow="hidden">
                            <Carousel.ItemGroup>
                                {attachments.map((attachment, index) => (
                                    <Carousel.Item key={attachment.record.id} index={index}>
                                        <Flex align="center" justify="center" height="55vh">
                                            {index === currentIndex && (
                                                <CarouselSlide
                                                    attachment={attachment}
                                                    sourceId={sourceId}
                                                    objectId={objectId}
                                                />
                                            )}
                                        </Flex>
                                    </Carousel.Item>
                                ))}
                            </Carousel.ItemGroup>
                        </Box>

                        <Carousel.NextTrigger asChild>
                            <IconButton
                                size="xs"
                                variant="outline"
                                flexShrink={0}
                                aria-label={t("common.next")}
                            >
                                <FaChevronRight />
                            </IconButton>
                        </Carousel.NextTrigger>
                    </Flex>

                    {attachments.length > 1 && (
                        <Carousel.IndicatorGroup
                            display="flex"
                            justifyContent="center"
                            gap="2"
                            mt="3"
                            flexWrap="nowrap"
                            overflowX="auto"
                            py="1"
                        >
                            {attachments.map((attachment, index) => (
                                <Carousel.Indicator
                                    key={attachment.record.id}
                                    index={index}
                                    unstyled
                                    cursor="pointer"
                                    opacity={0.45}
                                    transition="opacity 0.2s"
                                    flexShrink={0}
                                    borderRadius="0px"
                                    overflow="hidden"
                                    _current={{
                                        opacity: 1,
                                        outline: "2px solid",
                                        outlineColor: "border",
                                        outlineOffset: "2px",
                                    }}
                                >
                                    <AttachmentThumbnailStrip attachment={attachment} />
                                </Carousel.Indicator>
                            ))}
                        </Carousel.IndicatorGroup>
                    )}
                </Carousel.Root>
            </Dialog.Body>
        </BaseDialog>
    );
}

function AttachmentThumbnailStrip({ attachment }: { attachment: EventAttachment }) {
    const isUrl = attachment.kind === "URL";
    const showCameraIcon = isUrl && isCameraFeed(attachment.record.streamKind);
    const thumbnail = isUrl ? null : getThumbnailUrl(attachment);
    const isImage = !isUrl && attachment.record.contentType === "IMAGE";
    const mimeType = isUrl ? undefined : attachment.record.mimeType;
    const contentType = isUrl ? "VIDEO" : attachment.record.contentType;

    return (
        <Flex position="relative" style={{ width: 56, height: 40, overflow: "hidden" }}>
            {thumbnail && (
                <Image src={thumbnail} alt="" w="56px" h="40px" objectFit="cover" display="block" />
            )}
            {!isImage && (
                <Flex
                    position="absolute"
                    inset={0}
                    align="center"
                    justify="center"
                    bg={isUrl ? "bg.subtle" : "blackAlpha.400"}
                >
                    {showCameraIcon ? (
                        <FaCamera size={14} />
                    ) : (
                        <FileTypeIcon mimeType={mimeType} contentType={contentType} size={14} />
                    )}
                </Flex>
            )}
        </Flex>
    );
}
