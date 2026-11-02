// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Field, Input, Stack } from "@chakra-ui/react";
import type {
    AccessMode,
    ConnectionState,
    DataSourceInfo,
    DataSourcePatch,
    MyDataSource,
} from "@opencop/api";
import { zodResolver } from "@hookform/resolvers/zod";
import { DefaultCheckbox } from "@opencop/ui";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { ReactElement } from "react";
import { Controller, useForm, type SubmitHandler } from "react-hook-form";
import { useTranslation } from "react-i18next";
import z from "zod";
import { DrawerEditActions } from "~/features/drawers/DrawerEditActions";
import { useDrawerBusy } from "~/features/drawers/DrawerContext";
import { ConnectionStatusIndicator } from "~/features/websocket/ConnectionStatusIndicator";
import { DataSourceAccessModeField } from "../DataSourceAccessModeField";
import { getDataSourceMetadataErrorMessage } from "../data-source-errors";
import { dataSourceMutations } from "../data-sources.queries";

interface MyDataSourceEditContentProps {
    dataSource: MyDataSource;
    connection?: DataSourceInfo;
    /** Name of the mapper the connection decodes with, shown read-only. */
    mapperName?: string;
    /** Name of the mapper the connection writes with, shown read-only. */
    egressMapperName?: string;
    /** Called on successful save or cancel — returns the drawer to view mode. */
    onDone: () => void;
}

const editMyDataSourceSchema = z.object({
    name: z.string().trim().min(1),
    access: z.enum(["R", "W", "RW"] satisfies AccessMode[]),
    defaultVisible: z.boolean(),
});

type EditMyDataSourceForm = z.infer<typeof editMyDataSourceSchema>;

export function MyDataSourceEditContent({
    dataSource,
    connection,
    mapperName,
    egressMapperName,
    onDone,
}: MyDataSourceEditContentProps): ReactElement {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const { mutate: update, isPending } = useMutation(dataSourceMutations.update(queryClient));
    // The result callbacks below are observer-bound, so the drawer must stay open until the PATCH
    // settles or a failure loses its only place to be reported.
    useDrawerBusy(isPending);
    const { control, register, handleSubmit, setError, formState } = useForm<EditMyDataSourceForm>({
        resolver: zodResolver(editMyDataSourceSchema),
        defaultValues: {
            name: connection?.name ?? dataSource.serverName,
            access: connection?.access ?? dataSource.access,
            defaultVisible: connection?.defaultVisible ?? dataSource.defaultVisible,
        },
    });
    const transportType = connection?.transportType ?? dataSource.transportType;
    const hasSubmittableChanges =
        formState.dirtyFields.name ||
        formState.dirtyFields.defaultVisible ||
        formState.dirtyFields.access;

    const onSubmit: SubmitHandler<EditMyDataSourceForm> = (data) => {
        const patch: DataSourcePatch = {
            ...(formState.dirtyFields.name && { name: data.name }),
            ...(formState.dirtyFields.access && { access: data.access }),
            ...(formState.dirtyFields.defaultVisible && {
                defaultVisible: data.defaultVisible,
            }),
        };
        if (Object.keys(patch).length === 0) return;

        update(
            { id: dataSource.serverId, data: patch },
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
                            persistedValue={connection?.access ?? dataSource.access}
                            transportType={transportType}
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
                    <ConnectionStatusIndicator
                        state={(connection?.state ?? dataSource.serverState) as ConnectionState}
                    />
                </Field.Root>

                <Field.Root readOnly>
                    <Field.Label>{t("admin.protocol")}</Field.Label>
                    <Input value={transportType} readOnly />
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

                <Field.Root readOnly>
                    <Field.Label>{t("dataSources.role")}</Field.Label>
                    <Input value={dataSource.role} readOnly />
                </Field.Root>

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
