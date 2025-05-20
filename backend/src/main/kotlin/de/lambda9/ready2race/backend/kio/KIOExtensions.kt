package de.lambda9.ready2race.backend.kio

import de.lambda9.ready2race.backend.Config
import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.tailwind.core.Cause
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.failIf
import de.lambda9.tailwind.core.extensions.kio.onNull

fun accessConfig(): KIO<JEnv, Nothing, Config> = KIO.access { it.env.config }

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

fun <R, E, A> KIO<R, E, A?>.onNullDie(notNullReason: String): KIO<R, E, A> =
    onNull {
        KIO.halt(Cause.panic(NullPointerException("Expected non-null value was in fact NULL. It should not be null, because: $notNullReason")))
    }
