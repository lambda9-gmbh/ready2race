package de.lambda9.ready2race.backend.app.eventInfo.boundary

import com.fasterxml.jackson.databind.JsonNode
import de.lambda9.ready2race.backend.app.eventInfo.entity.AthleteBoardConfig
import de.lambda9.ready2race.backend.app.eventInfo.entity.AthleteBoardStartState
import de.lambda9.ready2race.backend.app.eventInfo.entity.UpcomingCompetitionMatchInfo
import de.lambda9.ready2race.backend.app.eventSchedule.boundary.PendingScheduleSlotInfo
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

    // Untergrenze für den Abfragetakt der öffentlichen Anzeige. Der Takt kommt aus
    // display_duration_seconds, und dessen Regler in der Admin-Maske dient zugleich als
    // Rotationsdauer der Kiosk-Seite, die bis 5 Sekunden hinunter darf. Eine flotte Rotation
    // soll nicht nebenbei alle Telefone im 5-Sekunden-Takt anfragen lassen.
    const val MIN_REFRESH_INTERVAL_SECONDS = 10

    // Serverseitiger Zwischenspeicher je Veranstaltung: deckelt die Datenbanklast unabhängig
    // von der Zuschauerzahl. Deutlich kürzer als der Abfragetakt, damit die Anzeige nie
    // sichtbar hinterherhängt.
    const val CACHE_TTL_SECONDS = 5

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
                ?.coerceAtLeast(MIN_REFRESH_INTERVAL_SECONDS)
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
     * Ein Eintrag ist frisch, solange er jünger als [CACHE_TTL_SECONDS] ist. Ein `builtAt`
     * in der Zukunft (Uhrsprung rückwärts) gilt als frisch statt als dauerhaft abgelaufen.
     */
    fun isCacheFresh(builtAt: LocalDateTime, now: LocalDateTime): Boolean =
        builtAt.plusSeconds(CACHE_TTL_SECONDS.toLong()).isAfter(now)

    /**
     * Aufsteigend nach Startzeit; Läufe ohne gepflegte Startzeit stehen am Ende.
     */
    fun <T> sortByStartTime(items: List<T>, startTime: (T) -> LocalDateTime?): List<T> =
        items.sortedWith(compareBy(nullsLast<LocalDateTime>()) { startTime(it) })

    /**
     * Platzhalter für "nächste Läufe", deren Runde noch nicht erzeugt wurde. [slots] enthält per
     * Konstruktion nur WAITING-Slots - die Filterung auf WAITING (SKIPPED, FREE, LINKED und
     * OBSOLETE sind entweder kein Kandidat oder bereits anderweitig sichtbar) übernimmt
     * `EventScheduleLogic.pendingSlotOrNull` beim Einlesen, gemeinsam für Athleten-Anzeige und
     * Live-Dashboard. Bewusst ohne Team-/Personendaten: die Sparsamkeitsregel der Athleten-Anzeige
     * gilt auch für Platzhalter, und für einen WAITING-Slot gibt es ohnehin noch keine Aufstellung.
     */
    fun placeholdersFromPendingSlots(slots: List<PendingScheduleSlotInfo>): List<UpcomingCompetitionMatchInfo> =
        slots.map { slot ->
            UpcomingCompetitionMatchInfo(
                matchId = slot.setupMatchId,
                matchNumber = null,
                competitionId = slot.competitionId,
                competitionName = slot.competitionName,
                categoryName = null,
                scheduledStartTime = slot.startTime,
                placeName = null,
                roundNumber = null,
                roundName = slot.roundName,
                matchName = slot.matchName,
                executionOrder = 0,
                teams = emptyList(),
                pendingRound = true,
            )
        }
}
