// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import type { Banner, BannerTranslation } from "@opencop/api";
import { useActiveBanners } from "./useActiveBanners";
import { useBannerDismiss } from "./BannerDismissContext";
import { BannerBar, BarNavControls } from "./BannerBar";

function resolveContent(banner: Banner, locale: string): string {
    const translations = banner.translations ?? [];
    const match =
        translations.find((t: BannerTranslation) => t.locale === locale) ??
        translations.find((t: BannerTranslation) => t.locale === "en");
    return match?.content ?? "";
}

interface BarBannerDisplayProps {
    banners: Banner[];
    locale: string;
    barRole?: "banner" | "contentinfo";
    dismiss: (id: string) => void;
}

function BarBannerDisplay({ banners, locale, barRole, dismiss }: BarBannerDisplayProps) {
    const [index, setIndex] = useState(0);

    // Auto-cycle every 8 seconds when there are multiple banners
    useEffect(() => {
        if (banners.length <= 1) return;
        const id = setInterval(() => {
            setIndex((i) => (i + 1) % banners.length);
        }, 8000);
        return () => clearInterval(id);
    }, [banners.length]);

    const safeIndex = banners.length > 0 ? Math.min(index, banners.length - 1) : 0;
    const current = banners[safeIndex];

    if (!current) return null;

    return (
        <BannerBar
            key={current.id}
            banner={current}
            content={resolveContent(current, locale)}
            role={barRole}
            onDismiss={dismiss}
            navControls={
                banners.length > 1 ? (
                    <BarNavControls
                        index={safeIndex}
                        total={banners.length}
                        onPrev={() => setIndex((i) => (i - 1 + banners.length) % banners.length)}
                        onNext={() => setIndex((i) => (i + 1) % banners.length)}
                    />
                ) : undefined
            }
        />
    );
}

interface ActiveBannerDisplayProps {
    kinds: Array<Banner["kind"]>;
    barRole?: "banner" | "contentinfo";
}

export function ActiveBannerDisplay({ kinds, barRole }: ActiveBannerDisplayProps) {
    const { i18n } = useTranslation();
    const activeBanners = useActiveBanners();
    const { dismissedIds, dismiss } = useBannerDismiss();

    const visible = activeBanners.filter((b) => kinds.includes(b.kind) && !dismissedIds.has(b.id));
    if (visible.length === 0) return null;

    const locale = i18n.language;

    const systemBanners = visible.filter((b) => b.kind === "SYSTEM");
    const barBanners = visible
        .filter((b) => b.kind === "HEADER" || b.kind === "FOOTER")
        .sort((a, b) => a.priority - b.priority);

    return (
        <>
            {systemBanners.map((banner) => (
                <BannerBar
                    key={banner.id}
                    banner={banner}
                    content={resolveContent(banner, locale)}
                    role="status"
                    onDismiss={dismiss}
                />
            ))}
            {barBanners.length > 0 && (
                <BarBannerDisplay
                    banners={barBanners}
                    locale={locale}
                    barRole={barRole}
                    dismiss={dismiss}
                />
            )}
        </>
    );
}
