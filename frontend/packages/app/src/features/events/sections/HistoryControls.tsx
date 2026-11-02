// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo } from "react";
import { Box, Button, Flex, Input, Text, VStack } from "@chakra-ui/react";
import { Field as FormikField, Formik, Form, type FieldProps } from "formik";
import * as Yup from "yup";
import { useTranslation } from "react-i18next";
import { AccentHeading, OcSelect } from "@opencop/ui";
import { toMilliseconds } from "~/date.util";

interface HistoryControlsProps {
    maxPoints: number;
    onApply: (from: string, to: string, maxPoints: number) => void;
}

type TimeRange = "10min" | "1hour" | "1day" | "1week" | "1month";

function calculateDates(range: TimeRange): { fromDate: string; toDate: string } {
    const now = new Date();
    const toDate = now.toISOString();

    const offsetMap: Record<TimeRange, Parameters<typeof toMilliseconds>[0]> = {
        "10min": { minutes: 10 },
        "1hour": { hours: 1 },
        "1day": { days: 1 },
        "1week": { days: 7 },
        "1month": { days: 30 },
    };

    const fromDate = new Date(now.getTime() - toMilliseconds(offsetMap[range]));
    return { fromDate: fromDate.toISOString(), toDate };
}

export function HistoryControls({ maxPoints, onApply }: HistoryControlsProps) {
    const { t } = useTranslation();

    const timeRangeOptions = useMemo(
        () => [
            { value: "10min", label: t("timeline.last10Minutes") },
            { value: "1hour", label: t("timeline.lastHour") },
            { value: "1day", label: t("timeline.lastDay") },
            { value: "1week", label: t("timeline.lastWeek") },
            { value: "1month", label: t("timeline.lastMonth") },
        ],
        [t],
    );

    const validationSchema = useMemo(
        () =>
            Yup.object({
                timeRange: Yup.string().required(t("timeline.required")),
                maxPoints: Yup.number()
                    .typeError(t("timeline.mustBeNumber"))
                    .required(t("timeline.required"))
                    .integer(t("timeline.mustBeWholeNumber"))
                    .min(1, t("timeline.mustBeAtLeast1"))
                    .max(10_000, t("timeline.cannotExceed10000")),
            }),
        [t],
    );

    return (
        <VStack gap={2} align="stretch">
            <AccentHeading>{t("timeline.settings")}</AccentHeading>
            <Formik
                initialValues={{
                    timeRange: "1day",
                    maxPoints: maxPoints.toString(),
                }}
                validationSchema={validationSchema}
                onSubmit={(values) => {
                    const { fromDate, toDate } = calculateDates(values.timeRange as TimeRange);
                    onApply(fromDate, toDate, parseInt(values.maxPoints, 10));
                }}
            >
                {({ values, setFieldValue }) => (
                    <Form>
                        <Flex gap={2} align="flex-end">
                            <Box flex={1} mx="-2px" overflow="visible">
                                <Box px="2px">
                                    <Text fontWeight="bold" fontSize="xs" mb="2">
                                        {t("timeline.timeRange")}
                                    </Text>
                                    <OcSelect
                                        items={timeRangeOptions}
                                        value={values.timeRange}
                                        onValueChange={(value) => setFieldValue("timeRange", value)}
                                        placeholder={t("timeline.selectTimeRange")}
                                        size="sm"
                                    />
                                </Box>
                            </Box>
                            <Box flex={1}>
                                <FormikField name="maxPoints">
                                    {({ field, meta }: FieldProps<string>) => (
                                        <>
                                            <Text fontWeight="bold" fontSize="xs" mb="2">
                                                {t("timeline.maxPoints")}
                                            </Text>
                                            <Input
                                                {...field}
                                                size="sm"
                                                aria-label={t("timeline.maxPoints")}
                                                aria-describedby={
                                                    meta.touched && meta.error
                                                        ? "maxPoints-error"
                                                        : undefined
                                                }
                                            />
                                            {meta.touched && meta.error && (
                                                <Text
                                                    id="maxPoints-error"
                                                    color="red.emphasized"
                                                    fontSize="xs"
                                                    mt={1}
                                                    role="alert"
                                                    aria-live="assertive"
                                                >
                                                    {meta.error}
                                                </Text>
                                            )}
                                        </>
                                    )}
                                </FormikField>
                            </Box>
                            <Button type="submit" size="sm" variant="solid" colorPalette="brand">
                                {t("timeline.applyFilters")}
                            </Button>
                        </Flex>
                    </Form>
                )}
            </Formik>
        </VStack>
    );
}
