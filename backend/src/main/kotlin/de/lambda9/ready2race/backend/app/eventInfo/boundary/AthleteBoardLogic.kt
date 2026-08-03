package de.lambda9.ready2race.backend.app.eventInfo.boundary

import com.fasterxml.jackson.databind.JsonNode
import de.lambda9.ready2race.backend.app.eventInfo.entity.AthleteBoardConfig
import de.lambda9.ready2race.backend.app.eventInfo.entity.AthleteBoardStartState
import java.time.LocalDateTime

/**
 * Reine Logik der Athleten-Anzeige, bewusst ohne Datenbank- und Ktor-Bezug,
 * damit sie ohne laufende Umgebung geprüft werden kann.
 */
object AthleteBoardLogic {

    const val DEFAULT_RUNNING_LIMIT = 3
    const val DEFAULT_UPCOMING_LIMIT = 3
    const val DEFAULT_RESULTS_LIMIT = 1
    const val DEFAULT_SHOW_COUNTDOWN = true
    const val DEFAULT_REFRESH_INTERVAL_SECONDS = 15

    // Nachfrist für "nächste Läufe": Eine verstrichene Startzeit macht einen Lauf nicht sofort
    // uninteressant für den Block, sondern erst nach dieser Frist - vorher bleibt er als
    // überfällig (OVERDUE) sichtbar, statt kommentarlos von der Anzeige zu verschwinden.
    const val DEFAULT_OVERDUE_GRACE_MINUTES = 30

    private const val MIN_LIMIT = 1
    private const val MAX_LIMIT = 20

    /**
     * Löst die Konfiguration Feld für Feld gegen die Vorgabewerte auf. Eine Konfiguration,
     * die nur einen Wert setzt, behält für alle übrigen die Vorgabe.
     */
    fun resolveConfig(filters: JsonNode?, displayDurationSeconds: Int?): AthleteBoardConfig =
        AthleteBoardConfig(
            runningLimit = filters.limitOr("running", DEFAULT_RUNNING_LIMIT),
            upcomingLimit = filters.limitOr("upcoming", DEFAULT_UPCOMING_LIMIT),
            resultsLimit = filters.limitOr("results", DEFAULT_RESULTS_LIMIT),
            showCountdown = filters?.get("showCountdown")
                ?.takeIf { it.isBoolean }
                ?.booleanValue()
                ?: DEFAULT_SHOW_COUNTDOWN,
            refreshIntervalSeconds = displayDurationSeconds?.takeIf { it > 0 }
                ?: DEFAULT_REFRESH_INTERVAL_SECONDS,
        )

    private fun JsonNode?.limitOr(field: String, default: Int): Int =
        this?.get(field)
            ?.takeIf { it.isInt }
            ?.intValue()
            ?.coerceIn(MIN_LIMIT, MAX_LIMIT)
            ?: default

    /**
     * Eine verstrichene Startzeit ergibt OVERDUE statt eines negativen Countdowns.
     */
    fun startState(
        startTime: LocalDateTime?,
        now: LocalDateTime,
        showCountdown: Boolean,
    ): AthleteBoardStartState = when {
        startTime == null -> AthleteBoardStartState.UNSCHEDULED
        !startTime.isAfter(now) -> AthleteBoardStartState.OVERDUE
        showCountdown -> AthleteBoardStartState.COUNTDOWN
        else -> AthleteBoardStartState.SCHEDULED
    }

    /**
     * Aufsteigend nach Startzeit; Läufe ohne gepflegte Startzeit stehen am Ende.
     */
    fun <T> sortByStartTime(items: List<T>, startTime: (T) -> LocalDateTime?): List<T> =
        items.sortedWith(compareBy(nullsLast<LocalDateTime>()) { startTime(it) })
}
