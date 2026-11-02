// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactNode } from "react";
import { Card, Flex, Icon, Text } from "@chakra-ui/react";

interface HubCardProps {
    title: string;
    icon: ReactNode;
    onClick: () => void;
}

export function HubCard({ title, icon, onClick }: HubCardProps) {
    return (
        <Card.Root
            as="button"
            onClick={onClick}
            bg="bg.surface"
            borderWidth="1px"
            borderColor="border"
            cursor="pointer"
            _hover={{ borderColor: "active" }}
            transition="border-color 0.15s"
            width="100%"
            aspectRatio="1"
            textAlign="center"
        >
            <Card.Body p={{ base: 2, md: 4 }}>
                <Flex
                    direction="column"
                    gap={{ base: 2, md: 3 }}
                    align="center"
                    height="100%"
                    justifyContent="center"
                >
                    <Icon boxSize={{ base: 8, md: 12 }} color="active">
                        {icon}
                    </Icon>
                    <Text
                        fontSize={{ base: "xs", md: "sm" }}
                        fontWeight="bold"
                        textTransform="uppercase"
                    >
                        {title}
                    </Text>
                </Flex>
            </Card.Body>
        </Card.Root>
    );
}
