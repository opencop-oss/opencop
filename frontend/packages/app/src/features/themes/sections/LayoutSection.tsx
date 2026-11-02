// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Text, VStack, HStack } from "@chakra-ui/react";
import { ThemesSection } from "../ThemesSection";
import { ExampleSection } from "../ExampleSection";
import { Panel, StatusBox, Square, Circle, Glow, Section } from "@opencop/ui";
import { Card } from "~/features/events/detail/Card";

export function LayoutSection() {
    return (
        <ThemesSection title="Layout & Containers">
            <VStack align="start" gap="8" w="full">
                <ExampleSection
                    label="Panel (Glassmorphic)"
                    code={`import { Panel } from "@opencop/ui";

<Panel p="4" gap="2">
    <Text>Glassmorphic panel with blur backdrop</Text>
</Panel>`}
                >
                    <Panel p="4" gap="2">
                        <Text fontWeight="bold">Panel Component</Text>
                        <Text fontSize="sm" color="fg.muted">
                            Glassmorphic panel with blur backdrop, border, and box shadow.
                        </Text>
                    </Panel>
                </ExampleSection>

                <ExampleSection
                    label="Card"
                    code={`import { Card } from "~/features/events/detail/Card";

<Card label="Details">
    <Text>Card content here</Text>
</Card>`}
                >
                    <Card label="Details">
                        <Text fontSize="sm">
                            Elevated card container with label header and optional end element.
                        </Text>
                    </Card>
                </ExampleSection>

                <ExampleSection
                    label="Section (with AccentHeading)"
                    code={`import { Section } from "@opencop/ui";

<Section heading="Section Title">
    <Text>Section content</Text>
</Section>`}
                >
                    <Section heading="Section Title">
                        <Text fontSize="sm">
                            Content inside a Section component, which renders an AccentHeading and
                            children.
                        </Text>
                    </Section>
                </ExampleSection>

                <ExampleSection
                    label="Glow Effect"
                    code={`import { Glow } from "@opencop/ui";

<Glow intensity="subtle" color="cyan.400">●</Glow>
<Glow intensity="medium" color="cyan.400">●</Glow>
<Glow intensity="strong" color="cyan.400">●</Glow>
<Glow type="box" color="accent.500">Box Glow</Glow>`}
                >
                    <HStack gap="8" align="center">
                        <VStack>
                            <Glow intensity="subtle" color="cyan.400" fontSize="2xl">
                                ●
                            </Glow>
                            <Text fontSize="2xs" color="fg.muted">
                                subtle
                            </Text>
                        </VStack>
                        <VStack>
                            <Glow intensity="medium" color="cyan.400" fontSize="2xl">
                                ●
                            </Glow>
                            <Text fontSize="2xs" color="fg.muted">
                                medium
                            </Text>
                        </VStack>
                        <VStack>
                            <Glow intensity="strong" color="cyan.400" fontSize="2xl">
                                ●
                            </Glow>
                            <Text fontSize="2xs" color="fg.muted">
                                strong
                            </Text>
                        </VStack>
                        <VStack>
                            <Glow type="box" color="accent.500" p="2">
                                <Text fontSize="sm">Box Glow</Text>
                            </Glow>
                            <Text fontSize="2xs" color="fg.muted">
                                box type
                            </Text>
                        </VStack>
                    </HStack>
                </ExampleSection>

                <ExampleSection
                    label="Square & Circle Shapes"
                    code={`import { Square, Circle } from "@opencop/ui";

<Square size="xs" bg="accent.500" />
<Square size="sm" bg="brand.500" />
<Circle size="xs" bg="success.500" />
<Circle size="sm" bg="red.500" />`}
                >
                    <HStack gap="4" align="center">
                        <VStack>
                            <Square size="xs" bg="accent.500" />
                            <Text fontSize="2xs" color="fg.muted">
                                Square xs
                            </Text>
                        </VStack>
                        <VStack>
                            <Square size="sm" bg="brand.500" />
                            <Text fontSize="2xs" color="fg.muted">
                                Square sm
                            </Text>
                        </VStack>
                        <VStack>
                            <Circle size="xs" bg="success.500" />
                            <Text fontSize="2xs" color="fg.muted">
                                Circle xs
                            </Text>
                        </VStack>
                        <VStack>
                            <Circle size="sm" bg="red.500" />
                            <Text fontSize="2xs" color="fg.muted">
                                Circle sm
                            </Text>
                        </VStack>
                    </HStack>
                </ExampleSection>

                <ExampleSection
                    label="StatusBox"
                    code={`import { StatusBox } from "@opencop/ui";

<StatusBox>Status message here</StatusBox>

Note: StatusBox is positioned fixed at bottom-center by default.
This example renders it inline for demonstration.`}
                >
                    <Box position="relative" h="60px" w="full">
                        <StatusBox
                            position="relative"
                            bottom="auto"
                            left="auto"
                            right="auto"
                            transform="none"
                        >
                            Status: All systems operational
                        </StatusBox>
                    </Box>
                </ExampleSection>
            </VStack>
        </ThemesSection>
    );
}
