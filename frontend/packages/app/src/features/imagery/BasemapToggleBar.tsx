// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Button, Center, Flex } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { TbChevronsDown, TbStack2 } from "react-icons/tb";
import { SearchInput } from "@opencop/ui";

interface BasemapToggleBarProps {
    open: boolean;
    onToggle: () => void;
    search: string;
    onSearchChange: (value: string) => void;
}

export function BasemapToggleBar({
    open,
    onToggle,
    search,
    onSearchChange,
}: BasemapToggleBarProps) {
    const { t } = useTranslation();

    return (
        <Flex
            align="center"
            bg="bg.glass"
            backdropFilter="blur(4px)"
            borderRadius="sm"
            borderWidth="1px"
            borderColor="border"
            w="fit-content"
            overflow="hidden"
        >
            <Button
                type="button"
                display="flex"
                alignItems="center"
                gap="1.5"
                px="2"
                py="2"
                cursor="pointer"
                flexShrink={0}
                fontSize="xs"
                fontWeight="medium"
                lineHeight="1"
                color="fg.muted"
                aria-label={t("imagery.basemapPicker.ariaLabel")}
                aria-expanded={open}
                _hover={{ bg: "active/5" }}
                transition="background 0.25s"
                onClick={onToggle}
            >
                <TbStack2 size={18} />
                {t("imagery.basemapPicker.label")}
                <Box
                    lineHeight="0"
                    style={{
                        transform: open ? "rotate(0deg)" : "rotate(180deg)",
                        transition: "transform 0.25s ease",
                    }}
                >
                    <TbChevronsDown size={14} />
                </Box>
            </Button>

            <Center
                style={{
                    maxWidth: open ? "205px" : "0px",
                    overflow: "hidden",
                    transition: "max-width 0.25s ease",
                }}
            >
                <Box w="1px" h="18px" bg="border" flexShrink={0} />
                <Box px="1.5" py="1">
                    <SearchInput
                        size="xs"
                        value={search}
                        onChange={onSearchChange}
                        placeholder={t("common.search")}
                    />
                </Box>
            </Center>
        </Flex>
    );
}
