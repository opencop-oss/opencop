// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useRef } from "react";
import { Box, HStack, IconButton, Input, Text, VStack } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { LuPlus, LuX } from "react-icons/lu";
import { useEventsEditingStore, useEventsEditingActions } from "../events-editing.store";

const excludedProps = ["userId"];

interface AttributesEditorProps {
    attributes: Record<string, unknown>;
    /** Validation errors from Yup — keys like "attributes.foo" for attribute "foo". */
    attributeErrors: Record<string, string>;
    /** Current value of the "add" row name field. */
    pendingName: string;
    /** Current value of the "add" row value field. */
    pendingValue: string;
    /** Called when the "add" row name changes. */
    onPendingNameChange: (value: string) => void;
    /** Called when the "add" row value changes. */
    onPendingValueChange: (value: string) => void;
    /** Validation error for the pending name field. */
    pendingNameError?: string;
    /** Validation error for the pending value field. */
    pendingValueError?: string;
    /** Called when a pending field is interacted with. */
    onMarkTouched: (field: string) => void;
}

/**
 * Read the latest attributes from the Zustand store at call time.
 * Called from event handlers (not during render) to avoid stale closure
 * issues when multiple mutations happen before React re-renders.
 * Zustand's set() is synchronous, so getState() always reflects
 * prior setField() calls in the same tick.
 */
function readLatestAttributes(propAttributes: Record<string, unknown>): Record<string, unknown> {
    const overlay = useEventsEditingStore.getState().overlay;
    return (overlay?.attributes as Record<string, unknown>) ?? propAttributes;
}

export function AttributesEditor({
    attributes,
    attributeErrors,
    pendingName,
    pendingValue,
    onPendingNameChange,
    onPendingValueChange,
    pendingNameError,
    pendingValueError,
    onMarkTouched,
}: AttributesEditorProps) {
    const { t } = useTranslation();
    const { setField, markTouched, unmarkTouched } = useEventsEditingActions();
    const nameInputRef = useRef<HTMLInputElement>(null);
    const suppressBlurRef = useRef(false);

    const entries = Object.entries(attributes).filter(([key]) => !excludedProps.includes(key));

    const updateAttributes = (next: Record<string, unknown>) => {
        setField(["attributes"], next);
        markTouched("attributes");
    };

    const handleAdd = () => {
        const name = pendingName.trim();
        const value = pendingValue.trim();
        onMarkTouched("pendingAttrName");
        onMarkTouched("pendingAttrValue");
        if (!name || !value) return;
        const current = readLatestAttributes(attributes);
        updateAttributes({ ...current, [name]: value });
        onPendingNameChange("");
        onPendingValueChange("");
        unmarkTouched(["pendingAttrName", "pendingAttrValue"]);
        suppressBlurRef.current = true;
        nameInputRef.current?.focus();
        queueMicrotask(() => {
            suppressBlurRef.current = false;
        });
    };

    // Key rename commits on blur/Enter — renaming on every keystroke would
    // create intermediate key conflicts in the attributes object.
    const handleKeyRename = (oldKey: string, newKey: string) => {
        const trimmed = newKey.trim();
        if (!trimmed || trimmed === oldKey) return;
        const current = readLatestAttributes(attributes);
        const next: Record<string, unknown> = {};
        for (const [k, v] of Object.entries(current)) {
            next[k === oldKey ? trimmed : k] = v;
        }
        updateAttributes(next);
    };

    // Value changes write to the overlay immediately (controlled input).
    // This ensures edits are never lost when the user clicks Save without
    // blurring first.
    const handleValueChange = (key: string, newVal: string) => {
        const current = readLatestAttributes(attributes);
        updateAttributes({ ...current, [key]: newVal });
    };

    const handleDelete = (key: string) => {
        const current = readLatestAttributes(attributes);
        const next = { ...current };
        delete next[key];
        updateAttributes(next);
    };

    return (
        <Box w="full">
            <Text fontWeight="bold" mb="2" color="fg.muted">
                {t("events.attributes")}
            </Text>

            {entries.length > 0 && (
                <VStack w="full" gap="1">
                    {entries.map(([key, value]) => (
                        <HStack key={key} w="full" gap="2">
                            <Input
                                size="sm"
                                w="2/5"
                                defaultValue={key}
                                onBlur={(e) => handleKeyRename(key, e.target.value)}
                                onKeyDown={(e) => {
                                    if (e.key === "Enter") e.currentTarget.blur();
                                }}
                                aria-label={`${t("events.namePlaceholder")}: ${key}`}
                            />
                            <Input
                                size="sm"
                                flex="1"
                                value={String(value ?? "")}
                                onChange={(e) => handleValueChange(key, e.target.value)}
                                onKeyDown={(e) => {
                                    if (e.key === "Enter") e.currentTarget.blur();
                                }}
                                aria-label={`${t("events.valuePlaceholder")}: ${key}`}
                                aria-invalid={!!attributeErrors[`attributes.${key}`] || undefined}
                                borderColor={
                                    attributeErrors[`attributes.${key}`] ? "red.400" : undefined
                                }
                            />
                            <IconButton
                                size="2xs"
                                variant="ghost"
                                onClick={() => handleDelete(key)}
                                aria-label={`${t("common.delete")} ${key}`}
                            >
                                <LuX />
                            </IconButton>
                        </HStack>
                    ))}
                </VStack>
            )}

            <HStack mt="2" gap="2">
                <Input
                    ref={nameInputRef}
                    size="sm"
                    w="2/5"
                    value={pendingName}
                    onChange={(e) => onPendingNameChange(e.target.value)}
                    onBlur={() => {
                        if (!suppressBlurRef.current && (pendingName || pendingValue))
                            onMarkTouched("pendingAttrName");
                    }}
                    onKeyDown={(e) => {
                        if (e.key === "Enter") {
                            e.preventDefault();
                            handleAdd();
                        }
                    }}
                    placeholder={t("events.namePlaceholder")}
                    aria-label={t("events.namePlaceholder")}
                    aria-invalid={!!pendingNameError || undefined}
                    borderColor={pendingNameError ? "red.400" : undefined}
                />
                <Input
                    size="sm"
                    flex="1"
                    value={pendingValue}
                    onChange={(e) => onPendingValueChange(e.target.value)}
                    onBlur={() => {
                        if (!suppressBlurRef.current && (pendingName || pendingValue))
                            onMarkTouched("pendingAttrValue");
                    }}
                    onKeyDown={(e) => {
                        if (e.key === "Enter") {
                            e.preventDefault();
                            handleAdd();
                        }
                    }}
                    placeholder={t("events.valuePlaceholder")}
                    aria-label={t("events.valuePlaceholder")}
                    aria-invalid={!!pendingValueError || undefined}
                    borderColor={pendingValueError ? "red.400" : undefined}
                />
                <IconButton
                    size="2xs"
                    variant="ghost"
                    onClick={handleAdd}
                    aria-label={t("events.addAttribute")}
                >
                    <LuPlus />
                </IconButton>
            </HStack>
        </Box>
    );
}
