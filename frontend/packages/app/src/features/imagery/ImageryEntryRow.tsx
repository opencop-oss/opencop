// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactNode } from "react";
import { Button, Flex, HStack, Text } from "@chakra-ui/react";
import { OcSwitch, Tooltip } from "@opencop/ui";
import { useTranslation } from "react-i18next";
import { MdTune } from "react-icons/md";

interface ImageryEntryRowProps {
    name: string;
    nameTooltip?: ReactNode;
    kindLabel: string;
    visible: boolean;
    onVisibleChange: (visible: boolean) => void;
    detailsOpen: boolean;
    detailsId: string;
    onDetailsOpenChange: (open: boolean) => void;
}

export function ImageryEntryRow({
    name,
    nameTooltip,
    kindLabel,
    visible,
    onVisibleChange,
    detailsOpen,
    detailsId,
    onDetailsOpenChange,
}: ImageryEntryRowProps) {
    const { t } = useTranslation();

    const nameEl = nameTooltip ? (
        <Tooltip content={nameTooltip} uppercase={false} openDelay={300}>
            <span>{name}</span>
        </Tooltip>
    ) : (
        name
    );

    return (
        <Flex px="3" py="2" align="center" gap="2">
            <HStack flex="1" minW="0" gap="1.5" align="baseline">
                <Text
                    fontSize="sm"
                    fontWeight="medium"
                    overflow="hidden"
                    textOverflow="ellipsis"
                    whiteSpace="nowrap"
                >
                    {nameEl}
                </Text>
                <Text fontSize="2xs" color="fg.muted" flexShrink={0}>
                    {kindLabel}
                </Text>
            </HStack>
            <OcSwitch
                checked={visible}
                label={name}
                srOnlyLabel
                onCheckedChange={onVisibleChange}
                colorPalette="brand"
                fullWidth={false}
                rootProps={{ size: "sm" }}
            />
            <Button
                size="md"
                onClick={() => onDetailsOpenChange(!detailsOpen)}
                flexShrink={0}
                active="iconToggle"
                data-active={detailsOpen ? "" : undefined}
                aria-expanded={detailsOpen}
                aria-controls={detailsId}
                aria-label={
                    detailsOpen
                        ? t("imagery.overlay.collapseDetails")
                        : t("imagery.overlay.expandDetails")
                }
            >
                <MdTune size={16} />
            </Button>
        </Flex>
    );
}
