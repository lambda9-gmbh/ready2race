package de.lambda9.ready2race.backend.responses

enum class ErrorCode {
    PRIVILEGE_MISSING,
    ROLE_IS_STATIC,
    CAPTCHA_WRONG,
    EMAIL_IN_USE,
    CANNOT_ASSIGN_ROLES,
}