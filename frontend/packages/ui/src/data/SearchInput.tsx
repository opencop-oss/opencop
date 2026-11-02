// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, Box, Input } from "@chakra-ui/react";
import { FaMagnifyingGlass } from "react-icons/fa6";
import { useTranslation } from "react-i18next";

interface SearchInputProps {
    value: string;
    onChange: (value: string) => void;
    placeholder?: string;
    size?: "xs" | "sm" | "md";
    /** Fill the parent's width instead of a fixed size; bound it with a wrapping `Box` if needed. */
    fill?: boolean;
    /**
     * Hard cap on typed/pasted length so absurd input never reaches consumers. Defaults to 100,
     * mirroring `MAX_SEARCH_LENGTH` in `@opencop/api` (the server's search cap) — kept in sync by
     * hand since `@opencop/ui` is a leaf package and can't import the data plane.
     */
    maxLength?: number;
}

export function SearchInput({
    value,
    onChange,
    placeholder,
    size = "md",
    fill = false,
    maxLength = 100,
}: SearchInputProps) {
    const { t } = useTranslation();
    const isXs = size === "xs";
    const compact = size === "sm" || isXs;
    const normalWidth = isXs ? "180px" : compact ? "240px" : "280px";

    return (
        <Flex
            width={fill ? "100%" : normalWidth}
            height={isXs ? "26px" : compact ? "32px" : "40px"}
            position="relative"
            alignItems="center"
            borderWidth={!compact ? "1px" : undefined}
            borderColor={!compact ? "border.muted" : undefined}
            bg={!compact ? "bg.glass" : undefined}
            _focusWithin={
                !compact
                    ? {
                          borderColor: "active",
                          boxShadow: "0 0 0 1px var(--chakra-colors-active)",
                          "& [data-search-icon]": {
                              filter: "drop-shadow(0 0 6px var(--chakra-colors-active))",
                              color: "active",
                          },
                      }
                    : undefined
            }
            transition={!compact ? "border-color 0.2s, box-shadow 0.2s" : undefined}
        >
            <Box
                position="absolute"
                left={isXs ? 2 : 3}
                display="flex"
                alignItems="center"
                pointerEvents="none"
                color="fg.muted"
                zIndex={1}
                transition={!compact ? "filter 0.2s, color 0.2s" : undefined}
                data-search-icon=""
            >
                <FaMagnifyingGlass
                    size={isXs ? 10 : compact ? 12 : 14}
                    style={{ color: "currentColor" }}
                />
            </Box>
            <Input
                flex={1}
                height="100%"
                pl={isXs ? "22px" : "32px"}
                pr={isXs ? 1 : 3}
                fontSize={isXs ? "xs" : compact ? "xs" : "sm"}
                bg={compact ? "bg.glass" : "transparent"}
                border={compact ? undefined : "none"}
                _focus={compact ? undefined : { outline: "none" }}
                placeholder={placeholder ?? t("common.search")}
                aria-label={placeholder ?? t("common.search")}
                value={value}
                maxLength={maxLength}
                onChange={(e) => onChange(e.target.value)}
            />
        </Flex>
    );
}
