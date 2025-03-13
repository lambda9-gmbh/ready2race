package de.lambda9.ready2race.backend.calls.requests

import com.fasterxml.jackson.module.kotlin.readValue
import de.lambda9.ready2race.backend.calls.serialization.jsonMapper
import de.lambda9.tailwind.core.IO
import de.lambda9.tailwind.core.KIO
import java.util.*
import kotlin.reflect.KClass

fun interface ParamParser<A : Any> {
    fun parse(input: String): A
    operator fun invoke(key: String, input: String, kClass: KClass<A>): IO<RequestError, A> =
        KIO.effect { parse(input) }.mapError { RequestError.ParameterUnparsable(key, input, kClass) }

    companion object {

        val int get() = ParamParser { it.toInt() }
        val uuid get() = ParamParser { UUID.fromString(it) }

        inline fun <reified T : Any> json() = ParamParser { jsonMapper.readValue<T>(it) }
    }
}