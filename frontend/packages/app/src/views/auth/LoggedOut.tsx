// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { FaCircleNotch } from "react-icons/fa";
import { useSearchParams } from "react-router";
import { useTranslation } from "react-i18next";
import { FullPageMessage } from "~/features/errors/FullPageMessage";
import { redirectToLogin } from "~/features/auth/redirect-to-login";

type AuthReason = "logout" | "expired" | "login_required";

const messageKeys = {
    logout: {
        title: "pages.logoutSuccess",
        description: "pages.logoutMessage",
    },
    expired: {
        title: "pages.sessionExpired",
        description: "pages.sessionExpiredMessage",
    },
    login_required: {
        title: "pages.loginRequired",
        description: "pages.loginRequiredMessage",
    },
} as const satisfies Record<AuthReason, { title: string; description: string }>;

export function LoggedOut() {
    const { t } = useTranslation();
    const [searchParams] = useSearchParams();
    const reason = searchParams.get("reason") ?? "login_required";
    const returnUrl = searchParams.get("returnUrl");
    const keys = messageKeys[reason as AuthReason] ?? messageKeys.login_required;

    return (
        <FullPageMessage
            icon={FaCircleNotch}
            title={t(keys.title)}
            message={t(keys.description)}
            buttonLabel={t("pages.loginButton")}
            onButtonClick={() => redirectToLogin(returnUrl)}
        />
    );
}
