// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Flex, Icon, IconButton, Text } from "@chakra-ui/react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { FaChevronLeft, FaChevronRight, FaExternalLinkAlt, FaTimes } from "react-icons/fa";
import type { Banner } from "@opencop/api";
import { BANNER_BAR_HEIGHT } from "./banner.util";

interface BannerBarProps {
    banner: Banner;
    content: string;
    role?: "banner" | "contentinfo" | "status";
    onDismiss: (id: string) => void;
    navControls?: ReactNode;
}

export function BannerBar({
    banner,
    content,
    role = "banner",
    onDismiss,
    navControls,
}: BannerBarProps) {
    const { t } = useTranslation();
    const safeUrl = /^https?:\/\//.test(banner.linkUrl ?? "") ? banner.linkUrl : undefined;
    const isSystem = banner.kind === "SYSTEM";
    const textColor = banner.textColor ?? "#ffffff";

    const inner = (
        <Flex
            position="relative"
            align="center"
            px={3}
            h={BANNER_BAR_HEIGHT}
            bg={banner.color}
            role={role}
            width="100%"
            flexShrink={0}
        >
            <Box width="100%" fontSize="xs" textAlign="center">
                {isSystem && (
                    <Text as="span" color={textColor} fontWeight="bold" mr={1}>
                        {banner.name}:
                    </Text>
                )}
                <Text as="span" color={textColor} whiteSpace="pre-wrap">
                    {content}
                </Text>
            </Box>
            <Flex
                position="absolute"
                right={3}
                top={0}
                bottom={0}
                align="center"
                gap={1}
                color={textColor}
            >
                {navControls}
                {safeUrl && (
                    <Icon as={FaExternalLinkAlt} boxSize={3} color={textColor} opacity={0.7} />
                )}
                {banner.dismissible && (
                    <IconButton
                        aria-label={t("banners.dismiss")}
                        variant="ghost"
                        size="xs"
                        color={textColor}
                        _hover={{ bg: "whiteAlpha.200" }}
                        onClick={(e) => {
                            e.preventDefault();
                            onDismiss(banner.id);
                        }}
                    >
                        <FaTimes />
                    </IconButton>
                )}
            </Flex>
        </Flex>
    );

    if (safeUrl) {
        return (
            <a
                href={safeUrl}
                target="_blank"
                rel="noopener noreferrer"
                style={{ display: "block" }}
            >
                {inner}
            </a>
        );
    }
    return inner;
}

interface BarNavControlsProps {
    index: number;
    total: number;
    onPrev: () => void;
    onNext: () => void;
}

export function BarNavControls({ index, total, onPrev, onNext }: BarNavControlsProps) {
    const { t } = useTranslation();
    return (
        <Flex align="center" gap={0}>
            <IconButton
                aria-label={t("banners.previous")}
                size="xs"
                variant="ghost"
                color="inherit"
                _hover={{ bg: "whiteAlpha.200" }}
                onClick={onPrev}
            >
                <FaChevronLeft />
            </IconButton>
            <Text fontSize="2xs" color="inherit" lineHeight={1} px={1}>
                {index + 1}/{total}
            </Text>
            <IconButton
                aria-label={t("banners.next")}
                size="xs"
                variant="ghost"
                color="inherit"
                _hover={{ bg: "whiteAlpha.200" }}
                onClick={onNext}
            >
                <FaChevronRight />
            </IconButton>
        </Flex>
    );
}
