package de.lambda9.ready2race.backend.parsing

import com.fasterxml.jackson.module.kotlin.readValue
import de.lambda9.ready2race.backend.calls.serialization.jsonMapper
import de.lambda9.ready2race.backend.data.Timecode
import de.lambda9.ready2race.backend.validation.timecodePattern
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.Task
import java.time.LocalDateTime
import java.util.*
import kotlin.enums.enumEntries

fun interface Parser<A : Any> {

    fun parse(input: String): A
    operator fun <R> invoke(input: String, execution: (task: Task<A>) -> R) =
        execution(KIO.effect { parse(input) })

    companion object {

        val int get() = Parser { it.toInt() }
        val uuid get() = Parser { UUID.fromString(it) }
        val datetime get() = Parser { LocalDateTime.parse(it) }
        val boolean get() = Parser { it.toBooleanStrict() }
        val timecode get() = Parser {
            val match = timecodePattern.find(it)
            val (millisLeft, baseUnit) = if (match!!.groups[6] == null) {
                if (match.groups[4] == null) {
                    val seconds = match.groups[2]!!.value.toLong()
                    seconds * 1000 to Timecode.BaseUnit.SECONDS
                } else {
                    val minutes = match.groups[2]!!.value.toLong()
                    val seconds = match.groups[4]!!.value.toLong()
                    (minutes * 60 + seconds) * 1000 to Timecode.BaseUnit.MINUTES
                }
            } else {
                val hours = match.groups[2]!!.value.toLong()
                val minutes = match.groups[4]!!.value.toLong()
                val seconds = match.groups[6]!!.value.toLong()
                (hours * 3600 + minutes * 60 + seconds) * 1000 to Timecode.BaseUnit.HOURS
            }
            val (millisRight, msPrec) = match.groups[8]?.value?.let { millisSuffix ->
                millisSuffix.padEnd(3, '0').toLong() to when(millisSuffix.length) {
                    1 -> Timecode.MillisecondPrecision.ONE
                    2 -> Timecode.MillisecondPrecision.TWO
                    3 -> Timecode.MillisecondPrecision.THREE
                    else -> Timecode.MillisecondPrecision.NONE
                }
            } ?: (0L to Timecode.MillisecondPrecision.NONE)

            val millis = (millisLeft + millisRight) * (if (match.groups[1]?.value == "-" ) -1 else 1)

            Timecode(
                millis = millis,
                baseUnit = baseUnit,
                millisecondPrecision = msPrec,
            )
        }

        inline fun <reified T : Any> json() = Parser { jsonMapper.readValue<T>(it) }

        inline fun <reified E: Enum<E>> enum() = Parser { value -> enumEntries<E>().find { it.name == value }!! }
    }
}