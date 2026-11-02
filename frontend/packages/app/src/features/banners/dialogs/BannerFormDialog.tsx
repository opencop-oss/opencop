// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    Field,
    Flex,
    Input,
    NumberInput,
    Stack,
    Text,
    Textarea,
    IconButton,
} from "@chakra-ui/react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { FaTrash } from "react-icons/fa";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { DateTime } from "luxon";
import { FormDialog, OcDateTimePicker, OcSelect, OcSwatch, OcSwitch } from "@opencop/ui";
import { BANNER_NO_EXPIRY_DATE } from "@opencop/api";
import type {
    Banner,
    BannerFormat,
    BannerKind,
    BannerPatch,
    BannerTranslation,
} from "@opencop/api";
import { bannerMutations } from "~/features/banners/banners.queries";
import { toHex, validateContent } from "~/features/banners/banner.util";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";

interface TranslationEntry {
    id?: string;
    locale: string;
    content: string;
    originalContent: string;
    isNew: boolean;
}

interface FormState {
    name: string;
    kind: BannerKind | "";
    format: BannerFormat | "";
    priority: number;
    color: string;
    textColor: string;
    linkUrl: string;
    enabled: boolean;
    dismissible: boolean;
    startsAt: DateTime;
    endsAt: DateTime;
    noExpiry: boolean;
}

function getInitialForm(banner?: Banner): FormState {
    if (banner) {
        return {
            name: banner.name,
            kind: banner.kind,
            format: banner.format,
            priority: banner.priority,
            color: banner.color,
            textColor: banner.textColor ?? "#ffffff",
            linkUrl: banner.linkUrl ?? "",
            enabled: banner.enabled,
            dismissible: banner.dismissible,
            startsAt: DateTime.fromISO(banner.startsAt),
            endsAt:
                banner.endsAt === BANNER_NO_EXPIRY_DATE
                    ? DateTime.now()
                    : DateTime.fromISO(banner.endsAt),
            noExpiry: banner.endsAt === BANNER_NO_EXPIRY_DATE,
        };
    }
    const now = DateTime.now();
    return {
        name: "",
        kind: "",
        format: "",
        priority: 5,
        color: "#1a73e8",
        textColor: "#ffffff",
        linkUrl: "",
        enabled: true,
        dismissible: false,
        startsAt: now,
        endsAt: now.plus({ months: 1 }),
        noExpiry: false,
    };
}

function getInitialTranslations(serverTranslations?: BannerTranslation[]): TranslationEntry[] {
    if (serverTranslations) {
        return serverTranslations.map((tr) => ({
            id: tr.id,
            locale: tr.locale,
            content: tr.content,
            originalContent: tr.content,
            isNew: false,
        }));
    }
    return [{ locale: "en", content: "", originalContent: "", isNew: true }];
}

export interface BannerFormDialogProps {
    onClose: () => void;
    /** When provided, the dialog operates in edit mode. */
    banner?: Banner;
    /** Server translations for edit mode. */
    serverTranslations?: BannerTranslation[];
}

export function BannerFormDialog({ onClose, banner, serverTranslations }: BannerFormDialogProps) {
    const isEdit = !!banner;
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const timezone = useUserTimezone();

    const createBannerMutation = useMutation(bannerMutations.create(queryClient));
    const updateMutation = useMutation(bannerMutations.update(queryClient));
    const createTranslationMutation = useMutation(bannerMutations.createTranslation(queryClient));
    const patchTranslationMutation = useMutation(bannerMutations.patchTranslation(queryClient));
    const deleteTranslationMutation = useMutation(bannerMutations.deleteTranslation(queryClient));

    const [form, setForm] = useState<FormState>(() => getInitialForm(banner));
    const [translationEntries, setTranslationEntries] = useState<TranslationEntry[]>(() =>
        getInitialTranslations(serverTranslations),
    );
    const [submitError, setSubmitError] = useState<string | null>(null);
    const [attempted, setAttempted] = useState(false);
    const dateMin = useMemo(() => DateTime.now().minus({ years: 10 }), []);
    const dateMax = useMemo(() => DateTime.now().plus({ years: 10 }), []);

    const kindItems = useMemo(
        () =>
            (["HEADER", "FOOTER", "MODAL", "SYSTEM"] as const).map((v) => ({
                value: v,
                label: t(`adminBanners.kinds.${v.toLowerCase()}` as never),
                // MODAL is shown for context but intentionally not selectable when creating a banner.
                disabled: v === "MODAL",
            })),
        [t],
    );

    const formatItems = useMemo(
        () =>
            (["PLAIN", "MARKDOWN", "HTML"] as const).map((v) => ({
                value: v,
                label: t(`adminBanners.formats.${v.toLowerCase()}` as never),
            })),
        [t],
    );

    const optionalLocaleItems = useMemo(
        () =>
            (["fr", "zh-Hant-TW"] as const).map((v) => ({
                value: v,
                label: t(`adminBanners.locales.${v}` as never),
            })),
        [t],
    );

    const englishEntry = translationEntries.find((tr) => tr.locale === "en");
    const otherEntries = translationEntries.filter((tr) => tr.locale !== "en");

    const priorityError =
        form.priority < 1 || form.priority > 10
            ? t("adminBanners.validation.priorityRange")
            : undefined;
    const dateError =
        !form.noExpiry && form.startsAt >= form.endsAt
            ? t("adminBanners.validation.dateRange")
            : undefined;
    const englishContentErrorKey = validateContent(englishEntry?.content ?? "", form.format);
    const englishContentError = englishContentErrorKey ? t(englishContentErrorKey) : undefined;
    const translationErrors: Record<string, string | undefined> = Object.fromEntries(
        otherEntries.map((tr) => {
            const key = validateContent(tr.content, form.format);
            return [tr.locale, key ? t(key) : undefined];
        }),
    );

    const linkUrlError =
        form.linkUrl.trim() !== "" && !/^https?:\/\//.test(form.linkUrl.trim())
            ? t("adminBanners.validation.invalidUrl")
            : undefined;

    const canSubmit =
        form.name.trim() !== "" &&
        form.kind !== "" &&
        form.format !== "" &&
        (englishEntry?.content.trim() ?? "") !== "" &&
        !priorityError &&
        !dateError &&
        !englishContentError &&
        !linkUrlError &&
        Object.values(translationErrors).every((e) => !e);

    const availableOptionalLocales = optionalLocaleItems.filter(
        (item) => !translationEntries.some((tr) => tr.locale === item.value),
    );

    const handleAddLocale = (locale: string) => {
        setTranslationEntries((prev) => [
            ...prev,
            { locale, content: "", originalContent: "", isNew: true },
        ]);
    };

    const handleRemoveLocale = (locale: string) => {
        setTranslationEntries((prev) => prev.filter((tr) => tr.locale !== locale));
    };

    const handleTranslationContentChange = (locale: string, content: string) => {
        setTranslationEntries((prev) =>
            prev.map((tr) => (tr.locale === locale ? { ...tr, content } : tr)),
        );
    };

    const handleCreate = async () => {
        if (!form.kind || !form.format) return;

        const translations = translationEntries
            .filter((tr) => tr.content.trim() !== "")
            .map((tr) => ({ locale: tr.locale, content: tr.content.trim() }));

        await createBannerMutation.mutateAsync({
            name: form.name.trim(),
            kind: form.kind,
            format: form.format,
            priority: form.priority,
            color: toHex(form.color),
            textColor: toHex(form.textColor),
            enabled: form.enabled,
            dismissible: form.dismissible,
            startsAt: form.startsAt.toISO()!,
            endsAt: form.noExpiry ? BANNER_NO_EXPIRY_DATE : form.endsAt.toISO()!,
            translations,
            ...(form.linkUrl.trim() ? { linkUrl: form.linkUrl.trim() } : {}),
        });
    };

    const handleUpdate = async () => {
        if (!banner) return;

        const patch: BannerPatch = {};
        if (form.name !== banner.name) patch.name = form.name;
        if (form.format !== banner.format) patch.format = form.format as BannerFormat;
        if (form.priority !== banner.priority) patch.priority = form.priority;
        const hexColor = toHex(form.color);
        if (hexColor !== banner.color) patch.color = hexColor;
        const hexTextColor = toHex(form.textColor);
        if (hexTextColor !== (banner.textColor ?? "#ffffff")) patch.textColor = hexTextColor;
        if (form.enabled !== banner.enabled) patch.enabled = form.enabled;
        if (form.dismissible !== banner.dismissible) patch.dismissible = form.dismissible;
        if (form.startsAt.toMillis() !== DateTime.fromISO(banner.startsAt).toMillis())
            patch.startsAt = form.startsAt.toUTC().toISO()!;
        const newEndsAtMillis = form.noExpiry ? null : form.endsAt.toMillis();
        if (newEndsAtMillis !== DateTime.fromISO(banner.endsAt).toMillis())
            patch.endsAt = form.noExpiry ? BANNER_NO_EXPIRY_DATE : form.endsAt.toUTC().toISO()!;
        if (form.linkUrl !== (banner.linkUrl ?? "")) patch.linkUrl = form.linkUrl.trim();

        const updatePromise =
            Object.keys(patch).length > 0
                ? updateMutation.mutateAsync({ id: banner.id, data: patch })
                : Promise.resolve();

        const translationPromises = translationEntries.flatMap((entry) => {
            if (entry.isNew && entry.content.trim()) {
                return [
                    createTranslationMutation.mutateAsync({
                        bannerId: banner.id,
                        data: { locale: entry.locale, content: entry.content.trim() },
                    }),
                ];
            }
            if (!entry.isNew && entry.content !== entry.originalContent && entry.id) {
                return [
                    patchTranslationMutation.mutateAsync({
                        bannerId: banner.id,
                        translationId: entry.id,
                        data: { content: entry.content },
                    }),
                ];
            }
            return [];
        });

        const removedTranslations = (serverTranslations ?? []).filter(
            (tr) => tr.locale !== "en" && !translationEntries.some((e) => e.locale === tr.locale),
        );
        const deletePromises = removedTranslations.map((tr) =>
            deleteTranslationMutation.mutateAsync({ bannerId: banner.id, translationId: tr.id }),
        );

        await Promise.all([updatePromise, ...translationPromises, ...deletePromises]);
    };

    const handleSubmit = async () => {
        setAttempted(true);
        if (!canSubmit) return;
        setSubmitError(null);

        try {
            if (isEdit) {
                await handleUpdate();
            } else {
                await handleCreate();
            }
            onClose();
        } catch (err) {
            const fallbackKey = isEdit
                ? "adminBanners.failedToUpdate"
                : "adminBanners.failedToCreate";
            setSubmitError(err instanceof Error ? err.message : t(fallbackKey));
        }
    };

    const isSubmitting =
        createBannerMutation.isPending ||
        updateMutation.isPending ||
        createTranslationMutation.isPending ||
        patchTranslationMutation.isPending ||
        deleteTranslationMutation.isPending;

    const isExpired = isEdit && !form.noExpiry && form.endsAt < DateTime.now();

    return (
        <FormDialog
            open
            onClose={onClose}
            title={t("adminBanners.title")}
            secondaryText={isEdit ? t("adminBanners.editBanner") : t("adminBanners.addBanner")}
            onSubmit={handleSubmit}
            submitLabel={isEdit ? t("common.save") : t("common.create")}
            cancelLabel={t("common.cancel")}
            isSubmitting={isSubmitting}
            size="lg"
        >
            <Stack gap={4}>
                <Field.Root required invalid={attempted && !form.name.trim()}>
                    <Field.Label>
                        {t("adminBanners.name")}
                        <Field.RequiredIndicator />
                    </Field.Label>
                    <Input
                        value={form.name}
                        onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
                        placeholder={t("adminBanners.name")}
                    />
                    {attempted && !form.name.trim() && (
                        <Field.ErrorText>{t("common.fieldRequired" as never)}</Field.ErrorText>
                    )}
                </Field.Root>

                <Flex gap={4}>
                    <Field.Root
                        required
                        invalid={attempted && !isEdit && form.kind === ""}
                        flex={1}
                    >
                        <Field.Label>
                            {t("adminBanners.kind")}
                            <Field.RequiredIndicator />
                        </Field.Label>
                        {isEdit ? (
                            <Input value={form.kind} disabled opacity={0.6} cursor="not-allowed" />
                        ) : (
                            <OcSelect
                                items={kindItems}
                                value={form.kind}
                                onValueChange={(value) =>
                                    setForm((prev) => ({ ...prev, kind: value as BannerKind }))
                                }
                                placeholder={t("adminBanners.kindPlaceholder")}
                            />
                        )}
                        {attempted && !isEdit && form.kind === "" && (
                            <Field.ErrorText>{t("common.fieldRequired" as never)}</Field.ErrorText>
                        )}
                    </Field.Root>
                    <Field.Root required invalid={attempted && form.format === ""} flex={1}>
                        <Field.Label>
                            {t("adminBanners.format")}
                            <Field.RequiredIndicator />
                        </Field.Label>
                        <OcSelect
                            items={formatItems}
                            value={form.format as string}
                            onValueChange={(value) =>
                                setForm((prev) => ({ ...prev, format: value as BannerFormat }))
                            }
                            placeholder={t("adminBanners.formatPlaceholder")}
                        />
                        {attempted && form.format === "" && (
                            <Field.ErrorText>{t("common.fieldRequired" as never)}</Field.ErrorText>
                        )}
                    </Field.Root>
                    <Field.Root flex={1} invalid={attempted && !!priorityError}>
                        <Field.Label>
                            {t("adminBanners.priority")}
                            <Field.RequiredIndicator />
                        </Field.Label>
                        <NumberInput.Root
                            value={String(form.priority)}
                            onValueChange={(e) =>
                                setForm((prev) => ({
                                    ...prev,
                                    priority: isNaN(e.valueAsNumber) ? 1 : e.valueAsNumber,
                                }))
                            }
                            min={1}
                            max={10}
                        >
                            <NumberInput.Control>
                                <NumberInput.IncrementTrigger />
                                <NumberInput.DecrementTrigger />
                            </NumberInput.Control>
                            <NumberInput.Input />
                        </NumberInput.Root>
                        {attempted && priorityError && (
                            <Field.ErrorText>{priorityError}</Field.ErrorText>
                        )}
                    </Field.Root>
                </Flex>

                <Flex gap={4}>
                    <Field.Root flex={1}>
                        <Field.Label>{t("adminBanners.color")}</Field.Label>
                        <OcSwatch
                            defaultValue={form.color}
                            value={form.color}
                            onChange={(color) => setForm((prev) => ({ ...prev, color }))}
                        />
                    </Field.Root>
                    <Field.Root flex={1}>
                        <Field.Label>{t("adminBanners.textColor")}</Field.Label>
                        <OcSwatch
                            defaultValue={form.textColor}
                            value={form.textColor}
                            onChange={(textColor) => setForm((prev) => ({ ...prev, textColor }))}
                        />
                    </Field.Root>
                </Flex>

                <Field.Root invalid={attempted && !!linkUrlError}>
                    <Field.Label>{t("adminBanners.linkUrl")}</Field.Label>
                    <Input
                        value={form.linkUrl}
                        onChange={(e) => setForm((prev) => ({ ...prev, linkUrl: e.target.value }))}
                        placeholder={t("adminBanners.linkUrlPlaceholder" as never)}
                    />
                    {attempted && linkUrlError && <Field.ErrorText>{linkUrlError}</Field.ErrorText>}
                </Field.Root>

                <Flex gap={6} align="center">
                    <Flex align="center" gap={2}>
                        <OcSwitch
                            fullWidth={false}
                            label={t("adminBanners.enabled")}
                            checked={form.enabled}
                            onCheckedChange={(checked) =>
                                setForm((prev) => ({ ...prev, enabled: checked }))
                            }
                        />
                        {isExpired && (
                            <Text fontSize="sm" fontWeight="medium" color="red.500">
                                {t("adminBanners.expired")}
                            </Text>
                        )}
                    </Flex>
                    <OcSwitch
                        fullWidth={false}
                        label={t("adminBanners.dismissible")}
                        checked={form.dismissible}
                        onCheckedChange={(checked) =>
                            setForm((prev) => ({ ...prev, dismissible: checked }))
                        }
                    />
                    <OcSwitch
                        fullWidth={false}
                        label={t("adminBanners.noExpiry")}
                        checked={form.noExpiry}
                        onCheckedChange={(checked) =>
                            setForm((prev) => ({ ...prev, noExpiry: checked }))
                        }
                    />
                </Flex>

                <Field.Root invalid={!!dateError}>
                    <Flex gap={4}>
                        <Field.Root required flex={1}>
                            <Field.Label>{t("adminBanners.startsAt")}</Field.Label>
                            <OcDateTimePicker
                                min={dateMin}
                                max={dateMax}
                                startTime={form.startsAt}
                                onChange={(date) =>
                                    setForm((prev) => ({ ...prev, startsAt: date }))
                                }
                                timezone={timezone}
                            />
                        </Field.Root>
                        {!form.noExpiry && (
                            <Field.Root flex={1}>
                                <Field.Label>{t("adminBanners.endsAt")}</Field.Label>
                                <OcDateTimePicker
                                    min={dateMin}
                                    max={dateMax}
                                    startTime={form.endsAt}
                                    onChange={(date) =>
                                        setForm((prev) => ({ ...prev, endsAt: date }))
                                    }
                                    timezone={timezone}
                                />
                            </Field.Root>
                        )}
                    </Flex>
                    {attempted && dateError && <Field.ErrorText>{dateError}</Field.ErrorText>}
                </Field.Root>

                <Field.Root
                    required
                    invalid={attempted && (!englishEntry?.content.trim() || !!englishContentError)}
                >
                    <Field.Label>
                        {t("adminBanners.englishContent")}
                        <Field.RequiredIndicator />
                    </Field.Label>
                    <Textarea
                        value={englishEntry?.content ?? ""}
                        onChange={(e) => handleTranslationContentChange("en", e.target.value)}
                        rows={3}
                        maxLength={100}
                        placeholder={t("adminBanners.contentPlaceholder")}
                    />
                    {attempted && !englishEntry?.content.trim() && (
                        <Field.ErrorText>{t("common.fieldRequired" as never)}</Field.ErrorText>
                    )}
                    {attempted && englishEntry?.content.trim() && englishContentError && (
                        <Field.ErrorText>{englishContentError}</Field.ErrorText>
                    )}
                </Field.Root>

                {otherEntries.map((tr) => (
                    <Field.Root
                        key={tr.locale}
                        invalid={attempted && !!translationErrors[tr.locale]}
                    >
                        <Flex align="center" justify="space-between" mb={1}>
                            <Field.Label mb={0}>
                                {t(
                                    `adminBanners.locales.${tr.locale}` as never,
                                    tr.locale.toUpperCase(),
                                )}
                            </Field.Label>
                            <IconButton
                                aria-label={t("common.delete")}
                                size="xs"
                                variant="ghost"
                                color="fg.muted"
                                _hover={{ color: "red.fg" }}
                                onClick={() => handleRemoveLocale(tr.locale)}
                            >
                                <FaTrash />
                            </IconButton>
                        </Flex>
                        <Textarea
                            value={tr.content}
                            onChange={(e) =>
                                handleTranslationContentChange(tr.locale, e.target.value)
                            }
                            rows={3}
                            maxLength={100}
                            placeholder={t("adminBanners.contentPlaceholder")}
                        />
                        {attempted && translationErrors[tr.locale] && (
                            <Field.ErrorText>{translationErrors[tr.locale]}</Field.ErrorText>
                        )}
                    </Field.Root>
                ))}

                {availableOptionalLocales.length > 0 && (
                    <OcSelect
                        items={availableOptionalLocales}
                        value=""
                        onValueChange={handleAddLocale}
                        placeholder={t("adminBanners.addLanguage")}
                    />
                )}

                {submitError && (
                    <Field.Root invalid>
                        <Field.ErrorText>{submitError}</Field.ErrorText>
                    </Field.Root>
                )}
            </Stack>
        </FormDialog>
    );
}
