// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MappingResponse } from "@opencop/api";
import { useMutation } from "@tanstack/react-query";
import { useEffect, useRef } from "react";
import { useMapperBuilderActions, useMapperBuilderStore } from "./mapper-builder.store";
import { mapperBuilderMutations } from "./mapper-builder.queries";
import {
    enrichErrors,
    enrichWarnings,
    fileToGzipBase64,
    isCompleteRule,
    partitionIssues,
} from "./mapper-builder.util";

/**
 * Manages the debounced preview lifecycle:
 * - Fires a preview request 500ms after rules/config/selector/file change
 * - Writes errorSectionKeys into the store after each response
 */
export function useMapperPreview() {
    const formatConfig = useMapperBuilderStore((s) => s.formatConfig);
    const recordSelector = useMapperBuilderStore((s) => s.recordSelector);
    const rules = useMapperBuilderStore((s) => s.rules);
    const sampleFile = useMapperBuilderStore((s) => s.sampleFile);
    const recordSelectorValid = useMapperBuilderStore((s) => s.recordSelectorValid);
    const { reportErrorSections, reportWarningSections, reportRuleErrors, reportRuleWarnings } =
        useMapperBuilderActions();

    const { mutateAsync, reset, isSuccess, isError, error, data } = useMutation(
        mapperBuilderMutations.preview(),
    );
    const abortRef = useRef<AbortController | null>(null);
    const debounceRef = useRef<ReturnType<typeof setTimeout>>(null);

    // Note: ruleErrors (from preview responses) is intentionally excluded here.
    // Including it would create a feedback loop: preview returns errors → ruleErrors
    // populated → isPreviewReady flips false → clears errors → flips true → repeat.
    const isPreviewReady =
        formatConfig !== null &&
        recordSelector !== null &&
        rules.length > 0 &&
        sampleFile !== null &&
        recordSelectorValid;

    useEffect(() => {
        if (!isPreviewReady || !formatConfig || !recordSelector || !sampleFile) {
            reset();
            reportRuleErrors({});
            reportRuleWarnings({});
            reportErrorSections([]);
            reportWarningSections([]);
            return;
        }

        if (debounceRef.current) clearTimeout(debounceRef.current);
        abortRef.current?.abort();

        debounceRef.current = setTimeout(async () => {
            const controller = new AbortController();
            abortRef.current = controller;

            try {
                const payloadBytes = await fileToGzipBase64(sampleFile);
                if (controller.signal.aborted) return;
                const previewRules = rules.filter(isCompleteRule);
                const result = await mutateAsync({
                    profile: { enabled: true, formatConfig, recordSelector, rules: previewRules },
                    payloadBytes,
                });
                if (controller.signal.aborted) return;

                const allErrors = enrichErrors(result.errors);
                const allWarnings = enrichWarnings([
                    ...result.warnings,
                    ...result.records.flatMap((r) => r.warnings),
                ]);

                const { ruleMap: ruleErrorMap, sectionKeys: errorKeys } =
                    partitionIssues(allErrors);
                const { ruleMap: ruleWarningMap, sectionKeys: warningKeys } =
                    partitionIssues(allWarnings);

                reportRuleErrors(ruleErrorMap);
                reportRuleWarnings(ruleWarningMap);
                reportErrorSections(errorKeys);
                reportWarningSections(warningKeys);
            } catch {
                if (!controller.signal.aborted) {
                    // Error state is tracked by the mutation (isError / error).
                    reportRuleErrors({});
                    reportRuleWarnings({});
                    reportErrorSections([]);
                    reportWarningSections([]);
                }
            }
        }, 500);

        return () => {
            if (debounceRef.current) clearTimeout(debounceRef.current);
            abortRef.current?.abort();
        };
    }, [
        rules,
        formatConfig,
        recordSelector,
        sampleFile,
        isPreviewReady,
        mutateAsync,
        reset,
        reportRuleErrors,
        reportRuleWarnings,
        reportErrorSections,
        reportWarningSections,
    ]);

    // Derive preview response from mutation state
    const previewResponse: MappingResponse | null = isPreviewReady && isSuccess ? data : null;
    const previewError = isPreviewReady && isError ? error : null;

    return { previewResponse, previewError };
}
