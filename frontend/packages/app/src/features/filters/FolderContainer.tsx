// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Flex, Heading, Text } from "@chakra-ui/react";
import { useState } from "react";
import { FaFolderOpen, FaFolder } from "react-icons/fa6";
import { MdEvent } from "react-icons/md";
import { TreeView } from "@opencop/ui";

interface FolderContainerProps {
    title: string;
    icon: "events" | "server";
    count?: number;
    children?: React.ReactNode;
}

export function FolderContainer({ title, icon, count, children }: FolderContainerProps) {
    const [isOpen, setIsOpen] = useState<boolean>(false);

    const IconComponent = icon === "events" ? MdEvent : FaFolder;

    return (
        <Box width="100%" position="relative">
            {/* Horizontal connector line */}
            <TreeView variant="branch" />

            <Flex
                width="100%"
                direction="row"
                p={2}
                alignItems="center"
                gap={2}
                transition="background-color .2s ease-out"
                _hover={{ bg: "glass.subtle", cursor: "pointer" }}
                borderRadius="md"
                onClick={() => setIsOpen(!isOpen)}
            >
                {isOpen ? <FaFolderOpen size={10} color="fg" /> : <FaFolder color="fg" size={10} />}
                <Heading size="xs" fontWeight={600}>
                    {title}
                </Heading>
                <IconComponent size={10} color="fg" />
                {count !== undefined && (
                    <Text fontSize="xs" fontWeight={400} opacity={0.7}>
                        ({count})
                    </Text>
                )}
            </Flex>
            {isOpen ? <TreeView variant="trunk">{children}</TreeView> : null}
        </Box>
    );
}
