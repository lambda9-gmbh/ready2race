package de.lambda9.ready2race.backend

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.tailwind.core.Exit
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.exit.fold
import de.lambda9.tailwind.core.extensions.kio.andThen
import de.lambda9.tailwind.core.extensions.kio.orIf
import java.time.LocalDateTime
import java.util.*
import kotlin.time.Duration

fun String.count(count: Int) = "$count $this${if (count == 1) "" else "s"}"

fun <T> Result<T>.mapFailure(f: (Throwable) -> Throwable): Result<T> = when (val exception = exceptionOrNull()) {
    null -> this
    else -> Result.failure(f(exception))
}

fun <A, B> A.applyNotNull(value: B?, block: A.(B) -> Unit): A = apply {
    if (value != null) block(value)
}

fun <A> A.applyEither(
    takeLeft: Boolean,
    left: A.() -> Unit,
    right: A.() -> Unit,
): A = apply {
    if (takeLeft) {
        left()
    } else {
        right()
    }
}

fun ByteArray.toBase64(): String =
    Base64.getEncoder().encodeToString(this)

fun String.base64ToByteArray(): ByteArray =
    Base64.getDecoder().decode(this)

fun Duration.beforeNow(): LocalDateTime =
    LocalDateTime.now().minusSeconds(this.inWholeSeconds)

fun <A, B> Exit<Nothing, A>.fold(
    onDefect: (Throwable) -> B,
    onSuccess: (A) -> B
): B = fold(
    onError = { it },
    onDefect = onDefect,
    onSuccess = onSuccess
)

fun <E, A> Exit<E, A>.onDefect(
    f: (Throwable) -> Unit
): Unit = fold(
    onError = {},
    onDefect = { f(it) },
    onSuccess = {}
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

