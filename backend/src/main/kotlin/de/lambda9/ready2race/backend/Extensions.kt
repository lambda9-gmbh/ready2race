package de.lambda9.ready2race.backend

import java.time.LocalDateTime
import java.util.*
import kotlin.time.Duration

fun String.count(count: Int) = "$count $this${if (count == 1) "" else "s"}"

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
