// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box } from "@chakra-ui/react";
import { Trans, useTranslation } from "react-i18next";
import type { PredicateSummaryValue } from "./predicate-summary.util";

export type PredicateSummaryProps = {
    summary: PredicateSummaryValue;
};

export function PredicateSummary({ summary }: PredicateSummaryProps) {
    const { t } = useTranslation();
    return (
        <>
            <Trans
                i18nKey={summary.key}
                values={summary.values}
                components={{
                    // field/operator are plain-colored text, not tied to the
                    // app's actionable (`active`/cyan) or error (`red`) hues.
                    field: <Box as="strong" color="blue.400" fontWeight="semibold" />,
                    // Negation shares the operator's color (it's part of the same
                    // "verb phrase" token, not a separate warning) — italic is the
                    // only thing that still sets it apart.
                    operator: <Box as="span" color="orange.400" fontWeight="medium" />,
                    negation: (
                        <Box as="em" color="orange.400" fontStyle="italic" fontWeight="medium" />
                    ),
                    // The one span that keeps a background: it's an exact literal
                    // string being compared, and inline-code styling is the
                    // standard convention for verbatim text — distinct from
                    // field/operator, which are just categorical words in the
                    // sentence.
                    literal: (
                        <Box
                            as="code"
                            bg="bg.subtle"
                            borderRadius="sm"
                            fontFamily="mono"
                            fontSize="0.9em"
                            px="1"
                        />
                    ),
                }}
            />
            {summary.unavailable && (
                <Box as="span" color="fg.muted" fontStyle="italic">
                    {" — "}
                    {t("filter.unavailableCondition")}
                </Box>
            )}
        </>
    );
}
