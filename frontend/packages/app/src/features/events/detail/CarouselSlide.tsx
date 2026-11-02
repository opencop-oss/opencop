// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, Image, Link, Text } from "@chakra-ui/react";
import type { EventAttachment, FileAttachment } from "@opencop/api";
import { FileTypeIcon } from "@opencop/ui";
import { useTranslation } from "react-i18next";
import { assertNever } from "assert-never";
import { UrlAttachmentPlayer } from "./UrlAttachmentPlayer";

export function CarouselSlide({
    attachment,
    sourceId,
    objectId,
}: {
    attachment: EventAttachment;
    sourceId: string;
    objectId: string;
}) {
    if (attachment.kind === "URL") {
        return (
            <UrlAttachmentPlayer
                attachment={attachment}
                sourceId={sourceId}
                objectId={objectId}
                style={{ width: "100%", maxHeight: "100%" }}
            />
        );
    }

    return <FileCarouselSlide attachment={attachment} />;
}

function FileCarouselSlide({ attachment }: { attachment: FileAttachment }) {
    const { t } = useTranslation();
    const url = attachment.url;

    switch (attachment.record.contentType) {
        case "IMAGE":
            return (
                <Image
                    src={url}
                    alt={attachment.record.originalFilename}
                    maxWidth="100%"
                    maxHeight="100%"
                    width="auto"
                    height="auto"
                    objectFit="contain"
                    borderRadius="md"
                    display="block"
                />
            );

        case "VIDEO":
            return (
                <video
                    src={url}
                    controls
                    playsInline
                    style={{
                        display: "block",
                        maxWidth: "100%",
                        maxHeight: "100%",
                        width: "auto",
                        height: "auto",
                    }}
                />
            );

        case "AUDIO":
            return (
                <Flex
                    direction="column"
                    align="center"
                    justify="center"
                    gap="3"
                    height="100%"
                    width="100%"
                >
                    <FileTypeIcon
                        mimeType={attachment.record.mimeType}
                        contentType={attachment.record.contentType}
                        size={48}
                    />
                    <Text fontSize="sm" color="fg.muted">
                        {attachment.record.originalFilename}
                    </Text>
                    <audio src={url} controls style={{ width: "100%", maxWidth: 400 }} />
                </Flex>
            );

        case "DOCUMENT":
            return attachment.record.mimeType === "application/pdf" ? (
                <iframe
                    src={url}
                    style={{
                        width: "100%",
                        height: "100%",
                        border: "none",
                        borderRadius: "var(--chakra-radii-md)",
                    }}
                />
            ) : (
                <Flex
                    direction="column"
                    align="center"
                    justify="center"
                    gap="3"
                    height="100%"
                    color="fg.muted"
                >
                    <FileTypeIcon
                        mimeType={attachment.record.mimeType}
                        contentType={attachment.record.contentType}
                        size={48}
                    />
                    <Text fontSize="sm">{attachment.record.originalFilename}</Text>
                    <Link
                        href={url}
                        target="_blank"
                        rel="noopener noreferrer"
                        fontSize="xs"
                        color="blue.400"
                        textDecoration="underline"
                    >
                        {t("common.openInBrowser")}
                    </Link>
                </Flex>
            );

        case "OTHER":
            return (
                <Flex
                    direction="column"
                    align="center"
                    justify="center"
                    gap="3"
                    height="100%"
                    color="fg.muted"
                >
                    <FileTypeIcon
                        mimeType={attachment.record.mimeType}
                        contentType={attachment.record.contentType}
                        size={48}
                    />
                    <Text fontSize="sm">{attachment.record.originalFilename}</Text>
                    <Link
                        href={url}
                        target="_blank"
                        rel="noopener noreferrer"
                        fontSize="xs"
                        colorPalette="brand"
                    >
                        {t("common.openInBrowser")}
                    </Link>
                </Flex>
            );

        default:
            return assertNever(attachment.record.contentType);
    }
}
