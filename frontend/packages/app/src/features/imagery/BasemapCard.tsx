// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Card, Text, VStack } from "@chakra-ui/react";
import { TbCheck } from "react-icons/tb";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import type { MapTile } from "@opencop/api";
import { Tooltip } from "@opencop/ui";
import { TilePreviewImage } from "./TilePreviewImage";

interface BasemapCardProps {
    name: string;
    kindLabel: string;
    description: string | null;
    tile: MapTile;
    selected: boolean;
    onSelect: (tile: MapTile) => void;
}

function CardNameText({ name }: { name: string }) {
    return (
        <Box
            position="absolute"
            bottom="0"
            left="0"
            right="0"
            px="1.5"
            py="0.5"
            bg="rgba(0,0,0,0.55)"
            css={{ backdropFilter: "blur(4px)" }}
        >
            <Text
                fontSize="2xs"
                fontWeight="medium"
                color="white"
                overflow="hidden"
                textOverflow="ellipsis"
                whiteSpace="nowrap"
            >
                {name}
            </Text>
        </Box>
    );
}

export function BasemapCard({
    name,
    kindLabel,
    description,
    tile,
    selected,
    onSelect,
}: BasemapCardProps) {
    const { t } = useTranslation();
    const [previewFailed, setPreviewFailed] = useState(false);

    function handleNoPreview() {
        setPreviewFailed(true);
    }

    return (
        <Tooltip
            content={
                <VStack align="start" gap="0.5" maxW="xs">
                    <Text fontSize="xs" fontWeight="semibold">
                        {name}
                    </Text>
                    <Text fontSize="xs" color="fg.muted">
                        {kindLabel}
                    </Text>
                    {description && <Text fontSize="xs">{description}</Text>}
                </VStack>
            }
            uppercase={false}
            openDelay={500}
        >
            <Card.Root
                as="button"
                flexShrink={0}
                w="130px"
                h="82px"
                position="relative"
                overflow="hidden"
                borderRadius="sm"
                cursor="pointer"
                borderWidth={selected ? "2px" : "1px"}
                borderColor={selected ? "active" : "border.muted"}
                transition="border-color 0.25s"
                bg={previewFailed ? "bg.subtle" : undefined}
                onClick={() => onSelect(tile)}
                aria-label={
                    previewFailed ? `${name} — ${t("imagery.basemapPicker.noPreview")}` : name
                }
                aria-pressed={selected}
            >
                {previewFailed ? (
                    <>
                        {/* bottom offsets CardNameText height (py="0.5" + 2xs line box ≈ 20px) */}
                        <Box
                            position="absolute"
                            inset="0"
                            bottom="20px"
                            display="flex"
                            alignItems="center"
                            justifyContent="center"
                            px="2"
                        >
                            <Text fontSize="2xs" color="fg.muted" textAlign="center">
                                {t("imagery.basemapPicker.noPreview")}
                            </Text>
                        </Box>
                        <CardNameText name={name} />
                    </>
                ) : (
                    <>
                        <Box position="absolute" inset="0">
                            <TilePreviewImage tile={tile} onNoPreview={handleNoPreview} />
                        </Box>
                        <CardNameText name={name} />
                    </>
                )}
                {selected && (
                    <Box
                        position="absolute"
                        top="1"
                        right="1"
                        bg="active"
                        color="white"
                        borderRadius="full"
                        p="0.5"
                        lineHeight="0"
                    >
                        <TbCheck size={9} />
                    </Box>
                )}
            </Card.Root>
        </Tooltip>
    );
}
