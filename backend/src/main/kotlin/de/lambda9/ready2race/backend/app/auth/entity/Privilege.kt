package de.lambda9.ready2race.backend.app.auth.entity

sealed class Privilege(
    val resource: Resource,
    val action: Action,
    val scope: Scope
) {

    enum class Action {
        CREATE,
        READ,
        UPDATE,
        DELETE
    }

    enum class Scope {
        OWN,
        GROUP,
        GLOBAL;

        val level get () = when (this) {
            OWN -> 1
            GROUP -> 2
            GLOBAL -> 3
        }
    }

    enum class Resource {
        USER,
        ROLE,
        EVENT,
    }

    data object UserCreateGlobal: Privilege(Resource.USER, Action.CREATE, Scope.GLOBAL)
    data object UserReadGlobal: Privilege(Resource.USER, Action.READ, Scope.GLOBAL)

    data object EventCreateGlobal: Privilege(Resource.EVENT, Action.CREATE, Scope.GLOBAL)
    data object EventReadGlobal: Privilege(Resource.EVENT, Action.READ, Scope.GLOBAL)
    data object EventUpdateGlobal: Privilege(Resource.EVENT, Action.UPDATE, Scope.GLOBAL)
    data object EventDeleteGlobal: Privilege(Resource.EVENT, Action.DELETE, Scope.GLOBAL)

    companion object {
        val entries get() = Privilege::class.sealedSubclasses.map { it.objectInstance!! }
    }
}
