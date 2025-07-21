package de.lambda9.ready2race.backend.app.auth.entity

sealed class Privilege(
    val action: Action,
    val resource: Resource,
    val scope: Scope
) {

    enum class Action {
        CREATE,
        READ,
        UPDATE,
        DELETE,
    }

    enum class Resource {
        USER,
        EVENT,
        CLUB,
        REGISTRATION,
        INVOICE,
        APP_EVENT_REQUIREMENT,
        APP_QR_MANAGEMENT,
        APP_COMPETITION_CHECK,
        APP_CATERER
    }

    enum class Scope(val level: Int) {
        OWN(1),
        GLOBAL(2),
    }

    data object CreateUserGlobal: Privilege(Action.CREATE, Resource.USER, Scope.GLOBAL)
    data object ReadUserGlobal: Privilege(Action.READ, Resource.USER, Scope.GLOBAL)
    data object ReadUserOwn: Privilege(Action.READ, Resource.USER, Scope.OWN)
    data object UpdateUserGlobal: Privilege(Action.UPDATE, Resource.USER, Scope.GLOBAL)
    data object UpdateUserOwn: Privilege(Action.UPDATE, Resource.USER, Scope.OWN)

    data object CreateEventGlobal: Privilege(Action.CREATE, Resource.EVENT, Scope.GLOBAL)
    data object ReadEventGlobal: Privilege(Action.READ, Resource.EVENT, Scope.GLOBAL)
    data object ReadEventOwn: Privilege(Action.READ, Resource.EVENT, Scope.OWN)
    data object UpdateEventGlobal: Privilege(Action.UPDATE, Resource.EVENT, Scope.GLOBAL)
    data object DeleteEventGlobal: Privilege(Action.DELETE, Resource.EVENT, Scope.GLOBAL)

    data object CreateClubGlobal: Privilege(Action.CREATE, Resource.CLUB, Scope.GLOBAL)
    data object CreateClubOwn: Privilege(Action.CREATE, Resource.CLUB, Scope.OWN)
    data object ReadClubGlobal: Privilege(Action.READ, Resource.CLUB, Scope.GLOBAL)
    data object ReadClubOwn: Privilege(Action.READ, Resource.CLUB, Scope.OWN)
    data object UpdateClubGlobal: Privilege(Action.UPDATE, Resource.CLUB, Scope.GLOBAL)
    data object UpdateClubOwn: Privilege(Action.UPDATE, Resource.CLUB, Scope.OWN)
    data object DeleteClubGlobal: Privilege(Action.DELETE, Resource.CLUB, Scope.GLOBAL)

    data object CreateRegistrationGlobal: Privilege(Action.CREATE, Resource.REGISTRATION, Scope.GLOBAL)
    data object CreateRegistrationOwn: Privilege(Action.CREATE, Resource.REGISTRATION, Scope.OWN)
    data object ReadRegistrationGlobal: Privilege(Action.READ, Resource.REGISTRATION, Scope.GLOBAL)
    data object ReadRegistrationOwn: Privilege(Action.READ, Resource.REGISTRATION, Scope.OWN)
    data object UpdateRegistrationGlobal: Privilege(Action.UPDATE, Resource.REGISTRATION, Scope.GLOBAL)
    data object UpdateRegistrationOwn: Privilege(Action.UPDATE, Resource.REGISTRATION, Scope.OWN)
    data object DeleteRegistrationGlobal: Privilege(Action.DELETE, Resource.REGISTRATION, Scope.GLOBAL)
    data object DeleteRegistrationOwn: Privilege(Action.DELETE, Resource.REGISTRATION, Scope.OWN)

    data object UpdateAppEventRequirementGlobal: Privilege(Action.UPDATE, Resource.APP_EVENT_REQUIREMENT, Scope.GLOBAL)
    data object UpdateAppQrManagementGlobal: Privilege(Action.UPDATE, Resource.APP_QR_MANAGEMENT, Scope.GLOBAL)
    data object UpdateAppCompetitionCheckGlobal: Privilege(Action.UPDATE, Resource.APP_COMPETITION_CHECK, Scope.GLOBAL)
    data object UpdateAppCatererGlobal: Privilege(Action.UPDATE, Resource.APP_CATERER, Scope.GLOBAL)

    data object CreateInvoiceGlobal: Privilege(Action.CREATE, Resource.INVOICE, Scope.GLOBAL)
    data object ReadInvoiceGlobal: Privilege(Action.READ, Resource.INVOICE, Scope.GLOBAL)
    data object ReadInvoiceOwn: Privilege(Action.READ, Resource.INVOICE, Scope.OWN)
    data object UpdateInvoiceGlobal: Privilege(Action.UPDATE, Resource.INVOICE, Scope.GLOBAL)

    companion object {
        val entries get() = Privilege::class.sealedSubclasses.map { it.objectInstance!! }
    }
}
