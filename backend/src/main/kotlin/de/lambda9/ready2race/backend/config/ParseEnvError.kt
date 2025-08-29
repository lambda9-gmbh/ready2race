package de.lambda9.ready2race.backend.config

import de.lambda9.tailwind.core.IO
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.getOrThrow
import de.lambda9.tailwind.core.extensions.kio.recover
import kotlin.reflect.KClass

class ParseEnvException(msg: String) : Exception(msg)

sealed interface ParseEnvError {

    data class MissingKey(val key: String) : ParseEnvError

    data class UnparsableValue<A : Any>(val key: String, val value: String, val type: KClass<A>) : ParseEnvError

    data class IncompleteBundle(val given: List<String>, val missing: List<String>) : ParseEnvError

    fun toException() = ParseEnvException(
        when (this) {

            is IncompleteBundle ->
                "Incomplete key bundle - either all or none, found: $given, missing: $missing"
            is MissingKey ->
                "Missing required key in environment: '$key'"
            is UnparsableValue<*> ->
                "Unparsable value '$value' for key '$key', expected type '$type'"
        }
    )
}

fun IO<ParseEnvError, Config>.unwrap() =
    recover<Any?, ParseEnvError, Nothing, Config> { throw it.toException() }.unsafeRunSync().getOrThrow()
