package de.lambda9.ready2race.backend.parsing

import com.fasterxml.jackson.module.kotlin.readValue
import de.lambda9.ready2race.backend.calls.requests.RequestError
import de.lambda9.ready2race.backend.calls.serialization.jsonMapper
import de.lambda9.ready2race.backend.config.ParseEnvException
import de.lambda9.ready2race.backend.kio.unwrap
import de.lambda9.tailwind.core.IO
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.Task
import java.util.UUID
import kotlin.enums.enumEntries
import kotlin.reflect.KClass

fun interface Parser<T : Any> {

    fun parse(input: String): T
    operator fun <R> invoke(input: String, execution: (task: Task<T>) -> R) =
        execution(KIO.effect { parse(input) })

    companion object {

        val int get() = Parser { it.toInt() }
        val uuid get() = Parser { UUID.fromString(it) }
        val boolean get() = Parser { it.toBooleanStrict() }

        inline fun <reified T : Any> json() = Parser { jsonMapper.readValue<T>(it) }

        inline fun <reified E: Enum<E>> enum() = Parser { value -> enumEntries<E>().find { it.name == value }!! }
    }
}