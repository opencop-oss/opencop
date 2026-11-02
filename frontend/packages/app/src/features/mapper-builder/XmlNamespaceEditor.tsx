// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Button, Flex, IconButton, Input, Text } from "@chakra-ui/react";
import { OcSwitch, Tooltip } from "@opencop/ui";
import type { KmlFormatConfig, XmlFormatConfig } from "@opencop/api";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { LuPlus, LuX } from "react-icons/lu";

interface NamespaceRow {
    id: number;
    prefix: string;
    uri: string;
}

interface XmlNamespaceEditorProps {
    config: XmlFormatConfig | KmlFormatConfig;
    onStrictChange: (checked: boolean) => void;
    onNamespacesChange: (namespaces: Record<string, string>) => void;
}

export function XmlNamespaceEditor({
    config,
    onStrictChange,
    onNamespacesChange,
}: XmlNamespaceEditorProps) {
    const { t } = useTranslation();

    // Convert Record to array with stable IDs for rendering
    const [rows, setRows] = useState<NamespaceRow[]>(() => {
        let id = 0;
        return Object.entries(config.namespaces).map(([prefix, uri]) => ({
            id: id++,
            prefix,
            uri,
        }));
    });

    // Sync rows → Record when rows change
    function commitRows(updated: NamespaceRow[]) {
        setRows(updated);
        const record: Record<string, string> = {};
        for (const { prefix, uri } of updated) {
            if (prefix) record[prefix] = uri;
        }
        onNamespacesChange(record);
    }

    function nextId() {
        return Math.max(0, ...rows.map((r) => r.id)) + 1;
    }

    function addRow() {
        commitRows([...rows, { id: nextId(), prefix: "", uri: "" }]);
    }

    function removeRow(id: number) {
        commitRows(rows.filter((row) => row.id !== id));
    }

    function updateRow(id: number, field: "prefix" | "uri", value: string) {
        const updated = rows.map((row) => (row.id === id ? { ...row, [field]: value } : row));
        commitRows(updated);
    }

    return (
        <Flex direction="column" gap="3" minW="280px">
            {/* Strict mode */}
            <OcSwitch
                colorPalette="brand"
                rootProps={{ size: "sm", gap: "4" }}
                label={
                    <Text as="span" fontSize="xs" color="fg.muted" whiteSpace="nowrap">
                        {t("mapperBuilder.strictMode")}
                    </Text>
                }
                checked={config.strict}
                onCheckedChange={onStrictChange}
            />

            {/* Namespaces */}
            <Box>
                <Text fontSize="xs" color="fg.muted" mb="2">
                    {t("mapperBuilder.namespaces")}
                </Text>
                {rows.map((row) => (
                    <Flex key={row.id} align="center" gap="1.5" mb="1.5">
                        <Input
                            size="xs"
                            aria-label={t("mapperBuilder.nsPrefix")}
                            placeholder={t("mapperBuilder.nsPrefix")}
                            value={row.prefix}
                            onChange={(e) => updateRow(row.id, "prefix", e.target.value)}
                            w="80px"
                            flexShrink={0}
                        />
                        <Input
                            size="xs"
                            aria-label={t("mapperBuilder.nsUri")}
                            placeholder={t("mapperBuilder.nsUri")}
                            value={row.uri}
                            onChange={(e) => updateRow(row.id, "uri", e.target.value)}
                            flex="1"
                        />
                        <Tooltip content={t("mapperBuilder.removeNamespace")}>
                            <IconButton
                                aria-label={t("mapperBuilder.removeNamespace")}
                                size="2xs"
                                variant="ghost"
                                color="fg.subtle"
                                _hover={{ color: "fg.error" }}
                                onClick={() => removeRow(row.id)}
                            >
                                <LuX />
                            </IconButton>
                        </Tooltip>
                    </Flex>
                ))}
                <Button size="xs" variant="ghost" onClick={addRow}>
                    <LuPlus />
                    {t("mapperBuilder.addNamespace")}
                </Button>
            </Box>
        </Flex>
    );
}
