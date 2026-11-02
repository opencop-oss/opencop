// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { SegmentGroup } from "@chakra-ui/react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import type { FilterCombinator } from "./predicate.model";

export type CombinatorSelectProps = {
    value: FilterCombinator;
    onChange?: (value: FilterCombinator) => void;
    disabled?: boolean;
    /** The id of the text that explains the control, such as why it is disabled. */
    describedBy?: string;
};

export function CombinatorSelect({
    value,
    onChange,
    disabled,
    describedBy,
}: CombinatorSelectProps) {
    const { t } = useTranslation();

    const options = useMemo(
        () => [
            { label: t("filter.all"), value: "ALL" },
            { label: t("filter.any"), value: "ANY" },
        ],
        [t],
    );

    return (
        <SegmentGroup.Root
            value={value}
            disabled={disabled}
            aria-label={t("filter.combinatorPlaceholder")}
            aria-describedby={describedBy}
            onValueChange={
                onChange
                    ? (details) => {
                          if (details.value) onChange(details.value as FilterCombinator);
                      }
                    : undefined
            }
            size="sm"
            h="8"
            w="auto"
            p="1"
            bg="bg"
            borderWidth="1px"
            borderColor="slate.border.primary"
            borderRadius="2px"
            overflow="hidden"
            boxShadow="none"
        >
            <SegmentGroup.Indicator
                bg="slate.elevated"
                border="none"
                borderRadius="0"
                boxShadow="none"
                outline="none"
            />
            <SegmentGroup.Items
                items={options}
                w="auto"
                h="full"
                minH="0"
                px="3"
                py="0"
                color="fg.muted"
                _checked={{ color: "fg" }}
                fontSize="xs"
                justifyContent="center"
                transition="all .1s ease-out"
            />
        </SegmentGroup.Root>
    );
}
