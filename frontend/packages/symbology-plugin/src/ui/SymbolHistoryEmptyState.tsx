// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Text } from "@chakra-ui/react";

interface SymbolHistoryEmptyStateProps {
    message: string;
    hint?: string;
}

export function SymbolHistoryEmptyState({ message, hint }: SymbolHistoryEmptyStateProps) {
    return (
        <Box p={4} textAlign="center" w="full">
            <Text color="fg.muted" fontSize="sm">
                {message}
            </Text>
            {hint && (
                <Text color="fg.muted" fontSize="xs" mt={2}>
                    {hint}
                </Text>
            )}
        </Box>
    );
}
