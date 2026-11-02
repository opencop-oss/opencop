// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactNode } from "react";
import { Flex, Heading, HStack, SkipNavContent, Text } from "@chakra-ui/react";
import { Divider } from "@opencop/ui";
import { ModuleWrapper } from "~/features/sidebar/ModuleWrapper";
import { ErrorBoundaryWrapper } from "~/features/errors/ErrorBoundaryWrapper";

interface PageLayoutProps {
    title: string;
    subtitle: string;
    componentName: string;
    subNavigation?: ReactNode;
    children: ReactNode;
}

export function PageLayout({
    title,
    subtitle,
    componentName,
    subNavigation,
    children,
}: PageLayoutProps) {
    return (
        <ModuleWrapper fixedHeight>
            <ErrorBoundaryWrapper componentName={componentName}>
                <Flex gap={2} width="100%" height="100%" minH="0" direction="column" bg="bg">
                    <HStack
                        as="header"
                        px={6}
                        py={6}
                        flexShrink={0}
                        background="bg.surface"
                        borderBottomWidth="1px"
                        borderBottomColor="border"
                    >
                        <Heading
                            as="h1"
                            px={6}
                            fontSize="2xl"
                            fontWeight="bold"
                            color="fg"
                            textTransform="uppercase"
                        >
                            {title}
                        </Heading>
                        <Divider orientation="vertical" borderWidth="1px" height="1.5em" />
                        <Text fontSize="sm" px={6} py={2} color="active" textTransform="uppercase">
                            {subtitle}
                        </Text>
                    </HStack>
                    {subNavigation}
                    <SkipNavContent asChild>
                        <Flex
                            as="main"
                            flex="1"
                            minH="0"
                            overflow="hidden"
                            px="12"
                            direction="column"
                        >
                            {children}
                        </Flex>
                    </SkipNavContent>
                </Flex>
            </ErrorBoundaryWrapper>
        </ModuleWrapper>
    );
}
