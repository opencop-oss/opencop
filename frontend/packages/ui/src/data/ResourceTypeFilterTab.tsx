// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, Text } from "@chakra-ui/react";
import type { ReactElement } from "react";
import { Divider } from "../typography/Divider";

export interface ResourceTypeFilterOption<V extends string = string> {
    label: string;
    value: V;
}

export interface ResourceTypeFilterTabProps<V extends string = string> {
    options: ResourceTypeFilterOption<V>[];
    value: V;
    onChange: (value: V) => void;
    "aria-label"?: string;
}

export function ResourceTypeFilterTab<V extends string = string>({
    options,
    value,
    onChange,
    "aria-label": ariaLabel,
}: ResourceTypeFilterTabProps<V>): ReactElement {
    return (
        <Flex role="tablist" aria-label={ariaLabel} align="center" gap={1.5}>
            {options.map((opt, i) => {
                const isActive = value === opt.value;
                return (
                    <Flex key={opt.value} align="center" gap={1.5}>
                        {i > 0 && <Divider orientation="vertical" h="4" />}
                        <Text
                            as="button"
                            role="tab"
                            aria-selected={isActive}
                            tabIndex={isActive ? 0 : -1}
                            fontSize="xs"
                            fontWeight={isActive ? "bold" : "normal"}
                            color={isActive ? "active" : "fg.muted"}
                            cursor="pointer"
                            _hover={{ color: "active", textDecoration: "underline" }}
                            _focusVisible={{
                                outline: "2px solid",
                                outlineColor: "active",
                                outlineOffset: "2px",
                                borderRadius: "sm",
                            }}
                            onClick={() => onChange(opt.value)}
                            onKeyDown={(e) => {
                                let nextIndex: number | null = null;
                                if (e.key === "ArrowRight") {
                                    nextIndex = (i + 1) % options.length;
                                } else if (e.key === "ArrowLeft") {
                                    nextIndex = (i - 1 + options.length) % options.length;
                                } else if (e.key === "Home") {
                                    nextIndex = 0;
                                } else if (e.key === "End") {
                                    nextIndex = options.length - 1;
                                }
                                if (nextIndex !== null) {
                                    e.preventDefault();
                                    const nextEl = (
                                        e.currentTarget.closest('[role="tablist"]') as HTMLElement
                                    )?.querySelectorAll<HTMLElement>('[role="tab"]')[nextIndex];
                                    nextEl?.focus();
                                    nextEl?.click();
                                }
                            }}
                        >
                            {opt.label}
                        </Text>
                    </Flex>
                );
            })}
        </Flex>
    );
}
