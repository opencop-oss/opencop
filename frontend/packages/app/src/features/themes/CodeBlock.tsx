// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box } from "@chakra-ui/react";

interface CodeBlockProps {
    code: string;
}

export function CodeBlock({ code }: CodeBlockProps) {
    return (
        <Box
            as="pre"
            bg="bg.surface"
            borderWidth="1px"
            borderColor="border"
            p="4"
            overflowX="auto"
            fontSize="xs"
            lineHeight="1.6"
            whiteSpace="pre"
        >
            {code}
        </Box>
    );
}
