package de.lambda9.ready2race.backend.app.auth.entity

// TODO: check all endpoints for correct usage

sealed class Privilege(
    val action: Action,
    val resource: Resource,
    val scope: Scope
) {

    enum class Action {
        CREATE,
        READ,
        UPDATE,
        DELETE
    }

    enum class Resource {
        USER,
        EVENT,
    }

    enum class Scope(val level: Int) {
        OWN(1),
        GLOBAL(2);
    }

    data object CreateUserGlobal: Privilege(Action.CREATE, Resource.USER, Scope.GLOBAL)
    data object ReadUserGlobal: Privilege(Action.READ, Resource.USER, Scope.GLOBAL)
    data object ReadUserOwn: Privilege(Action.READ, Resource.USER, Scope.OWN)
    data object UpdateUserGlobal: Privilege(Action.UPDATE, Resource.USER, Scope.GLOBAL)
    data object UpdateUserOwn: Privilege(Action.UPDATE, Resource.USER, Scope.OWN)
    data object DeleteUserGlobal: Privilege(Action.DELETE, Resource.USER, Scope.GLOBAL)

    data object CreateEventGlobal: Privilege(Action.CREATE, Resource.EVENT, Scope.GLOBAL)
    data object ReadEventGlobal: Privilege(Action.READ, Resource.EVENT, Scope.GLOBAL)
    data object UpdateEventGlobal: Privilege(Action.UPDATE, Resource.EVENT, Scope.GLOBAL)
    data object DeleteEventGlobal: Privilege(Action.DELETE, Resource.EVENT, Scope.GLOBAL)

    companion object {
        val entries get() = Privilege::class.sealedSubclasses.map { it.objectInstance!! }
    }
}
