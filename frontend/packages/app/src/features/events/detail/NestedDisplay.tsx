// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Field, Flex, HStack, IconButton, Input, Text, VStack } from "@chakra-ui/react";
import type { ReactElement } from "react";
import { FaPlus } from "react-icons/fa";
import { startCase } from "lodash";
import { useTranslation } from "react-i18next";
import { useEventsEditingStore, useEventsEditingActions } from "../events-editing.store";
import { ValueDisplay } from "./ValueDisplay";

const excludedProps = ["userId"];

export interface NestedDisplayProps {
    path: string[];
    value: unknown;
    mutable?: boolean;
}

export function NestedDisplay({ path, value, mutable }: NestedDisplayProps): ReactElement {
    const { t } = useTranslation();
    const overlay = useEventsEditingStore((s) => s.overlay);
    const { setField } = useEventsEditingActions();

    if (value && typeof value === "object") {
        const entries = Object.entries(value as object);

        return (
            <VStack align="start" w="full">
                {Array.isArray(value) ? (
                    value.map((val, i) => (
                        <HStack
                            // eslint-disable-next-line @eslint-react/no-array-index-key -- generic array, no stable id
                            key={i}
                            w="full"
                            pr={4}
                            justify="space-between"
                            bg={i % 2 ? "bg.contrast/4" : "transparent"}
                        >
                            <Text
                                w="1/12"
                                py={2}
                                textAlign="center"
                                letterSpacing="1px"
                                fontSize="sm"
                                fontWeight="bold"
                                color="active"
                            >
                                {i + 1}
                            </Text>
                            <NestedDisplay path={path.concat(i.toString())} value={val} />
                        </HStack>
                    ))
                ) : (
                    <Flex direction="column" w="full">
                        {entries.length === 0 && !(mutable && overlay) ? (
                            <Text px="2" py="2" fontSize="xs" color="fg.muted" fontStyle="italic">
                                {t("events.noAttributes")}
                            </Text>
                        ) : (
                            entries.map(([key, val], i) =>
                                !excludedProps.some((str) => str === key) ? (
                                    <HStack
                                        key={key}
                                        w="full"
                                        justify="space-between"
                                        borderBottomWidth={i === entries.length - 1 ? "0" : "1px"}
                                        borderColor="border.muted"
                                    >
                                        <Text
                                            px="2"
                                            py="2"
                                            w="1/3"
                                            h="full"
                                            letterSpacing="1px"
                                            fontSize="2xs"
                                            fontWeight="bold"
                                            color="fg.muted"
                                            textWrap="nowrap"
                                        >
                                            {startCase(key).toUpperCase()}
                                        </Text>
                                        <NestedDisplay path={path.concat(key)} value={val} />
                                    </HStack>
                                ) : null,
                            )
                        )}

                        {mutable && overlay ? (
                            <HStack
                                h="full"
                                w="full"
                                as="form"
                                onSubmit={(e) => {
                                    e.preventDefault();

                                    const data = new FormData(
                                        e.currentTarget as unknown as HTMLFormElement,
                                    );
                                    const name = data.get("name")?.toString();
                                    const formValue = data.get("value")?.toString();

                                    if (name && formValue) {
                                        setField(path, {
                                            ...(value as object),
                                            [name]: formValue,
                                        });
                                    }
                                }}
                            >
                                <Field.Root h="8">
                                    <Input
                                        placeholder={t("events.namePlaceholder")}
                                        aria-label={t("events.namePlaceholder")}
                                        name="name"
                                    />
                                </Field.Root>

                                <Field.Root h="8">
                                    <Input
                                        placeholder={t("events.valuePlaceholder")}
                                        aria-label={t("events.valuePlaceholder")}
                                        name="value"
                                    />
                                </Field.Root>

                                <IconButton
                                    size="2xs"
                                    borderRadius="full"
                                    variant="ghost"
                                    colorPalette="green"
                                    type="submit"
                                    aria-label={t("events.addAttribute")}
                                >
                                    <FaPlus />
                                </IconButton>
                            </HStack>
                        ) : null}
                    </Flex>
                )}
            </VStack>
        );
    }

    return <ValueDisplay path={path} value={value} />;
}
