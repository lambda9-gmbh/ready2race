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
     * Dieser Filter ist eine ZUSICHERUNG über den Inhalt der Liste - „hier steht kein Ergebnis" -
     * und KEIN Riegel gegen künftige Änderungen an den Abfragen: [MatchState.FINISHED] und
     * [MatchState.AWAITING_FINISH] können mit den heutigen Umwandlungen (`toLiveMatchInfo` für
     * beide Zweige, in `eventInfo/control/Conversions.kt`) gar nicht erst entstehen, weil beide
     * `finishedAt` fest auf `null` setzen und der anstehende Zweig jedes Boot als ungewertet
     * übergibt (`MatchStatusTeam(place = null, failed = false, deregistered = false)`) - der
     * Filter sieht diese Zustände also nie und kann folglich auch nichts abfangen, sollte die
     * SQL-Auswahl sie eines Tages doch liefern.
     *
     * Der tatsächliche Schutz sitzt vor dieser Funktion: in der SQL-Bedingung
     * `finished_at is not null` / „kein Boot mehr ohne Ergebnis" in
     * `CompetitionMatchRepo.getUpcomingMatchesForBoard` und im ergebnisfeldlosen
     * `UpcomingMatchTeamInfo`, das Platz und Zeit gar nicht erst führt. Fiele dort die
     * `finished_at`-Bedingung weg, käme ein beendeter Lauf als `UPCOMING` durch - dieser Filter
     * ließe ihn dann anstandslos passieren. Behalten wird er trotzdem: er ist billig und die
     * Zusicherung stimmt, solange die Quellen halten, was sie versprechen.
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
