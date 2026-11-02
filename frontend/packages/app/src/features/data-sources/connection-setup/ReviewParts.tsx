// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Flex, Grid, GridItem, IconButton, Text } from "@chakra-ui/react";
import { useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { FaEye, FaEyeSlash } from "react-icons/fa6";

/** A secret shown as asterisks, with a toggle to reveal it; a dash when empty. */
export function MaskedValue({ value }: { value: string }) {
    const { t } = useTranslation();
    const [visible, setVisible] = useState(false);

    if (!value) return <Text fontSize="sm">-</Text>;

    return (
        <Flex align="center" gap={1}>
            <Text fontSize="sm">{visible ? value : "*".repeat(value.length)}</Text>
            <IconButton
                aria-label={visible ? t("common.hide") : t("common.show")}
                variant="ghost"
                size="2xs"
                onClick={() => setVisible(!visible)}
            >
                {visible ? <FaEyeSlash /> : <FaEye />}
            </IconButton>
        </Flex>
    );
}

export function ReviewField({ label, children }: { label: string; children: ReactNode }) {
    return (
        <>
            <GridItem>
                <Text fontSize="sm" color="fg.muted">
                    {label}
                </Text>
            </GridItem>
            <GridItem>{children}</GridItem>
        </>
    );
}

/** Review cards side by side from `md` up, stretched to the taller one's height. */
export function ReviewRow({ children }: { children: ReactNode }) {
    return (
        <Grid templateColumns={{ base: "1fr", md: "1fr 1fr" }} gap={3}>
            {children}
        </Grid>
    );
}

export function ReviewSection({ title, children }: { title: string; children: ReactNode }) {
    return (
        <Box
            role="group"
            aria-label={title}
            p={3}
            borderWidth="1px"
            borderColor="border"
            borderRadius="md"
            bg="bg.subtle"
        >
            <Text fontWeight="bold" fontSize="sm" mb={2}>
                {title}
            </Text>
            <Grid templateColumns="auto 1fr" gapX={4} gapY={1}>
                {children}
            </Grid>
        </Box>
    );
}
