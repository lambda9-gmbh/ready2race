package de.lambda9.ready2race.backend

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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

fun Duration.afterNow(): LocalDateTime =
    LocalDateTime.now().plusSeconds(this.inWholeSeconds)

fun LocalDate.hr(locale: Locale? = null) = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).localizedBy(locale ?: Locale.GERMANY))
fun LocalTime.hr(locale: Locale? = null) = format(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).localizedBy(locale ?: Locale.GERMANY))
fun LocalDateTime.hr(locale: Locale? = null) = format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).localizedBy(locale ?: Locale.GERMANY))
fun LocalDateTime.hrDate(locale: Locale? = null) = toLocalDate().hr(locale)
fun LocalDateTime.hrTime(locale: Locale? = null) = toLocalTime().hr(locale)

fun <A: Any?> lexiNumberComp(stringSelector: (A) -> String?) = Comparator<A> { a, b ->
    val identA = a?.let(stringSelector)
    val identB = b?.let(stringSelector)

    if (identA == null && identB == null) {
        0
    } else if (identA == null) {
        1
    } else if (identB == null) {
        -1
    } else {
        val digitsA = identA.takeLastWhile { it.isDigit() }
        val digitsB = identB.takeLastWhile { it.isDigit() }

        val prefixA = identA.removeSuffix(digitsA)
        val prefixB = identB.removeSuffix(digitsB)

        val intA = digitsA.toIntOrNull() ?: 0
        val intB = digitsB.toIntOrNull() ?: 0

        // sort by lexicographical, except integer suffixes
        when {
            prefixA < prefixB -> -1
            prefixA > prefixB -> 1
            intA < intB -> -1
            intA > intB -> 1
            else -> 0
        }
    }
}
