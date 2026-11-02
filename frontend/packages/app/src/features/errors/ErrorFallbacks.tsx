// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, VStack, Text, Button, Flex } from "@chakra-ui/react";
import React from "react";
import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";

interface CompactErrorFallbackProps {
    error: unknown;
    resetErrorBoundary: () => void;
    componentName: string;
}

const getErrorMessage = (error: unknown, t: TFunction): string => {
    if (error instanceof Error) return error.message;
    if (typeof error === "string") return error;
    return t("errors.unknownError");
};

export const CompactErrorFallback: React.FC<CompactErrorFallbackProps> = ({
    error,
    resetErrorBoundary,
    componentName,
}) => {
    const { t } = useTranslation();
    return (
        <Box
            p={2}
            bg="red.900"
            borderRadius="md"
            border="1px solid"
            borderColor="red.700"
            position="absolute"
            bottom="0"
            right="0"
        >
            <VStack gap={2} alignItems="start">
                <Text fontSize="sm" fontWeight="bold" color="white">
                    {t("errors.componentError", { componentName })}
                </Text>
                <Text fontSize="xs" color="red.300" lineClamp={2}>
                    {getErrorMessage(error, t)}
                </Text>
                <Button size="xs" colorScheme="red" onClick={resetErrorBoundary}>
                    {t("errors.retry")}
                </Button>
            </VStack>
        </Box>
    );
};

interface MapErrorFallbackProps {
    error: unknown;
    resetErrorBoundary: () => void;
}

export const MapErrorFallback: React.FC<MapErrorFallbackProps> = ({
    error,
    resetErrorBoundary,
}) => {
    const { t } = useTranslation();
    return (
        <Flex
            width="100vw"
            height="100vh"
            alignItems="center"
            justifyContent="center"
            bg="gray.900"
        >
            <VStack gap={4} maxW="600px" p={6} bg="red.900" borderRadius="md">
                <Text fontSize="2xl" fontWeight="bold" color="white">
                    {t("errors.mapError")}
                </Text>
                <Box p={4} bg="gray.800" borderRadius="md" w="100%" maxH="200px" overflowY="auto">
                    <Text color="red.300" fontFamily="mono" fontSize="sm">
                        {getErrorMessage(error, t)}
                    </Text>
                </Box>
                <Button colorScheme="red" onClick={resetErrorBoundary} size="lg">
                    {t("errors.reloadMap")}
                </Button>
            </VStack>
        </Flex>
    );
};
