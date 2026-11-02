// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Icon, Text, chakra } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { LuArrowRight } from "react-icons/lu";
import type { GroupedIssue } from "./issue-list.util";

interface IssueListProps {
    /** Grouped issues to render */
    items: GroupedIssue[];
    /** Color token for the issue code text (e.g. "fg.error", "fg.warning") */
    codeColor: string;
    /** Message shown when items is empty */
    emptyMessage: string;
    /** Called when a section link is clicked */
    onSectionClick?: (sectionKey: string) => void;
    /** Called when a rule link is clicked */
    onRuleClick?: (ruleId: string) => void;
}

/** Map warning codes to field-level focus targets and labels */
const CODE_FIELD_OVERRIDES: Record<string, { focusKey: string; labelKey: string }> = {
    OBJECT_ID_NOT_MAPPED: {
        focusKey: "field:OBJECT_ID",
        labelKey: "mapperBuilder.field.OBJECT_ID",
    },
    OBJECT_ID_NOT_UNIQUE: {
        focusKey: "field:OBJECT_ID",
        labelKey: "mapperBuilder.field.OBJECT_ID",
    },
};

function sectionLinkLabel(issue: GroupedIssue, t: (key: never) => string): string {
    const override = CODE_FIELD_OVERRIDES[issue.code];
    if (override) return t(override.labelKey as never);
    return t(`mapperBuilder.group.${issue.sectionKey}` as never);
}

function sectionLinkTarget(issue: GroupedIssue): string {
    return CODE_FIELD_OVERRIDES[issue.code]?.focusKey ?? issue.sectionKey ?? "";
}

/** Renders a list of grouped mapping issues (errors or warnings) */
export function IssueList({
    items,
    codeColor,
    emptyMessage,
    onSectionClick,
    onRuleClick,
}: IssueListProps) {
    const { t } = useTranslation();

    if (items.length === 0) {
        return (
            <Text fontSize="xs" color="fg.subtle">
                {emptyMessage}
            </Text>
        );
    }

    return (
        <>
            {items.map((issue) => (
                <Box
                    key={`${issue.code}-${issue.ruleId ?? ""}-${issue.sectionKey ?? ""}`}
                    mb="2"
                    borderRadius="sm"
                    px="1"
                    mx="-1"
                >
                    <Text fontSize="xs">
                        {issue.message}
                        {issue.recordIndices.length > 0 && (
                            <Text as="span" color="fg.subtle">
                                {" "}
                                (
                                {issue.recordIndices.length === 1
                                    ? t("mapperBuilder.recordLabel", {
                                          index: issue.recordIndices[0] + 1,
                                      })
                                    : t("mapperBuilder.inNRecords", {
                                          count: issue.recordIndices.length,
                                      })}
                                )
                            </Text>
                        )}
                    </Text>
                    {issue.ruleId && (
                        <chakra.button
                            type="button"
                            display="flex"
                            alignItems="center"
                            gap="1"
                            cursor="pointer"
                            aria-label={t("mapperBuilder.goToRule" as never)}
                            _hover={{ textDecoration: "underline" }}
                            onClick={() => onRuleClick?.(issue.ruleId!)}
                        >
                            <Icon color={codeColor} boxSize="3">
                                <LuArrowRight />
                            </Icon>
                            <Text fontSize="2xs" color={codeColor}>
                                {t("mapperBuilder.goToRule" as never)}
                            </Text>
                        </chakra.button>
                    )}
                    {!issue.ruleId && issue.sectionKey && (
                        <chakra.button
                            type="button"
                            display="flex"
                            alignItems="center"
                            gap="1"
                            cursor="pointer"
                            aria-label={t("mapperBuilder.goToSection", {
                                section: sectionLinkLabel(issue, t),
                            })}
                            _hover={{ textDecoration: "underline" }}
                            onClick={() => onSectionClick?.(sectionLinkTarget(issue))}
                        >
                            <Icon color={codeColor} boxSize="3">
                                <LuArrowRight />
                            </Icon>
                            <Text fontSize="2xs" color={codeColor}>
                                {sectionLinkLabel(issue, t)}
                            </Text>
                        </chakra.button>
                    )}
                </Box>
            ))}
        </>
    );
}
