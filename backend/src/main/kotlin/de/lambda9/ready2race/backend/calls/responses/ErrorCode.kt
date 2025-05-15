package de.lambda9.ready2race.backend.calls.responses

enum class ErrorCode {
    CANNOT_ASSIGN_ROLES,
    CAPTCHA_WRONG,
    EMAIL_IN_USE,
    EVENT_REGISTRATION_ONGOING,
    INVOICES_ALREADY_PRODUCED,
    NO_ASSIGNED_PAYEE_INFORMATION,
}