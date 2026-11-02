// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Badge, Box, Flex, Spinner, Text } from "@chakra-ui/react";
import { BANNER_NO_EXPIRY_DATE } from "@opencop/api";
import type { Banner } from "@opencop/api";
import { DetailTable, DrawerSideNavLayout } from "@opencop/ui";
import type { DrawerSideNavSection } from "@opencop/ui";
import { useQuery } from "@tanstack/react-query";
import { DateTime } from "luxon";
import type { ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { FaPencilAlt, FaTrash } from "react-icons/fa";
import { TbSpeakerphone } from "react-icons/tb";
import { bannerQueries } from "~/features/banners/banners.queries";
import { formatInstant } from "~/features/banners/banner.util";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import { DrawerToolbarButton } from "~/features/drawers/DrawerToolbarButton";
import { createOverviewSection } from "~/features/drawers/relation-section.util";
import { useViewMode } from "~/features/drawers/use-view-mode.hook";
import type { BannerModalState } from "../BannerDrawer";
import { BannerEditContent } from "./BannerEditContent";

interface BannerDrawerContentProps {
    banner: Banner;
    onOpenModal: (modal: BannerModalState) => void;
}

export function BannerDrawerContent({
    banner,
    onOpenModal,
}: BannerDrawerContentProps): ReactElement {
    const { t } = useTranslation();
    const timezone = useUserTimezone();
    const { mode, toView, toEdit } = useViewMode();
    const editing = mode === "edit";
    const textColor = banner.textColor ?? "#ffffff";

    const { data: serverTranslations, isLoading: isTranslationsLoading } = useQuery({
        ...bannerQueries.translations(banner.id),
        enabled: editing,
    });

    const statusBadge = !banner.enabled ? (
        <Badge colorPalette="gray" variant="subtle">
            {t("adminBanners.disabled")}
        </Badge>
    ) : banner.endsAt !== BANNER_NO_EXPIRY_DATE &&
      DateTime.fromISO(banner.endsAt) < DateTime.now() ? (
        <Badge colorPalette="red">{t("adminBanners.expired")}</Badge>
    ) : (
        <Badge colorPalette="green">{t("adminBanners.enabled")}</Badge>
    );

    const colorValue = (
        <Flex align="center" gap={2}>
            <Box
                w={4}
                h={4}
                borderRadius="sm"
                bg={banner.color}
                border="1px solid"
                borderColor="border.muted"
                flexShrink={0}
            />
            <Text>{banner.color}</Text>
        </Flex>
    );

    const textColorValue = (
        <Flex align="center" gap={2}>
            <Box
                w={4}
                h={4}
                borderRadius="sm"
                bg={textColor}
                border="1px solid"
                borderColor="border.muted"
                flexShrink={0}
            />
            <Text>{textColor}</Text>
        </Flex>
    );

    const rows = [
        {
            key: "kind",
            label: t("adminBanners.kind"),
            value: t(`adminBanners.kinds.${banner.kind.toLowerCase()}` as never),
        },
        { key: "status", label: t("adminBanners.enabled"), value: statusBadge },
        { key: "priority", label: t("adminBanners.priority"), value: String(banner.priority) },
        { key: "color", label: t("adminBanners.color"), value: colorValue },
        { key: "textColor", label: t("adminBanners.textColor"), value: textColorValue },
        {
            key: "startsAt",
            label: t("adminBanners.startsAt"),
            value: formatInstant(banner.startsAt, t, timezone),
        },
        {
            key: "endsAt",
            label: t("adminBanners.endsAt"),
            value: formatInstant(banner.endsAt, t, timezone),
        },
    ];

    const sections: DrawerSideNavSection[] = [
        createOverviewSection(
            t,
            <DetailTable rows={rows} compact />,
            t("drawerSections.entity.banner"),
        ),
    ];

    return (
        <DrawerSideNavLayout
            name={banner.name}
            navLabel={t("adminNav.banners")}
            icon={<TbSpeakerphone />}
            sections={sections}
            actions={
                editing ? undefined : (
                    <>
                        <DrawerToolbarButton
                            label={t("adminBanners.editBanner")}
                            icon={<FaPencilAlt />}
                            onClick={toEdit}
                        />
                        <DrawerToolbarButton
                            label={t("adminBanners.deleteBanner")}
                            icon={<FaTrash />}
                            destructive
                            onClick={() => onOpenModal({ type: "deleteBanner", banner })}
                        />
                    </>
                )
            }
            editContent={
                editing ? (
                    isTranslationsLoading || !serverTranslations ? (
                        <Flex align="center" justify="center" flex={1}>
                            <Spinner size="lg" color="brand.500" />
                        </Flex>
                    ) : (
                        <BannerEditContent
                            banner={banner}
                            serverTranslations={serverTranslations}
                            onDone={toView}
                        />
                    )
                ) : undefined
            }
        />
    );
}
