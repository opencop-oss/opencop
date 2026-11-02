// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Center, Flex, Menu, Text } from "@chakra-ui/react";
import type { ReactNode } from "react";
import { getElementKey, type Element } from "@opencop/elements";
import { ElementPreviewCanvas } from "./ElementPreviewCanvas";
import { formatTimestamp } from "./helpers.util";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import { FaChevronRight } from "react-icons/fa";

interface ElementRowProps {
    element: Element;
    onSelect: (element: Element) => void;
}

export function ElementRow({ element, onSelect }: ElementRowProps): ReactNode {
    const key = getElementKey(element);
    const timezone = useUserTimezone();
    return (
        <Menu.Item
            key={key}
            value={key}
            onClick={() => onSelect(element)}
            id={key}
            px="3"
            py="3"
            bg="slate.bg/80"
            borderBottom="1px solid"
            borderColor="whiteAlpha.100"
            cursor="pointer"
            _highlighted={{
                bg: "whiteAlpha.200",
            }}
            _last={{ borderBottom: "none" }}
        >
            <Flex align="center" gap="3" width="100%">
                <Center
                    flexShrink={0}
                    w="10"
                    h="10"
                    border="1px solid"
                    borderColor="whiteAlpha.200"
                    overflow="hidden"
                    backgroundImage="conic-gradient(#4a4a4a 25%, #333333 25%, #333333 50%, #4a4a4a 50%, #4a4a4a 75%, #333333 75%)"
                    backgroundSize="10px 10px"
                >
                    <ElementPreviewCanvas element={element} />
                </Center>

                <Flex direction="column" gap="0.5" flex={1} minW={0}>
                    <Text
                        fontSize="sm"
                        fontWeight={700}
                        color="white"
                        letterSpacing="1px"
                        overflow="hidden"
                        whiteSpace="nowrap"
                        textOverflow="ellipsis"
                    >
                        {element.getDisplayName()}
                    </Text>
                    {element.time != null && (
                        <Text fontSize="xs" color="active" fontFamily="mono" letterSpacing="0.02em">
                            {formatTimestamp(element.time, timezone)}
                        </Text>
                    )}
                </Flex>

                <Box color="whiteAlpha.400" flexShrink={0} fontSize="xs">
                    <FaChevronRight />
                </Box>
            </Flex>
        </Menu.Item>
    );
}
