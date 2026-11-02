// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Dialog, Flex, Text } from "@chakra-ui/react";

interface DialogHeaderProps {
    title: string;
    secondaryText?: string;
}

export function DialogHeader({ title, secondaryText }: DialogHeaderProps) {
    return (
        <Dialog.Header>
            <Flex align="center" gap={3} padding={4}>
                <Dialog.Title textTransform="uppercase">{title}</Dialog.Title>
                {secondaryText && (
                    <>
                        <Text
                            color="brand.fg"
                            fontWeight="normal"
                            fontSize="md"
                            textTransform="uppercase"
                        >
                            |
                        </Text>
                        <Text
                            fontSize="sm"
                            color="brand.fg"
                            fontWeight="normal"
                            textTransform="uppercase"
                        >
                            {secondaryText}
                        </Text>
                    </>
                )}
            </Flex>
        </Dialog.Header>
    );
}
