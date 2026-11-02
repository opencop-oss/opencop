// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Center, Flex, HStack, IconButton, Image, Text, VStack } from "@chakra-ui/react";
import type { EventAttachment, FileAttachment } from "@opencop/api";
import { useTranslation } from "react-i18next";
import { FaExternalLinkAlt, FaTrash, FaVideo, FaCamera } from "react-icons/fa";
import { ConfirmDialog, FileTypeIcon, Tooltip } from "@opencop/ui";
import { formatBytes } from "~/format.util";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import { formatDateMilitary } from "~/date.util";
import { useState } from "react";
import { useAttachmentMutations } from "../use-attachment-mutations.hook";
import {
    getThumbnailUrl,
    isCameraFeed,
    streamKindName,
    streamUrlTitle,
    urlAttachmentLabelKey,
} from "./attachment.util";

const CONFIRM_LABEL_MAX_LENGTH = 60;

interface AttachmentCardProps {
    attachment: EventAttachment;
    sourceId: string;
    objectId: string;
    canDelete: boolean;
    onOpen: () => void;
}

const FileTooltipContent = ({ attachment }: { attachment: FileAttachment }) => {
    const { t } = useTranslation();
    const timezone = useUserTimezone();
    const { mimeType, originalFilename, sizeBytes, createdAt, attributes } = attachment.record;

    return (
        <VStack align="start" gap="1" fontSize="xs" maxW="280px">
            <Flex gap="2" align="baseline">
                <Text fontWeight="semibold" minW="70px" flexShrink={0}>
                    {t("common.filename")}:
                </Text>
                <Text wordBreak="break-word">{originalFilename}</Text>
            </Flex>
            <Flex gap="2">
                <Text fontWeight="semibold" minW="70px">
                    {t("common.size")}:
                </Text>
                <Text>{formatBytes(sizeBytes)}</Text>
            </Flex>
            <Flex gap="2">
                <Text fontWeight="semibold" minW="70px">
                    {t("common.type")}:
                </Text>
                <Text wordBreak="break-word">{mimeType ?? t("common.notAvailable")}</Text>
            </Flex>
            {attributes?.widthPx != null && attributes?.heightPx != null && (
                <Flex gap="2">
                    <Text fontWeight="semibold" minW="70px">
                        {t("common.dimensions")}:
                    </Text>
                    <Text>
                        {attributes.widthPx} × {attributes.heightPx}
                    </Text>
                </Flex>
            )}
            <Flex gap="2">
                <Text fontWeight="semibold" minW="70px">
                    {t("common.uploaded")}:
                </Text>
                <Text>{formatDateMilitary(createdAt, timezone)}</Text>
            </Flex>
            <Text mt="1" opacity={0.5} fontStyle="italic">
                {t("common.clickToPreview")}
            </Text>
        </VStack>
    );
};

export function AttachmentCard({
    attachment,
    sourceId,
    objectId,
    canDelete,
    onOpen,
}: AttachmentCardProps) {
    const { t } = useTranslation();
    const timezone = useUserTimezone();
    const [confirmOpen, setConfirmOpen] = useState(false);
    const { deleteAttachment } = useAttachmentMutations({ sourceId, objectId });

    const isUrl = attachment.kind === "URL";
    const showCameraIcon = isUrl && isCameraFeed(attachment.record.streamKind);
    const label = isUrl ? attachment.record.url : attachment.record.originalFilename;
    const confirmLabel =
        label.length > CONFIRM_LABEL_MAX_LENGTH
            ? `${label.slice(0, CONFIRM_LABEL_MAX_LENGTH)}…`
            : label;
    const urlLabel = isUrl ? t(urlAttachmentLabelKey(attachment.record)) : "";
    const title = isUrl ? streamUrlTitle(attachment.record.url) : label;
    const subLabel = isUrl
        ? `${streamKindName(attachment.record.streamKind)} · ${urlLabel}`
        : formatBytes(attachment.record.sizeBytes) +
          " • " +
          formatDateMilitary(attachment.record.createdAt, timezone);
    const thumbnail = isUrl ? null : getThumbnailUrl(attachment);

    const card = (
        <HStack
            gap="3"
            px="3"
            py="2.5"
            borderRadius="lg"
            border="1px solid"
            borderColor="border.muted"
            bg="glass.subtle"
            width="full"
            transition="background 0.15s ease"
            _hover={{
                "& .row-actions": { opacity: 1, pointerEvents: "auto" },
            }}
        >
            <Center
                as="button"
                onClick={onOpen}
                aria-label={title}
                flexShrink={0}
                boxSize="10"
                borderRadius="md"
                borderWidth="1px"
                borderColor="transparent"
                overflow="hidden"
                bg="bg.subtle"
                color="fg.muted"
                transition="all 0.2s ease"
                cursor="pointer"
                _hover={{
                    borderColor: "brand.fg",
                    borderStyle: "solid",
                }}
            >
                {isUrl ? (
                    showCameraIcon ? (
                        <FaCamera size={16} />
                    ) : (
                        <FaVideo size={16} />
                    )
                ) : (
                    <Box position="relative" w="full" h="full">
                        {thumbnail && (
                            <Image
                                src={thumbnail}
                                alt={label}
                                w="full"
                                h="full"
                                objectFit="cover"
                                display="block"
                            />
                        )}
                        {attachment.record.contentType !== "IMAGE" && (
                            <Flex
                                position="absolute"
                                inset={0}
                                align="center"
                                justify="center"
                                bg="blackAlpha.400"
                            >
                                <FileTypeIcon
                                    mimeType={attachment.record.mimeType}
                                    contentType={attachment.record.contentType}
                                    size={16}
                                />
                            </Flex>
                        )}
                    </Box>
                )}
            </Center>

            <VStack align="start" flex={1} minW={0} gap="0.5">
                <Box as="button" onClick={onOpen} cursor="pointer" maxW="full">
                    <Text
                        fontSize="sm"
                        fontWeight="medium"
                        color="fg"
                        truncate
                        transition="all 0.2s ease"
                        _hover={{ color: "brand.fg" }}
                    >
                        {title}
                    </Text>
                </Box>
                <Text color="fg.muted" fontSize="xs">
                    {subLabel}
                </Text>
            </VStack>

            {(!isUrl || canDelete) && (
                <HStack
                    className="row-actions"
                    gap="1"
                    opacity={0}
                    pointerEvents="none"
                    transition="opacity 0.15s ease"
                    flexShrink={0}
                >
                    {!isUrl && (
                        <Tooltip
                            content={t("common.openInBrowser")}
                            positioning={{ placement: "top" }}
                        >
                            <IconButton
                                asChild
                                size="sm"
                                variant="ghost"
                                color="fg.muted"
                                _hover={{ color: "brand.fg" }}
                                aria-label={t("common.openInBrowser")}
                            >
                                <a href={attachment.url} target="_blank" rel="noopener noreferrer">
                                    <FaExternalLinkAlt />
                                </a>
                            </IconButton>
                        </Tooltip>
                    )}
                    {canDelete && (
                        <>
                            <Tooltip
                                content={t("common.delete")}
                                positioning={{ placement: "top" }}
                            >
                                <IconButton
                                    size="xs"
                                    variant="ghost"
                                    color="fg.muted"
                                    _hover={{ color: "red.fg" }}
                                    aria-label={t("common.delete")}
                                    onClick={() => setConfirmOpen(true)}
                                >
                                    <FaTrash />
                                </IconButton>
                            </Tooltip>
                            <ConfirmDialog
                                open={confirmOpen}
                                onClose={() => setConfirmOpen(false)}
                                title={t("common.confirm")}
                                message={t("events.confirmDeleteAttachment", {
                                    name: confirmLabel,
                                })}
                                onConfirm={() => {
                                    deleteAttachment(attachment.record.id);
                                    setConfirmOpen(false);
                                }}
                            />
                        </>
                    )}
                </HStack>
            )}
        </HStack>
    );

    if (isUrl) return card;

    return (
        <Tooltip
            positioning={{ placement: "right-start", gutter: 8 }}
            uppercase={false}
            content={<FileTooltipContent attachment={attachment} />}
        >
            {card}
        </Tooltip>
    );
}
