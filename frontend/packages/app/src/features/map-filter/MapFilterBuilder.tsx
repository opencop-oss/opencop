// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useRef, useState } from "react";
import {
    FilterRulesBuilder,
    type FilterRulesBuilderHandle,
    type FilterDraft,
    type FilterField,
} from "@opencop/ui";
import { Button, Field, Input, VStack, HStack } from "@chakra-ui/react";
import { useMapFilterStore, type MapFilter } from "./map-filter.store";
import { canCompilePath } from "./compiled-filter.model";
import { useTranslation } from "react-i18next";

const MIN_FILTER_NAME_LENGTH = 3;
const MAX_FILTER_NAME_LENGTH = 64;

export type MapFilterBuilderProps = {
    defaultValue: FilterDraft;
    onSave: (filter: MapFilter) => void;
    onBack: () => void;
};

/** A map filter offers only the fields whose property the map can evaluate. */
const offerCompilable = (field: FilterField) => canCompilePath(field.path);

type MapFilterError = "name-too-long" | "name-too-short" | "name-already-exists";

const messageKey = {
    "name-too-long": "filter.errors.name-too-long",
    "name-too-short": "filter.errors.name-too-short",
    "name-already-exists": "filter.errors.name-already-exists",
} as const satisfies Record<MapFilterError, string>;

export function MapFilterBuilder({ defaultValue, onSave, onBack }: MapFilterBuilderProps) {
    const { t } = useTranslation();
    const handleRef = useRef<FilterRulesBuilderHandle>(null);
    const [name, setName] = useState("");
    const [error, setError] = useState<MapFilterError | null>(null);
    const [isUnsavable, setIsUnsavable] = useState(false);
    const filters = useMapFilterStore((s) => s.filters);

    const handleSave = () => {
        const res = handleRef.current?.attempt();
        if (!res?.ok) return;
        const trimmed = name.trim();
        if (trimmed.length > MAX_FILTER_NAME_LENGTH) return setError("name-too-long");
        if (trimmed.length < MIN_FILTER_NAME_LENGTH) return setError("name-too-short");
        if (filters.some((f) => f.name === trimmed)) return setError("name-already-exists");
        onSave({ ...res.result, name: trimmed });
    };

    return (
        <VStack w="full" h="full">
            <HStack justify="start" w="full">
                <Button onClick={onBack} size="xs" variant="ghost">
                    {t("common.back")}
                </Button>
            </HStack>
            <Field.Root invalid={!!error}>
                <Field.Label>{t("filter.name")}</Field.Label>
                <Input
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder={t("filter.namePlaceholder")}
                />
                <Field.ErrorText>{error && t(messageKey[error])}</Field.ErrorText>
            </Field.Root>
            <FilterRulesBuilder
                defaultValue={defaultValue}
                ref={handleRef}
                offerField={offerCompilable}
                allowCustomFields={false}
                onValidityChange={setIsUnsavable}
            />
            <HStack w="full" justify="end">
                <Button bg="active" onClick={handleSave} disabled={isUnsavable}>
                    {t("common.save")}
                </Button>
            </HStack>
        </VStack>
    );
}
