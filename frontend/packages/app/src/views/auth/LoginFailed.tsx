// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { FaCircleNotch } from "react-icons/fa";
import { useSearchParams } from "react-router";
import { useTranslation } from "react-i18next";
import { FullPageMessage } from "~/features/errors/FullPageMessage";
import { redirectToLogin } from "~/features/auth/redirect-to-login";

export function LoginFailed() {
    const { t } = useTranslation();
    const [searchParams] = useSearchParams();
    const reason = searchParams.get("reason");
    const returnUrl = searchParams.get("returnUrl");
    const canAutoRecover = reason === "auth_request_not_found";

    // Only allow relative paths to prevent open redirect
    const safeReturnUrl = returnUrl?.startsWith("/") ? returnUrl : null;

    // When the OAuth state was lost (e.g. multi-tab or Keycloak session timeout),
    // redirect straight to login. Keycloak's SSO session will silently
    // re-authenticate without showing the login form.
    if (canAutoRecover) {
        redirectToLogin(safeReturnUrl);
        return null;
    }

    return (
        <FullPageMessage
            icon={FaCircleNotch}
            title={t("pages.loginFailed")}
            message={t("pages.loginFailedMessage")}
            buttonLabel={t("pages.loginButton")}
            onButtonClick={() => redirectToLogin(safeReturnUrl)}
        />
    );
}
