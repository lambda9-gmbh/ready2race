package de.lambda9.ready2race.backend.kio

import de.lambda9.ready2race.backend.Config
import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.Task
import de.lambda9.tailwind.core.extensions.kio.andThen

fun <T> Result<T>.toKio(): Task<T> = fold(
    onSuccess = { KIO.ok(it) },
    onFailure = { KIO.fail(it) }
)

fun accessConfig(): KIO<JEnv, Nothing, Config> = KIO.access<JEnv>().map { it.env.config }

// todo: Use from library
fun <R, E : E1, E1, A> KIO<R, E, A>.failIf(condition: (A) -> Boolean, then: (A) -> E1): KIO<R, E1, A> =
    andThen {
        if (condition(it))
            KIO.fail(then(it))
        else
            KIO.ok(it)
    }

fun <R, E : E1, E1> KIO<R, E, Boolean>.failOnTrue(then: () -> E1): KIO<R, E1, Unit> =
    andThen {
        if (it)
            KIO.fail(then())
        else
            KIO.unit
    }

fun <R, E : E1, E1> KIO<R, E, Boolean>.failOnFalse(then: () -> E1): KIO<R, E1, Unit> =
    andThen {
        if (!it)
            KIO.fail(then())
        else
            KIO.unit
    }