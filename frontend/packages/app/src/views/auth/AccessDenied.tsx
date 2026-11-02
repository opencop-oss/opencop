// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useTranslation } from "react-i18next";
import { FaCircleNotch } from "react-icons/fa6";
import { useSearchParams } from "react-router";
import { FullPageMessage } from "~/features/errors/FullPageMessage";
import { redirectToLogin } from "~/features/auth/redirect-to-login";

export function AccessDenied() {
    const { t } = useTranslation();
    const [searchParams] = useSearchParams();
    const returnUrl = searchParams.get("returnUrl");

    // Only allow relative paths to prevent open redirect
    const safeReturnUrl = returnUrl?.startsWith("/") ? returnUrl : null;

    return (
        <FullPageMessage
            icon={FaCircleNotch}
            title={t("pages.accessDenied")}
            message={t("pages.accessDeniedMessage")}
            buttonLabel={t("pages.loginButton")}
            onButtonClick={() => redirectToLogin(safeReturnUrl)}
        />
    );
}
