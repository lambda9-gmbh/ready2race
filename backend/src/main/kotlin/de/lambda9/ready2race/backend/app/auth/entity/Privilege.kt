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
        PARTICIPANT,
        REGISTRATION
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

    data object CreateParticipantGlobal: Privilege(Action.CREATE, Resource.PARTICIPANT, Scope.GLOBAL)
    data object CreateParticipantOwn: Privilege(Action.CREATE, Resource.PARTICIPANT, Scope.OWN)
    data object ReadParticipantGlobal: Privilege(Action.READ, Resource.PARTICIPANT, Scope.GLOBAL)
    data object ReadParticipantOwn: Privilege(Action.READ, Resource.PARTICIPANT, Scope.OWN)
    data object UpdateParticipantGlobal: Privilege(Action.UPDATE, Resource.PARTICIPANT, Scope.GLOBAL)
    data object UpdateParticipantOwn: Privilege(Action.UPDATE, Resource.PARTICIPANT, Scope.OWN)
    data object DeleteParticipantGlobal: Privilege(Action.DELETE, Resource.PARTICIPANT, Scope.GLOBAL)
    data object DeleteParticipantOwn: Privilege(Action.DELETE, Resource.PARTICIPANT, Scope.OWN)

    data object CreateRegistrationGlobal: Privilege(Action.CREATE, Resource.REGISTRATION, Scope.GLOBAL)
    data object CreateRegistrationOwn: Privilege(Action.CREATE, Resource.REGISTRATION, Scope.OWN)
    data object ReadRegistrationGlobal: Privilege(Action.READ, Resource.REGISTRATION, Scope.GLOBAL)
    data object ReadRegistrationOwn: Privilege(Action.READ, Resource.REGISTRATION, Scope.OWN)
    data object UpdateRegistrationGlobal: Privilege(Action.UPDATE, Resource.REGISTRATION, Scope.GLOBAL)
    data object UpdateRegistrationOwn: Privilege(Action.UPDATE, Resource.REGISTRATION, Scope.OWN)
    data object DeleteRegistrationGlobal: Privilege(Action.DELETE, Resource.REGISTRATION, Scope.GLOBAL)
    data object DeleteRegistrationOwn: Privilege(Action.DELETE, Resource.REGISTRATION, Scope.OWN)

    companion object {
        val entries get() = Privilege::class.sealedSubclasses.map { it.objectInstance!! }
    }
}
