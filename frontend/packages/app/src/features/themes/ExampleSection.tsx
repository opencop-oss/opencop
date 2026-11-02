// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Button, Flex, HStack, Text, VStack } from "@chakra-ui/react";
import { useRef, useState, type ReactNode } from "react";
import { CodeBlock } from "./CodeBlock";
import { TokenSwatch } from "./TokenSwatch";
import { useExtractedColors } from "./use-extracted-colors.hook";

interface ExampleSectionProps {
    label: string;
    code: string;
    children: ReactNode;
}

export function ExampleSection({ label, code, children }: ExampleSectionProps) {
    const [showCode, setShowCode] = useState(false);
    const [showColors, setShowColors] = useState(false);
    const exampleRef = useRef<HTMLDivElement>(null);
    const colors = useExtractedColors(exampleRef);

    return (
        <VStack align="start" gap="3" w="full">
            <HStack justify="space-between" w="full">
                <Text fontWeight="bold" fontSize="sm">
                    {label}
                </Text>
                <HStack gap="2">
                    <Button variant="outline" size="xs" onClick={() => setShowColors((v) => !v)}>
                        {showColors ? "Hide Colors" : "Colors"}
                    </Button>
                    <Button variant="outline" size="xs" onClick={() => setShowCode((v) => !v)}>
                        {showCode ? "Hide Code" : "Show Code"}
                    </Button>
                </HStack>
            </HStack>

            <Box
                ref={exampleRef}
                w="full"
                p="4"
                bg="bg.elevated"
                borderWidth="1px"
                borderColor="border"
            >
                {children}
            </Box>

            {showColors && colors.length > 0 && (
                <Box w="full" p="3" bg="bg.surface" borderWidth="1px" borderColor="border">
                    <Text fontSize="xs" fontWeight="bold" color="fg.muted" mb="2">
                        Colors Used
                    </Text>
                    <Flex gap="4" flexWrap="wrap">
                        {colors.map((c) => (
                            <TokenSwatch
                                key={c.computed}
                                color={c.computed}
                                token={c.token ?? undefined}
                                label={c.computed}
                            />
                        ))}
                    </Flex>
                </Box>
            )}

            {showCode && <CodeBlock code={code} />}
        </VStack>
    );
}
