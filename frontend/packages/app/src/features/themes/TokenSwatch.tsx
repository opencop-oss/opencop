// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Flex, Text } from "@chakra-ui/react";

interface TokenSwatchProps {
    color: string;
    token?: string;
    label?: string;
}

export function TokenSwatch({ color, token, label }: TokenSwatchProps) {
    return (
        <Flex direction="column" align="center" gap="1">
            <Box
                w="20px"
                h="20px"
                bg={color}
                borderWidth="1px"
                borderColor="border.muted"
                flexShrink={0}
            />
            {token && (
                <Text fontSize="2xs" color="fg.muted" textAlign="center" maxW="80px" lineClamp={2}>
                    {token}
                </Text>
            )}
            {label && (
                <Text fontSize="2xs" color="fg.muted" textAlign="center" maxW="80px" opacity={0.7}>
                    {label}
                </Text>
            )}
        </Flex>
    );
}
