package de.lambda9.ready2race.backend.app.eventInfo.boundary

import de.lambda9.ready2race.backend.app.eventInfo.entity.AthleteBoardStartState
import de.lambda9.ready2race.backend.app.event.entity.PublicResultsVisibility
import de.lambda9.ready2race.backend.app.eventInfo.entity.UpcomingCompetitionMatchInfo
import de.lambda9.ready2race.backend.app.eventSchedule.boundary.FreeScheduleSlotInfo
import de.lambda9.ready2race.backend.app.eventSchedule.boundary.PendingScheduleSlotInfo
import java.time.Duration
import java.time.LocalDateTime

/**
 * Reine Logik der Athleten-Anzeige, bewusst ohne Datenbank- und Ktor-Bezug,
 * damit sie ohne laufende Umgebung geprüft werden kann.
 */
object AthleteBoardLogic {

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

    /**
     * Ab wann ein Lauf als Ergebnis öffentlich sichtbar ist — die Regel hinter
     * `Event.publicResultsVisibility` und damit hinter Athleten-Anzeige, Kiosk und öffentlicher
     * Ergebnisseite gleichermaßen.
     *
     * Ein beendeter Lauf ([finishedAt] gesetzt) ist immer sichtbar; das ist der Klick, mit dem der
     * zuständige Akteur den Stand für final erklärt. Ein vollständig gewerteter, aber nicht
     * beendeter Lauf (Zustand `AWAITING_FINISH`) erscheint nur, wenn die Veranstaltung das
     * ausdrücklich erlaubt hat: bis zum Beenden kann noch eine Zeitstrafe eintreffen, und ein
     * veröffentlichtes Ergebnis, das sich danach ändert, lässt sich nicht zurückholen.
     *
     * Diese Funktion trifft die Entscheidung an einem Lauf; die eigentliche Auswahl passiert aus
     * Mengengründen in SQL (`CompetitionMatchRepo.getMatchResults` und die View
     * `competition_having_results`). Beide Stellen bilden genau diese beiden Zweige nach — ändert
     * sich die Regel, ändern sie sich zusammen.
     */
    fun isPublicResult(
        finishedAt: LocalDateTime?,
        allTeamsScored: Boolean,
        visibility: PublicResultsVisibility,
    ): Boolean = when {
        finishedAt != null -> true
        visibility == PublicResultsVisibility.RESULTS_COMPLETE -> allTeamsScored
        else -> false
    }

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
     * Gehört ein Eintrag mit dieser Startzeit noch in "nächste Läufe"? Die Nachfrist beginnt mit
     * der Startzeit: bis [grace] verstrichen ist, bleibt der Eintrag als überfällig sichtbar,
     * danach nicht mehr. Bewusst dieselbe Grenze wie in
     * `CompetitionMatchRepo.getUpcomingMatchesForBoard` (dort als SQL-Bedingung
     * `START_TIME IS NULL OR START_TIME > now - grace`), damit echte Läufe und Platzhalter nach
     * derselben Regel verschwinden. Genau auf der Grenze ist die Frist abgelaufen.
     *
     * [now] wird hereingereicht statt hier geholt, damit die Entscheidung ohne Uhr prüfbar bleibt.
     */
    fun isStillUpcoming(startTime: LocalDateTime?, now: LocalDateTime, grace: Duration): Boolean =
        startTime == null || startTime.isAfter(now.minus(grace))

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
                competitionShortName = slot.competitionShortName,
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

    /**
     * Platzhalter für FREE-Slots (Programmpunkte wie "Mittagspause") - nur gebaut, wenn die
     * Veranstaltung das über `Event.showBreaksOnPublicBoards` erlaubt (siehe
     * `EventInfoService.mergeWithPendingPlaceholders`). Anders als bei [placeholdersFromPendingSlots]
     * gibt es keine Kompetition; [name] trägt die Anzeige statt Kompetitions-/Rundenname. Ohne
     * Team-/Personendaten aus demselben Grund wie bei Lauf-Platzhaltern - für einen Programmpunkt
     * gibt es ohnehin keine Aufstellung.
     */
    fun placeholdersFromFreeSlots(slots: List<FreeScheduleSlotInfo>): List<UpcomingCompetitionMatchInfo> =
        slots.map { slot ->
            UpcomingCompetitionMatchInfo(
                matchId = slot.slotId,
                matchNumber = null,
                competitionId = null,
                competitionName = "",
                categoryName = null,
                scheduledStartTime = slot.startTime,
                placeName = null,
                roundNumber = null,
                roundName = null,
                matchName = null,
                executionOrder = 0,
                teams = emptyList(),
                pendingRound = false,
                name = slot.name,
            )
        }
}
