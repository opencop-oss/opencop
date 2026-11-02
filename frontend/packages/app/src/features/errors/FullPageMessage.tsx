// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Heading, Text, Flex, Icon, Button } from "@chakra-ui/react";

interface FullPageMessageProps {
    icon: React.ElementType;
    title: string;
    message: string;
    buttonLabel: string;
    onButtonClick: () => void;
}

export function FullPageMessage({
    icon,
    title,
    message,
    buttonLabel,
    onButtonClick,
}: FullPageMessageProps) {
    return (
        <Flex h="100vh" w="100vw" bg="bg" align="center" justify="center" direction="column">
            <Heading mb="10" fontSize="3xl" letterSpacing="wide" color="fg.muted">
                {import.meta.env.VITE_APP_TITLE || "OPENCOP"}
            </Heading>
            <Box
                bg="bg.surface"
                p="10"
                borderRadius="lg"
                boxShadow="lg"
                textAlign="center"
                maxW="420px"
                w="full"
            >
                <Icon as={icon} boxSize="20" color="fg.muted" mb="6" display="block" mx="auto" />
                <Heading size="lg" mb="4" color="fg">
                    {title}
                </Heading>
                <Text color="fg.muted" mb="8">
                    {message}
                </Text>
                <Button
                    colorPalette="brand"
                    variant="solid"
                    size="lg"
                    w="full"
                    onClick={onButtonClick}
                >
                    {buttonLabel}
                </Button>
            </Box>
        </Flex>
    );
}
