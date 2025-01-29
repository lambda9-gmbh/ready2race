package de.lambda9.ready2race.backend

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.tailwind.core.Exit
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.exit.fold
import java.time.LocalDateTime
import java.util.*
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
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