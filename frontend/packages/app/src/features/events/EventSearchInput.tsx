// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { IconButton, Input, InputGroup } from "@chakra-ui/react";
import { useRef } from "react";
import { useTranslation } from "react-i18next";
import { MdClose, MdSearch } from "react-icons/md";

interface EventSearchInputProps {
    query: string;
    onQueryChange: (query: string) => void;
}

export function EventSearchInput({ query, onQueryChange }: EventSearchInputProps) {
    const { t } = useTranslation();
    const inputRef = useRef<HTMLInputElement>(null);

    const handleClear = () => {
        onQueryChange("");
        // The clear button steals focus on click; return it to the input so
        // keyboard/screen-reader users can keep typing without re-targeting it.
        inputRef.current?.focus();
    };

    return (
        <InputGroup
            role="search"
            startElement={<MdSearch />}
            endElement={
                query ? (
                    <IconButton
                        type="button"
                        aria-label={t("events.clearSearch")}
                        size="2xs"
                        variant="ghost"
                        onClick={handleClear}
                    >
                        <MdClose />
                    </IconButton>
                ) : undefined
            }
        >
            <Input
                ref={inputRef}
                type="search"
                aria-label={t("events.searchLabel")}
                placeholder={t("events.searchPlaceholder")}
                value={query}
                onChange={(event) => onQueryChange(event.target.value)}
            />
        </InputGroup>
    );
}
