// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, Spinner } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { FormDialog } from "@opencop/ui";
import { bannerQueries } from "~/features/banners/banners.queries";
import { BannerFormDialog } from "./BannerFormDialog";

interface EditBannerDialogProps {
    isOpen: boolean;
    onClose: () => void;
    bannerId: string;
}

export function EditBannerDialog({ isOpen, onClose, bannerId }: EditBannerDialogProps) {
    const { t } = useTranslation();

    const { data: banner, isLoading: isBannerLoading } = useQuery(bannerQueries.detail(bannerId));
    const { data: serverTranslations, isLoading: isTranslationsLoading } = useQuery(
        bannerQueries.translations(bannerId),
    );

    if (isBannerLoading || isTranslationsLoading || !banner || !serverTranslations) {
        return (
            <FormDialog
                open={isOpen}
                onClose={onClose}
                title={t("adminBanners.title")}
                secondaryText={t("adminBanners.editBanner")}
                size="lg"
                disableFooter
            >
                <Flex align="center" justify="center" py={10}>
                    <Spinner size="lg" color="brand.500" />
                </Flex>
            </FormDialog>
        );
    }

    return (
        <BannerFormDialog
            onClose={onClose}
            banner={banner}
            serverTranslations={serverTranslations}
        />
    );
}
