// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, HStack, VStack } from "@chakra-ui/react";
import { useState, type ReactNode } from "react";
import { TbChevronDown, TbChevronRight } from "react-icons/tb";
import { ActiveHeading } from "@opencop/ui";

interface ThemesSectionProps {
    title: string;
    children: ReactNode;
    defaultOpen?: boolean;
}

export function ThemesSection({ title, children, defaultOpen = true }: ThemesSectionProps) {
    const [open, setOpen] = useState(defaultOpen);

    return (
        <VStack align="start" gap="6" w="full">
            <HStack
                w="full"
                cursor="pointer"
                userSelect="none"
                onClick={() => setOpen((v) => !v)}
                _hover={{ opacity: 0.8 }}
            >
                <Box fontSize="xl" color="active" flexShrink={0}>
                    {open ? <TbChevronDown /> : <TbChevronRight />}
                </Box>
                <Box flex="1">
                    <ActiveHeading>{title}</ActiveHeading>
                </Box>
            </HStack>
            {open && children}
        </VStack>
    );
}
