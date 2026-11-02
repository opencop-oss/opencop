// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Field, Input, Stack } from "@chakra-ui/react";
import type { AccessMode, ConnectionState, DataSourceInfo, DataSourcePatch } from "@opencop/api";
import { zodResolver } from "@hookform/resolvers/zod";
import { DefaultCheckbox } from "@opencop/ui";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { ReactElement } from "react";
import { Controller, useForm, type SubmitHandler } from "react-hook-form";
import { useTranslation } from "react-i18next";
import z from "zod";
import { formatRelativeTime } from "~/date.util";
import { DrawerEditActions } from "~/features/drawers/DrawerEditActions";
import { useDrawerBusy } from "~/features/drawers/DrawerContext";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import { ConnectionStatusIndicator } from "~/features/websocket/ConnectionStatusIndicator";
import { DataSourceAccessModeField } from "../DataSourceAccessModeField";
import { getDataSourceMetadataErrorMessage } from "../data-source-errors";
import { dataSourceMutations } from "../data-sources.queries";

interface DataSourceEditContentProps {
    dataSource: DataSourceInfo;
    /** Name of the mapper the connection decodes with, shown read-only. */
    mapperName?: string;
    /** Name of the mapper the connection writes with, shown read-only. */
    egressMapperName?: string;
    /** Called on successful save or cancel — returns the drawer to view mode. */
    onDone: () => void;
}

const editDataSourceSchema = z.object({
    name: z.string().trim().min(1),
    access: z.enum(["R", "W", "RW"] satisfies AccessMode[]),
    defaultVisible: z.boolean(),
});

type EditDataSourceForm = z.infer<typeof editDataSourceSchema>;

export function DataSourceEditContent({
    dataSource,
    mapperName,
    egressMapperName,
    onDone,
}: DataSourceEditContentProps): ReactElement {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const timezone = useUserTimezone();
    const { mutate: update, isPending } = useMutation(dataSourceMutations.update(queryClient));
    // The result callbacks below are observer-bound, so the drawer must stay open until the PATCH
    // settles or a failure loses its only place to be reported.
    useDrawerBusy(isPending);
    const { control, register, handleSubmit, setError, formState } = useForm<EditDataSourceForm>({
        resolver: zodResolver(editDataSourceSchema),
        defaultValues: {
            name: dataSource.name,
            access: dataSource.access,
            defaultVisible: dataSource.defaultVisible,
        },
    });

    const hasSubmittableChanges =
        formState.dirtyFields.name ||
        formState.dirtyFields.defaultVisible ||
        formState.dirtyFields.access;

    const onSubmit: SubmitHandler<EditDataSourceForm> = (data) => {
        const patch: DataSourcePatch = {
            ...(formState.dirtyFields.name && { name: data.name }),
            ...(formState.dirtyFields.access && { access: data.access }),
            ...(formState.dirtyFields.defaultVisible && {
                defaultVisible: data.defaultVisible,
            }),
        };
        if (Object.keys(patch).length === 0) return;

        update(
            { id: dataSource.id, data: patch },
            {
                onSuccess: onDone,
                onError: (error) => {
                    const message = getDataSourceMetadataErrorMessage(error, t);
                    if (message) setError("root", { message });
                },
            },
        );
    };

    return (
        <form noValidate onSubmit={handleSubmit(onSubmit)}>
            <Stack gap={4}>
                <Field.Root required invalid={!!formState.errors.name}>
                    <Field.Label>
                        {t("common.name")}
                        <Field.RequiredIndicator />
                    </Field.Label>
                    <Input {...register("name")} placeholder={t("admin.enterServerName")} />
                    <Field.ErrorText>{t("common.fieldRequired")}</Field.ErrorText>
                </Field.Root>

                <Controller
                    control={control}
                    name="access"
                    render={({ field }) => (
                        <DataSourceAccessModeField
                            value={field.value}
                            persistedValue={dataSource.access}
                            transportType={dataSource.transportType}
                            onChange={field.onChange}
                        />
                    )}
                />

                <Controller
                    control={control}
                    name="defaultVisible"
                    render={({ field }) => (
                        <DefaultCheckbox
                            checked={field.value}
                            label={t("common.defaultVisible")}
                            onCheckedChange={field.onChange}
                        />
                    )}
                />

                <Field.Root>
                    <Field.Label>{t("common.status")}</Field.Label>
                    <ConnectionStatusIndicator state={dataSource.state as ConnectionState} />
                </Field.Root>

                <Field.Root readOnly>
                    <Field.Label>{t("admin.protocol")}</Field.Label>
                    <Input value={dataSource.transportType} readOnly />
                </Field.Root>

                <Field.Root readOnly>
                    <Field.Label>{t("admin.securityType")}</Field.Label>
                    <Input value={dataSource.securityType ?? "-"} readOnly />
                </Field.Root>

                <Field.Root readOnly>
                    <Field.Label>{t("resourceTable.lastTelemetry")}</Field.Label>
                    <Input
                        value={
                            dataSource.lastMessageAt
                                ? formatRelativeTime(new Date(dataSource.lastMessageAt), timezone)
                                : "-"
                        }
                        readOnly
                    />
                </Field.Root>

                {mapperName && (
                    <Field.Root readOnly>
                        <Field.Label>{t("admin.mapper")}</Field.Label>
                        <Input value={mapperName} readOnly />
                    </Field.Root>
                )}

                {egressMapperName && (
                    <Field.Root readOnly>
                        <Field.Label>{t("connectionSetup.egressMapper")}</Field.Label>
                        <Input value={egressMapperName} readOnly />
                    </Field.Root>
                )}

                {formState.errors.root && (
                    <Field.Root invalid>
                        <Field.ErrorText>{formState.errors.root.message}</Field.ErrorText>
                    </Field.Root>
                )}

                <DrawerEditActions
                    onCancel={onDone}
                    loading={isPending}
                    canSave={!!hasSubmittableChanges}
                />
            </Stack>
        </form>
    );
}
