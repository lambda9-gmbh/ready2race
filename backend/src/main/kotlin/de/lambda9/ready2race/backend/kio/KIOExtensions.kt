package de.lambda9.ready2race.backend.kio

import de.lambda9.ready2race.backend.Config
import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.Task
import de.lambda9.tailwind.core.extensions.kio.failIf
import de.lambda9.tailwind.core.extensions.kio.recover

fun <T> Result<T>.toKio(): Task<T> = fold(
    onSuccess = { KIO.ok(it) },
    onFailure = { KIO.fail(it) }
)

fun accessConfig(): KIO<JEnv, Nothing, Config> = KIO.access { it.env.config }

// todo: Use from library after fix
fun <R, E, A> KIO<R, E, A>.recoverDefault(default: (E) -> A) =
    recover { KIO.ok(default(it)) }

fun <R, E : E1, E1> KIO<R, E, Boolean>.onTrueFail(error: () -> E1): KIO<R, E1, Unit> =
    failIf(
        condition = { it },
        transform = { error() }
    ).map {}

fun <R, E : E1, E1> KIO<R, E, Boolean>.onFalseFail(error: () -> E1): KIO<R, E1, Unit> =
    failIf(
        condition = { !it },
        transform = { error() }
    ).map {}
