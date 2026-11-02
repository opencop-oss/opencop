// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Button, Flex, HStack, Text, VStack } from "@chakra-ui/react";
import { ThemesSection } from "../ThemesSection";
import { ExampleSection } from "../ExampleSection";
import { ConnectionStatusIndicator } from "~/features/websocket/ConnectionStatusIndicator";
import { Tooltip, Divider } from "@opencop/ui";
import type { ConnectionState } from "@opencop/api";

const CONNECTION_STATES: ConnectionState[] = [
    "CONNECTED",
    "CONNECTING",
    "FAILED",
    "DISCONNECTED",
    "STOPPED",
    "NEW",
];

export function FeedbackSection() {
    return (
        <ThemesSection title="Feedback & Status">
            <VStack align="start" gap="8" w="full">
                <ExampleSection
                    label="ConnectionStatusIndicator"
                    code={`import { ConnectionStatusIndicator } from "~/features/websocket/ConnectionStatusIndicator";

<ConnectionStatusIndicator state="CONNECTED" />
<ConnectionStatusIndicator state="CONNECTING" />
<ConnectionStatusIndicator state="FAILED" />
<ConnectionStatusIndicator state="DISCONNECTED" />
<ConnectionStatusIndicator state="STOPPED" />
<ConnectionStatusIndicator state="NEW" />`}
                >
                    <HStack gap="6" flexWrap="wrap">
                        {CONNECTION_STATES.map((state) => (
                            <ConnectionStatusIndicator key={state} state={state} />
                        ))}
                    </HStack>
                </ExampleSection>

                <ExampleSection
                    label="Tooltip"
                    code={`import { Tooltip } from "@opencop/ui";

<Tooltip content="Help text">
    <Button>Hover me</Button>
</Tooltip>

<Tooltip content="With portal" portalled>
    <Button variant="outline">Portalled</Button>
</Tooltip>`}
                >
                    <HStack gap="4">
                        <Tooltip content="Help text">
                            <Button>Hover me</Button>
                        </Tooltip>
                        <Tooltip content="With portal" portalled>
                            <Button variant="outline">Portalled</Button>
                        </Tooltip>
                    </HStack>
                </ExampleSection>

                <ExampleSection
                    label="Divider"
                    code={`import { Divider } from "@opencop/ui";

// Horizontal (default)
<Divider />

// Vertical
<Divider orientation="vertical" />`}
                >
                    <VStack align="start" gap="4" w="full">
                        <Text fontSize="xs" color="fg.muted">
                            Horizontal
                        </Text>
                        <Divider />
                        <Flex gap="4" h="40px" align="center">
                            <Text fontSize="xs" color="fg.muted">
                                Vertical →
                            </Text>
                            <Box h="full">
                                <Divider orientation="vertical" />
                            </Box>
                            <Text fontSize="xs" color="fg.muted">
                                ← between text
                            </Text>
                        </Flex>
                    </VStack>
                </ExampleSection>
            </VStack>
        </ThemesSection>
    );
}
