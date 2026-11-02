// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Editable, HStack, IconButton, Text, useEditable } from "@chakra-ui/react";
import { DateTime } from "luxon";
import { Divider, Trash } from "@opencop/ui";
import { useAuth } from "~/features/auth/AuthContext";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { eventMutations } from "../events.queries";
import { LuCheck, LuPencilLine, LuX } from "react-icons/lu";
import { useTranslation } from "react-i18next";
import type { Remark } from "@opencop/api";
import { useToast } from "~/features/notifications/use-toast.hook";
import { useEffect } from "react";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";

export function RemarkCard({ remark, sourceId }: { remark: Remark; sourceId: string }) {
    const { t } = useTranslation();
    const { user } = useAuth();
    const timezone = useUserTimezone();
    const client = useQueryClient();
    const { mutateAsync: updateRemark } = useMutation(
        eventMutations.remarks.update(client, sourceId, remark.objectId),
    );
    const { mutateAsync: deleteRemark } = useMutation(
        eventMutations.remarks.delete(client, sourceId, remark.objectId),
    );
    const editable = useEditable({ defaultValue: remark.content });
    const { showToast } = useToast();
    useEffect(() => {
        if (remark.content !== editable.value) editable.setValue(remark.content);
        // useEditable doesn't not memoize editable, so this will happen every render.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [remark]);

    return (
        <Box w="full" h="fit-content" bg="slate.bg" borderWidth="1px" borderRadius="4px" p="4">
            <HStack align="center" justify="space-between">
                <Text h="full" fontSize="sm" fontWeight="bold">
                    {remark.contact?.username}
                </Text>
                <Text fontSize="xs" color="slate.text.muted">
                    {t(remark.updatedAt ? "events.editedAt" : "events.postedAt", {
                        time: DateTime.fromMillis(remark.updatedAt ?? remark.createdAt)
                            .setZone(timezone)
                            .toLocaleString(DateTime.DATETIME_SHORT),
                    })}
                </Text>
            </HStack>
            <Divider mb="2" />

            <Editable.Root
                display="flex"
                justifyContent="end"
                alignItems="end"
                flexDir="column"
                value={editable.value}
                edit={editable.editing}
                disabled={!remark.contact || user?.user.id !== remark.contact.userId}
                activationMode="none"
                onValueChange={(details) => editable.setValue(details.value)}
                onValueCommit={async (details) => {
                    try {
                        await updateRemark({ id: remark.id, content: details.value });
                        showToast({
                            type: "success",
                            title: t("events.editRemarkSuccess"),
                        });
                    } catch {
                        showToast({
                            type: "error",
                            title: t("events.editRemarkFailed"),
                        });
                    }
                }}
            >
                <Editable.Preview w="full" _hover={{ bg: "none" }} />
                <Editable.Textarea
                    w="full"
                    borderWidth={editable.editing ? "2px" : "0px"}
                    outline="none"
                />
                <Editable.Control display="flex" justifyContent="end">
                    {remark.contact &&
                        user?.user.id === remark.contact.userId &&
                        (editable.editing ? (
                            <>
                                <IconButton
                                    variant="surface"
                                    colorPalette="red"
                                    size="xs"
                                    onClick={editable.cancel}
                                >
                                    <LuX />
                                </IconButton>
                                <IconButton
                                    variant="surface"
                                    colorPalette="green"
                                    size="xs"
                                    onClick={editable.submit}
                                >
                                    <LuCheck />
                                </IconButton>
                            </>
                        ) : (
                            <>
                                <IconButton
                                    variant="surface"
                                    size="xs"
                                    borderRadius="2px"
                                    onClick={editable.edit}
                                >
                                    <LuPencilLine />
                                </IconButton>
                                <IconButton
                                    variant="surface"
                                    colorPalette="red"
                                    size="xs"
                                    borderRadius="2px"
                                    onClick={async () => {
                                        try {
                                            await deleteRemark({ id: remark.id });
                                            showToast({
                                                type: "success",
                                                title: t("events.deleteRemarkSuccess"),
                                            });
                                        } catch {
                                            showToast({
                                                type: "error",
                                                title: t("events.deleteRemarkFailed"),
                                            });
                                        }
                                    }}
                                >
                                    <Trash />
                                </IconButton>
                            </>
                        ))}
                </Editable.Control>
            </Editable.Root>
        </Box>
    );
}
