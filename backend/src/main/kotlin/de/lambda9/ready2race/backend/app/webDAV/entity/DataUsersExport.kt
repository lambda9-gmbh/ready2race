package de.lambda9.ready2race.backend.app.webDAV.entity

data class DataUsersExport(
    val appUsers: List<AppUserExport>,
    val roles: List<RoleExport>,
    val roleHasPrivileges: List<RoleHasPrivilegeExport>,
    val appUserHasRoles: List<AppUserHasRoleExport>
)