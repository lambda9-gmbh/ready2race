package de.lambda9.ready2race.backend.app.auth.entity

enum class PrivilegeScope {
    GLOBAL,
    ASSOCIATION_BOUND,
}

enum class Privilege {

    USER_VIEW,
    USER_EDIT,

    ROLE_VIEW,
    ROLE_EDIT,
}