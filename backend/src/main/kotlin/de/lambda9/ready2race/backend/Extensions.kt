package de.lambda9.ready2race.backend

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.tailwind.core.KIO
import java.time.LocalDateTime
import java.util.*
import kotlin.time.Duration

fun ByteArray.toBase64(): String =
    Base64.getEncoder().encodeToString(this)

fun String.base64ToByteArray(): ByteArray =
    Base64.getDecoder().decode(this)

fun Duration.beforeNow(): LocalDateTime =
    LocalDateTime.now().minusSeconds(this.inWholeSeconds)

fun accessConfig(): KIO<JEnv, Nothing, Config> = KIO.access<JEnv>().map { it.env.config }

fun <R, E : E1, E1, A> KIO<R, E, A>.failOn(predicate: (A) -> Boolean, error: () -> E1): KIO<R, E1, Unit> =
    KIO.failOnM(this.map { predicate(it) }, error)

fun <R, E : E1, E1> KIO<R, E, Boolean>.failOnTrue(error: () -> E1): KIO<R, E1, Unit> =
    this.failOn(predicate = { it }, error)

fun <R, E : E1, E1> KIO<R, E, Boolean>.failOnFalse(error: () -> E1): KIO<R, E1, Unit> =
    this.failOn(predicate = { !it }, error)