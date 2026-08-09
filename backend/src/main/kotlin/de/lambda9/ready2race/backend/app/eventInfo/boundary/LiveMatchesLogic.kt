package de.lambda9.ready2race.backend.app.eventInfo.boundary

import de.lambda9.ready2race.backend.app.eventInfo.entity.LiveMatchInfo
import de.lambda9.ready2race.backend.app.matchStatus.entity.MatchState
import java.time.LocalDateTime

/**
 * Reine Funktionen für den Tab „Live" der öffentlichen Ergebnisanzeige - ohne Datenbank und ohne
 * Uhr, damit die Reihenfolge und der Schutz der Ergebnisfreigabe ohne laufenden Server prüfbar
 * bleiben.
 */
object LiveMatchesLogic {

    /**
     * Zustände, die in der öffentlichen Live-Liste nichts zu suchen haben.
     *
     * Ein beendeter ([MatchState.FINISHED]) und ein vollständig gewerteter, aber nicht beendeter
     * Lauf ([MatchState.AWAITING_FINISH]) sind ERGEBNISSE. Ob sie öffentlich sichtbar sind,
     * entscheidet allein `Event.publicResultsVisibility` über `/latest-match-results` - bis zum
     * Beenden kann noch eine Zeitstrafe kommen.
     *
     * Die beiden Abfragen hinter [merge] können solche Läufe per SQL gar nicht liefern
     * (`CompetitionMatchRepo.getUpcomingMatchesForBoard` schließt `finished_at is not null` und
     * „kein Boot mehr ohne Ergebnis" aus). Dieser Filter ist deshalb kein Arbeitsschritt, sondern
     * ein Riegel: Ändert jemand eine der Abfragen, fällt die Freigaberegel nicht still um,
     * sondern der Lauf verschwindet aus der Live-Liste.
     */
    private val notLive = setOf(MatchState.FINISHED, MatchState.AWAITING_FINISH)

    /**
     * Innerhalb einer Gruppe zählt die geplante Zeit; ein Lauf ohne Termin steht ans Ende, weil er
     * über seine Reihenfolge nichts aussagt. Bei gleicher Zeit entscheidet die Startfolge des
     * Wettkampfs.
     */
    private val byStartTime: Comparator<LiveMatchInfo> =
        compareBy<LiveMatchInfo, LocalDateTime?>(nullsLast()) { it.startTime }
            .thenBy { it.executionOrder }

    /**
     * Führt die aktivierten und die anstehenden Läufe zu einer Liste zusammen.
     *
     * [activated] steht vorn: wer die Seite öffnet, sucht zuerst, was gerade passiert. Steht ein
     * Lauf in beiden Listen - die zwei Abfragen laufen nacheinander, dazwischen kann jemand
     * aktivieren -, gewinnt der aktivierte Eintrag; er trägt die frischere Aussage.
     *
     * [limit] deckelt das GESAMTE Ergebnis und nicht jeden Zweig für sich. Andernfalls
     * verdrängten zwanzig anstehende Läufe den einen, der gerade fährt.
     */
    fun merge(
        activated: List<LiveMatchInfo>,
        upcoming: List<LiveMatchInfo>,
        limit: Int,
    ): List<LiveMatchInfo> {
        val live = { matches: List<LiveMatchInfo> ->
            matches.filterNot { it.status.state in notLive }.sortedWith(byStartTime)
        }
        val front = live(activated)
        val frontIds = front.map { it.matchId }.toSet()
        val back = live(upcoming).filterNot { it.matchId in frontIds }
        return (front + back).take(limit.coerceAtLeast(0))
    }
}
