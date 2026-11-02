// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, Text, VStack, type FlexProps } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { AccentHeading } from "@opencop/ui";
import { NestedDisplay } from "./NestedDisplay";

export function GroupDisplay({
    path,
    label,
    object,
    mutable,
    ...rest
}: {
    path: string[];
    label: string;
    object: object;
    mutable?: boolean;
} & FlexProps) {
    const { t } = useTranslation();
    const isEmpty = Object.keys(object).length === 0;

    return (
        <Flex direction="column" w="full" gap={2} {...rest}>
            <AccentHeading>{label}</AccentHeading>

            {isEmpty ? (
                <Text px="2" py="2" fontSize="xs" color="fg.muted" fontStyle="italic">
                    {t("events.noItems", { label: label.toLocaleLowerCase() })}
                </Text>
            ) : (
                <VStack
                    align="start"
                    w="full"
                    bg="bg.surface"
                    borderRadius="sm"
                    borderWidth="1px"
                    borderColor="border"
                    overflow="visible"
                >
                    <NestedDisplay path={path} value={object} mutable={mutable} />
                </VStack>
            )}
        </Flex>
    );
}
