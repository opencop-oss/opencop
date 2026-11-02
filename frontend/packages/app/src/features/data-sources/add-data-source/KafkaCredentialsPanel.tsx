// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Button, Dialog, Grid, GridItem, HStack, Input, Text } from "@chakra-ui/react";
import type { KafkaCredentials } from "@opencop/api";
import { DialogContainerContext, DialogHeader } from "@opencop/ui";
import { use, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import Logger from "js-logger";

const logger = Logger.get("data-sources");

interface KafkaCredentialsPanelProps {
    credentials: KafkaCredentials;
    onClose: () => void;
}

function CredentialField({
    label,
    value,
    copied,
    onCopy,
}: {
    label: string;
    value: string;
    copied: boolean;
    onCopy: () => void;
}) {
    const { t } = useTranslation();
    return (
        <GridItem>
            <Text fontWeight="bold" mb={1}>
                {label}
            </Text>
            <HStack>
                <Input readOnly value={value} fontFamily="mono" autoComplete="off" />
                <Button size="sm" variant="subtle" onClick={onCopy}>
                    {copied ? t("admin.copied") : t("admin.copy")}
                </Button>
            </HStack>
        </GridItem>
    );
}

export function KafkaCredentialsPanel({ credentials, onClose }: KafkaCredentialsPanelProps) {
    const { t } = useTranslation();
    const [copiedField, setCopiedField] = useState<keyof KafkaCredentials | null>(null);
    const [errorMessage, setErrorMessage] = useState("");
    const dialogContent = use(DialogContainerContext);

    // The panel replaces the wizard, unmounting the button that had focus, and the dialog's focus
    // trap only restores focus when the focused node itself is removed. Focusing the dialog,
    // which its title labels, keeps keyboard users inside and announces the one-time credentials.
    useEffect(() => {
        dialogContent?.current?.focus();
    }, [dialogContent]);

    const copyCredential = async (field: keyof KafkaCredentials, value: string) => {
        // Cleared first, so a repeated failure changes the live region and is announced again.
        setErrorMessage("");
        if (!navigator.clipboard) {
            setErrorMessage(t("admin.clipboardUnavailable"));
            return;
        }

        try {
            await navigator.clipboard.writeText(value);
            setCopiedField(field);
            setTimeout(() => {
                setCopiedField((prev) => (prev === field ? null : prev));
            }, 1_500);
        } catch (err) {
            logger.error(err);
            setErrorMessage(t("admin.copyFailed"));
        }
    };

    const downloadAllCredentials = () => {
        const content = JSON.stringify(credentials, null, 2);
        const blob = new Blob([content], { type: "application/json" });
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.download = "kafka-credentials.json";
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
    };

    const fields: { key: keyof KafkaCredentials; label: string }[] = [
        { key: "topicName", label: t("admin.topicName") },
        { key: "bootstrapServer", label: t("admin.bootstrapServer") },
        { key: "username", label: t("admin.username") },
        { key: "password", label: t("admin.password") },
    ];

    return (
        <>
            <DialogHeader
                title={t("admin.kafkaCredentialsTitle")}
                secondaryText={t("admin.addNewDataSource")}
            />

            <Dialog.Body>
                <Text color="fg.muted" mb={4}>
                    {t("admin.kafkaCredentialsDescription")}
                </Text>

                <Grid templateColumns="repeat(2, 1fr)" gap={4}>
                    {fields.map(({ key, label }) => (
                        <CredentialField
                            key={key}
                            label={label}
                            value={credentials[key]}
                            copied={copiedField === key}
                            onCopy={() => copyCredential(key, credentials[key])}
                        />
                    ))}
                </Grid>

                {/* Mounted before it has anything to say, so a failed copy is announced. */}
                <Box role="alert">
                    {errorMessage && (
                        <Text color="fg.error" fontSize="sm" mt={3}>
                            {errorMessage}
                        </Text>
                    )}
                </Box>
            </Dialog.Body>

            <Dialog.Footer justifyContent="space-between">
                <Button size="sm" variant="outline" onClick={downloadAllCredentials}>
                    {t("admin.downloadAll")}
                </Button>
                <Button size="sm" colorPalette="brand" onClick={onClose}>
                    {t("common.close")}
                </Button>
            </Dialog.Footer>
        </>
    );
}
